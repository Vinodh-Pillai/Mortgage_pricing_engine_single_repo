package com.wcpe.catalog.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class CatalogRepository {
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  CatalogRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  <T> T idempotent(UUID tenantId, String idempotencyKey, Object request, Class<T> responseType, Supplier<T> command) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) return command.get();
    String requestHash = hash(json(request));
    jdbc.execute("select pg_advisory_xact_lock(hashtext('" + tenantId + ":" + idempotencyKey.replace("'", "''") + "'))");
    List<Map<String, Object>> rows = jdbc.queryForList("select request_hash,response_json from catalog.catalog_idempotency_record where tenant_id=? and idempotency_key=?", tenantId, idempotencyKey);
    if (!rows.isEmpty()) {
      String existingHash = Objects.toString(rows.get(0).get("request_hash"), "");
      if (!existingHash.equals(requestHash)) throw new CatalogException("IDEMPOTENCY_CONFLICT");
      return readJson(Objects.toString(rows.get(0).get("response_json"), "{}"), responseType);
    }
    T response = command.get();
    jdbc.update("insert into catalog.catalog_idempotency_record(tenant_id,idempotency_key,request_hash,response_json) values (?,?,?,?::jsonb)", tenantId, idempotencyKey, requestHash, json(response));
    return response;
  }

  UUID currentCatalogId(UUID tenantId) {
    List<UUID> ids = jdbc.query("select catalog_id from catalog.product_catalog where tenant_id=? order by updated_at desc limit 1", (rs, row) -> rs.getObject(1, UUID.class), tenantId);
    if (!ids.isEmpty()) return ids.get(0);
    UUID id = UUID.randomUUID();
    jdbc.update("insert into catalog.product_catalog(tenant_id,catalog_id,version,status,replay_hash) values (?,?,?,?,?)", tenantId, id, 1, CatalogStatus.DRAFT.name(), hash("new:" + id));
    return id;
  }

  CatalogStatus status(UUID tenantId, UUID catalogId) {
    return CatalogStatus.valueOf(jdbc.queryForObject("select status from catalog.product_catalog where tenant_id=? and catalog_id=?", String.class, tenantId, catalogId));
  }

  int version(UUID tenantId, UUID catalogId) {
    return jdbc.queryForObject("select version from catalog.product_catalog where tenant_id=? and catalog_id=?", Integer.class, tenantId, catalogId);
  }

  ProductDefinition addProduct(UUID tenantId, UUID catalogId, ProductRequest request) {
    requireEditable(tenantId, catalogId);
    if (exists("catalog.product_definition", tenantId, "product_code", request.productCode())) throw new CatalogException("PRODUCT_CODE_DUPLICATE");
    ProductDefinition p = new ProductDefinition(UUID.randomUUID(), required(request.productCode(), "PRODUCT_CODE_REQUIRED"), required(request.productName(), "PRODUCT_NAME_REQUIRED"), required(request.productFamily(), "PRODUCT_FAMILY_REQUIRED"), safe(request.allowedChannels()), safe(request.allowedStates()), requiredDate(request.effectiveFrom()), request.effectiveTo());
    jdbc.update("insert into catalog.product_definition(tenant_id,product_id,catalog_id,product_code,product_name,product_family,allowed_channels,allowed_states,effective_from,effective_to) values (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
        tenantId, p.productId(), catalogId, p.productCode(), p.productName(), p.productFamily(), json(p.allowedChannels()), json(p.allowedStates()), java.sql.Date.valueOf(p.effectiveFrom()), date(p.effectiveTo()));
    versionControl(tenantId, catalogId, "PRODUCT", p.productId(), p.productCode(), CatalogStatus.DRAFT, p);
    bump(tenantId, catalogId);
    return p;
  }

  InvestorProgram addInvestor(UUID tenantId, UUID catalogId, InvestorRequest request) {
    requireEditable(tenantId, catalogId);
    if (exists("catalog.investor_program", tenantId, "investor_code", request.investorCode())) throw new CatalogException("INVESTOR_CODE_DUPLICATE");
    for (String productCode : safe(request.productCodes())) if (!exists("catalog.product_definition", tenantId, "product_code", productCode)) throw new CatalogException("INVESTOR_PRODUCT_UNKNOWN");
    InvestorProgram i = new InvestorProgram(UUID.randomUUID(), required(request.investorCode(), "INVESTOR_CODE_REQUIRED"), required(request.investorName(), "INVESTOR_NAME_REQUIRED"), safe(request.channels()), safe(request.productCodes()), requiredDate(request.effectiveFrom()), request.effectiveTo());
    jdbc.update("insert into catalog.investor_program(tenant_id,investor_id,catalog_id,investor_code,investor_name,channels,product_codes,effective_from,effective_to) values (?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
        tenantId, i.investorId(), catalogId, i.investorCode(), i.investorName(), json(i.channels()), json(i.productCodes()), java.sql.Date.valueOf(i.effectiveFrom()), date(i.effectiveTo()));
    versionControl(tenantId, catalogId, "INVESTOR", i.investorId(), i.investorCode(), CatalogStatus.DRAFT, i);
    bump(tenantId, catalogId);
    return i;
  }

  ReferenceEntry addReference(UUID tenantId, UUID catalogId, String catalogType, ReferenceCatalogRequest request) {
    requireEditable(tenantId, catalogId);
    if (existsReference(tenantId, catalogType, request.code())) throw new CatalogException(catalogType + "_CODE_DUPLICATE");
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), catalogType, required(request.code(), "REFERENCE_CODE_REQUIRED"), required(request.label(), "REFERENCE_LABEL_REQUIRED"), request.category(), request.attributes() == null ? Map.of() : Map.copyOf(request.attributes()), requiredDate(request.effectiveFrom()), request.effectiveTo());
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    versionControl(tenantId, catalogId, catalogType, e.entryId(), e.code(), CatalogStatus.DRAFT, e);
    bump(tenantId, catalogId);
    return e;
  }

  MarketArea addMarket(UUID tenantId, UUID catalogId, MarketRequest request) {
    requireEditable(tenantId, catalogId);
    if (request.stateCode() == null || !request.stateCode().matches("[A-Z]{2}")) throw new CatalogException("INVALID_STATE_CODE");
    if (request.countyFips() != null && !request.countyFips().matches("\\d{5}")) throw new CatalogException("INVALID_COUNTY_FIPS");
    MarketArea m = new MarketArea(UUID.randomUUID(), request.stateCode(), request.countyFips(), request.countyName(), request.marketStatus() == null ? "ENABLED" : request.marketStatus(), safe(request.allowedChannels()), requiredDate(request.effectiveFrom()), request.effectiveTo());
    jdbc.update("insert into catalog.market_area(tenant_id,market_id,catalog_id,state_code,county_fips,county_name,market_status,allowed_channels,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, m.marketId(), catalogId, m.stateCode(), m.countyFips(), m.countyName(), m.marketStatus(), json(m.allowedChannels()), java.sql.Date.valueOf(m.effectiveFrom()), date(m.effectiveTo()));
    versionControl(tenantId, catalogId, "MARKET", m.marketId(), m.stateCode() + ":" + Objects.toString(m.countyFips(), "*"), CatalogStatus.DRAFT, m);
    bump(tenantId, catalogId);
    return m;
  }

  void publish(UUID tenantId, UUID catalogId) {
    if (products(tenantId).isEmpty()) throw new CatalogException("CATALOG_PRODUCTS_REQUIRED");
    if (investors(tenantId).isEmpty()) throw new CatalogException("CATALOG_INVESTORS_REQUIRED");
    transition(tenantId, catalogId, CatalogStatus.APPROVED, CatalogStatus.PUBLISHED);
  }

  void transition(UUID tenantId, UUID catalogId, CatalogStatus expected, CatalogStatus next) {
    int updated = jdbc.update("update catalog.product_catalog set status=?, version=version+1, replay_hash=?, updated_at=now() where tenant_id=? and catalog_id=? and status=?",
        next.name(), hash(next + ":" + tenantId + ":" + catalogId + ":" + Instant.now()), tenantId, catalogId, expected.name());
    if (updated == 0) throw new CatalogException("INVALID_CATALOG_STATUS_TRANSITION");
    jdbc.update("update catalog.catalog_version_control set status=?, row_version=row_version+1, updated_at=now() where tenant_id=? and catalog_id=? and status=?", next.name(), tenantId, catalogId, expected.name());
  }

  void requireVersion(UUID tenantId, UUID catalogId, int expectedVersion) {
    if (version(tenantId, catalogId) != expectedVersion) throw new CatalogException("CATALOG_VERSION_CONFLICT");
  }

  void forceStatus(UUID tenantId, UUID catalogId, CatalogStatus next) {
    jdbc.update("update catalog.product_catalog set status=?, version=version+1, replay_hash=?, updated_at=now() where tenant_id=? and catalog_id=?",
        next.name(), hash(next + ":" + tenantId + ":" + catalogId + ":" + Instant.now()), tenantId, catalogId);
    jdbc.update("update catalog.catalog_version_control set status=?, row_version=row_version+1, updated_at=now() where tenant_id=? and catalog_id=?", next.name(), tenantId, catalogId);
  }

  void resetToDraft(UUID tenantId, UUID catalogId) {
    jdbc.update("update catalog.product_catalog set status='DRAFT', version=version+1, replay_hash=?, updated_at=now() where tenant_id=? and catalog_id=?",
        hash("DRAFT:" + tenantId + ":" + catalogId + ":" + Instant.now()), tenantId, catalogId);
    jdbc.update("update catalog.catalog_version_control set status='DRAFT', row_version=row_version+1, updated_at=now() where tenant_id=? and catalog_id=?", tenantId, catalogId);
  }

  List<CatalogVersionControlRecord> versionControls(UUID tenantId, UUID catalogId) {
    return jdbc.query("select version_control_id,catalog_id,artifact_type,artifact_id,artifact_code,version_number,status,config_hash,row_version from catalog.catalog_version_control where tenant_id=? and catalog_id=? order by artifact_type,artifact_code,version_number",
        (rs, row) -> new CatalogVersionControlRecord(rs.getObject("version_control_id", UUID.class), rs.getObject("catalog_id", UUID.class), rs.getString("artifact_type"), rs.getObject("artifact_id", UUID.class), rs.getString("artifact_code"), rs.getInt("version_number"), CatalogStatus.valueOf(rs.getString("status")), rs.getString("config_hash"), rs.getLong("row_version")), tenantId, catalogId);
  }

  String findSubmitterId(UUID tenantId) {
    List<String> rows = jdbc.query("select actor_id from catalog.catalog_audit_record where tenant_id=? and action='CATALOG_SUBMITTED_FOR_APPROVAL' order by occurred_at desc limit 1", (rs, row) -> rs.getString("actor_id"), tenantId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  String replayHash(UUID tenantId, UUID catalogId) {
    return jdbc.queryForObject("select replay_hash from catalog.product_catalog where tenant_id=? and catalog_id=?", String.class, tenantId, catalogId);
  }

  CatalogResponse current(UUID tenantId) {
    UUID catalogId = currentCatalogId(tenantId);
    String replayHash = jdbc.queryForObject("select replay_hash from catalog.product_catalog where tenant_id=? and catalog_id=?", String.class, tenantId, catalogId);
    return new CatalogResponse(catalogId, version(tenantId, catalogId), status(tenantId, catalogId), products(tenantId), investors(tenantId), references(tenantId), markets(tenantId), replayHash);
  }

  CatalogResponse active(UUID tenantId) {
    List<UUID> ids = jdbc.query("select catalog_id from catalog.product_catalog where tenant_id=? and status='PUBLISHED' order by updated_at desc limit 1", (rs, row) -> rs.getObject(1, UUID.class), tenantId);
    if (ids.isEmpty()) throw new CatalogException("NO_PUBLISHED_CATALOG");
    UUID catalogId = ids.get(0);
    String replayHash = jdbc.queryForObject("select replay_hash from catalog.product_catalog where tenant_id=? and catalog_id=?", String.class, tenantId, catalogId);
    return new CatalogResponse(catalogId, version(tenantId, catalogId), status(tenantId, catalogId), products(tenantId, catalogId), investors(tenantId, catalogId), references(tenantId, catalogId), markets(tenantId, catalogId), replayHash);
  }

  ProductConfigSnapshot resolve(UUID tenantId, ResolveCatalogRequest request) {
    LocalDate asOf = request.asOfDate() == null ? LocalDate.now() : request.asOfDate();
    CatalogResponse activeCatalog = active(tenantId);
    List<ProductDefinition> products = activeCatalog.products().stream().filter(p -> active(p.effectiveFrom(), p.effectiveTo(), asOf)).filter(p -> request.channel() == null || p.allowedChannels().contains(request.channel())).filter(p -> request.stateCode() == null || p.allowedStates().contains(request.stateCode())).filter(p -> request.productFamily() == null || p.productFamily().equals(request.productFamily())).toList();
    List<InvestorProgram> investors = activeCatalog.investors().stream().filter(i -> active(i.effectiveFrom(), i.effectiveTo(), asOf)).filter(i -> request.channel() == null || i.channels().contains(request.channel())).filter(i -> request.investorCode() == null || i.investorCode().equals(request.investorCode())).toList();
    List<ReferenceEntry> refs = activeCatalog.references().stream().filter(r -> active(r.effectiveFrom(), r.effectiveTo(), asOf)).filter(r -> matchesReferenceRequest(r, request)).toList();
    List<MarketArea> markets = activeCatalog.markets().stream().filter(m -> active(m.effectiveFrom(), m.effectiveTo(), asOf)).filter(m -> request.stateCode() == null || m.stateCode().equals(request.stateCode())).filter(m -> request.channel() == null || m.allowedChannels().isEmpty() || m.allowedChannels().contains(request.channel())).toList();
    if (products.isEmpty()) throw new CatalogException("NO_ACTIVE_PRODUCTS");
    if (investors.isEmpty()) throw new CatalogException("NO_ACTIVE_INVESTORS");
    String snapshotHash = hash(products + ":" + investors + ":" + refs + ":" + markets + ":" + asOf);
    UUID snapshotId = jdbc.queryForObject("insert into catalog.product_config_snapshot(snapshot_id,tenant_id,catalog_id,snapshot_hash,request_json,snapshot_json,as_of_date,created_at) values (?,?,?,?,?::jsonb,?::jsonb,?,now()) on conflict (tenant_id,snapshot_hash) do update set snapshot_hash=excluded.snapshot_hash returning snapshot_id",
        UUID.class, UUID.randomUUID(), tenantId, activeCatalog.catalogId(), snapshotHash, json(request), json(Map.of("products", products, "investors", investors, "references", refs, "markets", markets)), java.sql.Date.valueOf(asOf));
    return new ProductConfigSnapshot(snapshotId, tenantId, snapshotHash, asOf, products, investors, refs, markets);
  }

  UUID activeCatalogId(UUID tenantId) {
    List<UUID> ids = jdbc.query("select catalog_id from catalog.product_catalog where tenant_id=? and status='PUBLISHED' order by updated_at desc limit 1", (rs, row) -> rs.getObject(1, UUID.class), tenantId);
    if (ids.isEmpty()) throw new CatalogException("NO_PUBLISHED_CATALOG");
    return ids.get(0);
  }

  ProductConfigSnapshot snapshot(UUID tenantId, UUID snapshotId) {
    List<ProductConfigSnapshot> snapshots = jdbc.query("select snapshot_id,tenant_id,snapshot_hash,as_of_date,snapshot_json from catalog.product_config_snapshot where tenant_id=? and snapshot_id=?", (rs, row) -> {
      Map<String, Object> snapshot = map(rs.getString("snapshot_json"));
      return new ProductConfigSnapshot(rs.getObject("snapshot_id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("snapshot_hash"), rs.getDate("as_of_date").toLocalDate(),
          convertList(snapshot.get("products"), ProductDefinition.class), convertList(snapshot.get("investors"), InvestorProgram.class), convertList(snapshot.get("references"), ReferenceEntry.class), convertList(snapshot.get("markets"), MarketArea.class));
    }, tenantId, snapshotId);
    if (snapshots.isEmpty()) throw new CatalogException("SNAPSHOT_NOT_FOUND");
    return snapshots.get(0);
  }

  void event(CatalogEvent event) {
    UUID catalogId = event.catalogId() == null ? currentCatalogId(event.tenantId()) : event.catalogId();
    jdbc.update("insert into catalog.catalog_outbox_event(tenant_id,event_id,catalog_id,event_type,event_version,payload_json,occurred_at) values (?,?,?,?,?,?::jsonb,?)",
        event.tenantId(), event.eventId(), catalogId, event.eventType(), 1, json(event.payload()), Timestamp.from(event.occurredAt()));
  }

  void audit(UUID tenantId, UUID catalogId, String action, String replayHash, Object before, Object after, Map<String, Object> payload, String actorId, String correlationId, String idempotencyKey) {
    jdbc.update("insert into catalog.catalog_audit_record(tenant_id,audit_id,catalog_id,action,replay_hash,payload_json,before_json,after_json,actor_id,correlation_id,idempotency_key,occurred_at) values (?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?,?,?,now())",
        tenantId, UUID.randomUUID(), catalogId, action, replayHash, json(payload == null ? Map.of() : payload), json(before == null ? Map.of() : before), json(after == null ? Map.of() : after), actorId, correlationId, idempotencyKey);
  }

  List<CatalogAuditRecord> audit(UUID tenantId) {
    return jdbc.query("select audit_id,tenant_id,catalog_id,action,replay_hash,occurred_at,payload_json from catalog.catalog_audit_record where tenant_id=? order by occurred_at desc", (rs, row) -> new CatalogAuditRecord(rs.getObject("audit_id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getObject("catalog_id", UUID.class), rs.getString("action"), rs.getString("replay_hash"), rs.getTimestamp("occurred_at").toInstant(), map(rs.getString("payload_json"))), tenantId);
  }

  List<CatalogEvent> events(UUID tenantId) {
    return jdbc.query("select event_id,tenant_id,catalog_id,event_type,occurred_at,payload_json from catalog.catalog_outbox_event where tenant_id=? order by occurred_at", (rs, row) -> new CatalogEvent(rs.getObject("event_id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getObject("catalog_id", UUID.class), rs.getString("event_type"), rs.getTimestamp("occurred_at").toInstant(), map(rs.getString("payload_json"))), tenantId);
  }

  private List<ProductDefinition> products(UUID tenantId) {
    return jdbc.query("select * from catalog.product_definition where tenant_id=? order by product_code", (rs, row) -> new ProductDefinition(rs.getObject("product_id", UUID.class), rs.getString("product_code"), rs.getString("product_name"), rs.getString("product_family"), strings(rs.getString("allowed_channels")), strings(rs.getString("allowed_states")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId);
  }

  private List<ProductDefinition> products(UUID tenantId, UUID catalogId) {
    return jdbc.query("select * from catalog.product_definition where tenant_id=? and catalog_id=? order by product_code", (rs, row) -> new ProductDefinition(rs.getObject("product_id", UUID.class), rs.getString("product_code"), rs.getString("product_name"), rs.getString("product_family"), strings(rs.getString("allowed_channels")), strings(rs.getString("allowed_states")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, catalogId);
  }

  private List<InvestorProgram> investors(UUID tenantId) {
    return jdbc.query("select * from catalog.investor_program where tenant_id=? order by investor_code", (rs, row) -> new InvestorProgram(rs.getObject("investor_id", UUID.class), rs.getString("investor_code"), rs.getString("investor_name"), strings(rs.getString("channels")), strings(rs.getString("product_codes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId);
  }

  private List<InvestorProgram> investors(UUID tenantId, UUID catalogId) {
    return jdbc.query("select * from catalog.investor_program where tenant_id=? and catalog_id=? order by investor_code", (rs, row) -> new InvestorProgram(rs.getObject("investor_id", UUID.class), rs.getString("investor_code"), rs.getString("investor_name"), strings(rs.getString("channels")), strings(rs.getString("product_codes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, catalogId);
  }

  private List<ReferenceEntry> references(UUID tenantId) {
    return jdbc.query("select * from catalog.reference_entry where tenant_id=? order by catalog_type,code", (rs, row) -> new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId);
  }

  private List<ReferenceEntry> references(UUID tenantId, UUID catalogId) {
    return jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_id=? order by catalog_type,code", (rs, row) -> new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, catalogId);
  }

  private List<MarketArea> markets(UUID tenantId) {
    return jdbc.query("select * from catalog.market_area where tenant_id=? order by state_code,county_fips", (rs, row) -> new MarketArea(rs.getObject("market_id", UUID.class), rs.getString("state_code"), rs.getString("county_fips"), rs.getString("county_name"), rs.getString("market_status"), strings(rs.getString("allowed_channels")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId);
  }

  private List<MarketArea> markets(UUID tenantId, UUID catalogId) {
    return jdbc.query("select * from catalog.market_area where tenant_id=? and catalog_id=? order by state_code,county_fips", (rs, row) -> new MarketArea(rs.getObject("market_id", UUID.class), rs.getString("state_code"), rs.getString("county_fips"), rs.getString("county_name"), rs.getString("market_status"), strings(rs.getString("allowed_channels")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, catalogId);
  }

  private static boolean matchesReferenceRequest(ReferenceEntry ref, ResolveCatalogRequest request) {
    if ("LOAN_PURPOSE".equals(ref.catalogType()) && request.loanPurpose() != null) return ref.code().equals(request.loanPurpose());
    if ("PROPERTY_TYPE".equals(ref.catalogType()) && request.propertyType() != null) return ref.code().equals(request.propertyType());
    if ("OCCUPANCY_TYPE".equals(ref.catalogType()) && request.occupancyType() != null) return ref.code().equals(request.occupancyType());
    if ("TERM_AMORTIZATION".equals(ref.catalogType())) {
      Object term = ref.attributes().get("termMonths");
      Object amortization = ref.attributes().get("amortizationType");
      boolean termMatches = request.termMonths() == null || Objects.toString(term, "").equals(request.termMonths().toString());
      boolean amortizationMatches = request.amortizationType() == null || Objects.toString(amortization, "").equals(request.amortizationType());
      return termMatches && amortizationMatches;
    }
    return true;
  }

  private void requireEditable(UUID tenantId, UUID catalogId) {
    if (status(tenantId, catalogId) != CatalogStatus.DRAFT) throw new CatalogException("CATALOG_NOT_EDITABLE");
  }

  private void bump(UUID tenantId, UUID catalogId) {
    jdbc.update("update catalog.product_catalog set version=version+1, row_version=row_version+1, replay_hash=?, updated_at=now() where tenant_id=? and catalog_id=?", hash("bump:" + tenantId + catalogId + Instant.now()), tenantId, catalogId);
  }

  private void versionControl(UUID tenantId, UUID catalogId, String artifactType, UUID artifactId, String artifactCode, CatalogStatus status, Object snapshot) {
    String configHash = hash(json(snapshot));
    jdbc.update("insert into catalog.catalog_version_control(tenant_id,version_control_id,catalog_id,artifact_type,artifact_id,artifact_code,version_number,status,config_hash,snapshot_json) values (?,?,?,?,?,?,?,?,?,?::jsonb)",
        tenantId, UUID.randomUUID(), catalogId, artifactType, artifactId, artifactCode, 1, status.name(), configHash, json(snapshot));
  }

  private boolean exists(String table, UUID tenantId, String field, String value) {
    Integer count = jdbc.queryForObject("select count(*) from " + table + " where tenant_id=? and " + field + "=?", Integer.class, tenantId, value);
    return count != null && count > 0;
  }

  private boolean existsReference(UUID tenantId, String type, String code) {
    Integer count = jdbc.queryForObject("select count(*) from catalog.reference_entry where tenant_id=? and catalog_type=? and code=?", Integer.class, tenantId, type, code);
    return count != null && count > 0;
  }

  private static boolean active(LocalDate from, LocalDate to, LocalDate asOf) {
    return !from.isAfter(asOf) && (to == null || to.isAfter(asOf));
  }

  private static List<String> safe(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
  private static String required(String value, String code) { if (value == null || value.isBlank()) throw new CatalogException(code); return value; }
  private static LocalDate requiredDate(LocalDate value) { if (value == null) throw new CatalogException("EFFECTIVE_FROM_REQUIRED"); return value; }
  private static java.sql.Date date(LocalDate value) { return value == null ? null : java.sql.Date.valueOf(value); }
  private static LocalDate localDate(java.sql.Date value) { return value == null ? null : value.toLocalDate(); }

  JdbcTemplate getJdbcTemplate() { return jdbc; }

  private String json(Object value) {
    try { return mapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
  }

  private List<String> strings(String json) {
    try { return mapper.readValue(json, STRING_LIST); } catch (Exception ex) { throw new IllegalStateException(ex); }
  }

  private Map<String, Object> map(String json) {
    try { return mapper.readValue(json, MAP_TYPE); } catch (Exception ex) { throw new IllegalStateException(ex); }
  }

  private <T> T readJson(String json, Class<T> type) {
    try { return mapper.readValue(json, type); } catch (Exception ex) { throw new IllegalStateException(ex); }
  }

  private <T> List<T> convertList(Object value, Class<T> type) {
    if (value == null) return List.of();
    return mapper.convertValue(value, mapper.getTypeFactory().constructCollectionType(List.class, type));
  }

  private static String hash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder("sha256:");
      for (byte b : digest) out.append(String.format("%02x", b));
      return out.toString();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
