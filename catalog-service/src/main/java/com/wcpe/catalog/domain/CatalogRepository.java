package com.wcpe.catalog.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
  private static final UUID SYSTEM_TENANT_ID = new UUID(0L, 0L);
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final Set<String> PRICING_CONFIG_REFERENCE_TYPES = Set.of(
      "RATE_SHEET_PROFILE",
      "ADJUSTMENT_POLICY",
      "MARGIN_POLICY",
      "SRP_POLICY");
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

  ProductDefinition addProduct(UUID tenantId, UUID catalogId, ProductRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    if (exists("catalog.product_definition", tenantId, "product_code", request.productCode())) throw new CatalogException("PRODUCT_CODE_DUPLICATE");
    ProductDefinition p = new ProductDefinition(UUID.randomUUID(), required(request.productCode(), "PRODUCT_CODE_REQUIRED"), required(request.productName(), "PRODUCT_NAME_REQUIRED"), required(request.productFamily(), "PRODUCT_FAMILY_REQUIRED"), safe(request.allowedChannels()), safe(request.allowedStates()), requiredDate(request.effectiveFrom()), request.effectiveTo());
    jdbc.update("insert into catalog.product_definition(tenant_id,product_id,catalog_id,product_code,product_name,product_family,allowed_channels,allowed_states,effective_from,effective_to) values (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
        tenantId, p.productId(), catalogId, p.productCode(), p.productName(), p.productFamily(), json(p.allowedChannels()), json(p.allowedStates()), java.sql.Date.valueOf(p.effectiveFrom()), date(p.effectiveTo()));
    versionControl(tenantId, catalogId, "PRODUCT", p.productId(), p.productCode(), CatalogStatus.DRAFT, p, actorId);
    bump(tenantId, catalogId);
    return p;
  }

  ProductCreationPersistence addProductCreation(UUID tenantId, UUID catalogId, ProductCreationDraft draft, String actorId) {
    requireEditable(tenantId, catalogId);
    if (exists("catalog.product_definition", tenantId, "product_code", draft.productCode())) throw new CatalogException("PRODUCT_CODE_DUPLICATE");
    LocalDate effectiveFrom = LocalDate.ofInstant(draft.effectiveStart(), ZoneOffset.UTC);
    LocalDate effectiveTo = draft.effectiveEnd() == null ? null : LocalDate.ofInstant(draft.effectiveEnd(), ZoneOffset.UTC);
    ProductDefinition product = new ProductDefinition(UUID.randomUUID(), draft.productCode(), draft.displayName(), draft.productFamily(), draft.supportedChannels(), draft.allowedStates(), effectiveFrom, effectiveTo);
    jdbc.update("insert into catalog.product_definition(tenant_id,product_id,catalog_id,product_code,product_name,product_family,allowed_channels,allowed_states,effective_from,effective_to) values (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
        tenantId, product.productId(), catalogId, product.productCode(), product.productName(), product.productFamily(), json(product.allowedChannels()), json(product.allowedStates()), java.sql.Date.valueOf(product.effectiveFrom()), date(product.effectiveTo()));
    if (!draft.metadataRefs().isEmpty()) {
      jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
          tenantId, UUID.randomUUID(), catalogId, "LOANPASS_MAPPING_METADATA", draft.productCode(), draft.displayName(), draft.productType(), json(draft.metadataRefs()), java.sql.Date.valueOf(product.effectiveFrom()), date(product.effectiveTo()));
    }
    ProductCreationSnapshot snapshot = new ProductCreationSnapshot(product, draft.productType(), draft.supportedTerms(), draft.amortizationTypes(), draft.loanPurposes(), draft.metadataRefs(), draft.status(), draft.effectiveStart(), draft.effectiveEnd());
    UUID versionId = versionControl(tenantId, catalogId, "PRODUCT", product.productId(), product.productCode(), "ACTIVE".equals(draft.status()) ? CatalogStatus.PUBLISHED : CatalogStatus.DRAFT, snapshot, actorId);
    bump(tenantId, catalogId);
    return new ProductCreationPersistence(product, versionId, draft.status(), draft.metadataRefs());
  }

  ProductPricingConfigurationResponse attachProductPricingConfiguration(UUID tenantId, UUID catalogId, ProductPricingConfigurationRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    String productCode = required(request.productCode(), "PRODUCT_CODE_REQUIRED");
    ProductDefinition product = productByCode(tenantId, catalogId, productCode).orElseThrow(() -> new CatalogException("PRODUCT_CODE_UNKNOWN"));
    UUID productVersionId = versionIdForCode(tenantId, "PRODUCT", product.productCode()).orElse(product.productId());
    Instant effectiveStart = request.effectiveAsOf();
    Instant effectiveEnd = request.effectiveEnd();
    if (effectiveEnd != null && !effectiveEnd.isAfter(effectiveStart)) throw new CatalogException("EFFECTIVE_WINDOW_INVALID");
    List<PricingConfigReference> refs = pricingRefs(request.refs());
    validatePricingConfigRefs(tenantId, effectiveStart, refs);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("productVersionId", productVersionId.toString());
    attributes.put("pricingConfigRefs", refs);
    ReferenceEntry entry = new ReferenceEntry(UUID.randomUUID(), "PRODUCT_PRICING_CONFIGURATION", product.productCode(), "Pricing configuration refs for " + product.productCode(), "PRICING_CONFIGURATION", attributes, LocalDate.ofInstant(effectiveStart, ZoneOffset.UTC), effectiveEnd == null ? null : LocalDate.ofInstant(effectiveEnd, ZoneOffset.UTC));
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, entry.entryId(), catalogId, entry.catalogType(), entry.code(), entry.label(), entry.category(), json(entry.attributes()), java.sql.Date.valueOf(entry.effectiveFrom()), date(entry.effectiveTo()));
    versionControl(tenantId, catalogId, "PRODUCT_PRICING_CONFIGURATION", entry.entryId(), product.productCode(), CatalogStatus.DRAFT, entry, actorId);
    bump(tenantId, catalogId);
    return new ProductPricingConfigurationResponse(product.productCode(), productVersionId, effectiveStart, effectiveEnd, refs, "catalog-audit:" + catalogId + ":pricing-config:" + product.productCode());
  }

  InvestorProgram addInvestor(UUID tenantId, UUID catalogId, InvestorRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    if (exists("catalog.investor_program", tenantId, "investor_code", request.investorCode())) throw new CatalogException("INVESTOR_CODE_DUPLICATE");
    for (String productCode : safe(request.productCodes())) if (!exists("catalog.product_definition", tenantId, "product_code", productCode)) throw new CatalogException("INVESTOR_PRODUCT_UNKNOWN");
    InvestorProgram i = new InvestorProgram(UUID.randomUUID(), required(request.investorCode(), "INVESTOR_CODE_REQUIRED"), required(request.investorName(), "INVESTOR_NAME_REQUIRED"), safe(request.channels()), safe(request.productCodes()), requiredDate(request.effectiveFrom()), request.effectiveTo());
    jdbc.update("insert into catalog.investor_program(tenant_id,investor_id,catalog_id,investor_code,investor_name,channels,product_codes,effective_from,effective_to) values (?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
        tenantId, i.investorId(), catalogId, i.investorCode(), i.investorName(), json(i.channels()), json(i.productCodes()), java.sql.Date.valueOf(i.effectiveFrom()), date(i.effectiveTo()));
    versionControl(tenantId, catalogId, "INVESTOR", i.investorId(), i.investorCode(), CatalogStatus.DRAFT, i, actorId);
    bump(tenantId, catalogId);
    return i;
  }

  ReferenceEntry addReference(UUID tenantId, UUID catalogId, String catalogType, ReferenceCatalogRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    if (existsReference(tenantId, catalogType, request.code())) throw new CatalogException(catalogType + "_CODE_DUPLICATE");
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), catalogType, required(request.code(), "REFERENCE_CODE_REQUIRED"), required(request.label(), "REFERENCE_LABEL_REQUIRED"), request.category(), request.attributes() == null ? Map.of() : Map.copyOf(request.attributes()), requiredDate(request.effectiveFrom()), request.effectiveTo());
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    versionControl(tenantId, catalogId, catalogType, e.entryId(), e.code(), CatalogStatus.DRAFT, e, actorId);
    bump(tenantId, catalogId);
    return e;
  }

  EnumerationTypeResponse addEnumerationType(UUID tenantId, UUID catalogId, EnumerationTypeResponse enumeration, String actorId) {
    requireEditable(tenantId, catalogId);
    if (existsReference(tenantId, "LOANPASS_ENUMERATION", enumeration.enumTypeId())) throw new CatalogException("ENUM_TYPE_DUPLICATE");
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("source", enumeration.source());
    attributes.put("overrideScope", enumeration.overrideScope());
    attributes.put("variants", enumeration.variants());
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), "LOANPASS_ENUMERATION", enumeration.enumTypeId(), enumeration.name(), "SYSTEM_DEFAULT", attributes, LocalDate.now(), null);
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    versionControl(tenantId, catalogId, "LOANPASS_ENUMERATION", e.entryId(), e.code(), CatalogStatus.DRAFT, e, actorId);
    bump(tenantId, catalogId);
    return enumerationFrom(e);
  }

  FieldMetadataResponse addFieldMetadata(UUID tenantId, UUID catalogId, FieldMetadataResponse field, String actorId) {
    requireEditable(tenantId, catalogId);
    if (existsReference(tenantId, "LOANPASS_FIELD_METADATA", field.id())) throw new CatalogException("FIELD_ID_DUPLICATE");
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("oldId", field.oldId());
    attributes.put("description", field.description());
    attributes.put("valueType", field.valueType());
    attributes.put("sourceGroup", field.sourceGroup());
    attributes.put("conditions", field.conditions());
    attributes.put("disposition", field.disposition());
    attributes.put("source", field.source());
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), "LOANPASS_FIELD_METADATA", field.id(), field.name(), field.category(), attributes, LocalDate.now(), null);
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    versionControl(tenantId, catalogId, "LOANPASS_FIELD_METADATA", e.entryId(), e.code(), CatalogStatus.DRAFT, e, actorId);
    bump(tenantId, catalogId);
    return fieldMetadataFrom(e);
  }

  List<FieldMetadataResponse> listFieldMetadata(UUID tenantId) {
    return jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_type='LOANPASS_FIELD_METADATA' order by code",
        (rs, row) -> fieldMetadataFrom(new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to")))), tenantId);
  }

  List<FieldMetadataResponse> listSystemFieldMetadata() {
    return listFieldMetadata(SYSTEM_TENANT_ID);
  }

  List<FieldMetadataResponse> importProductSpecificationFieldsFromSystem(UUID tenantId, UUID catalogId, List<FieldMetadataResponse> selected, String actorId) {
    List<FieldMetadataResponse> imported = new ArrayList<>();
    for (FieldMetadataResponse field : selected == null ? List.<FieldMetadataResponse>of() : selected) {
      imported.add(addFieldMetadata(tenantId, catalogId, field, actorId));
    }
    return List.copyOf(imported);
  }

  List<EnumerationTypeResponse> listEnumerations(UUID tenantId) {
    return jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_type='LOANPASS_ENUMERATION' order by code",
        (rs, row) -> enumerationFrom(new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to")))), tenantId);
  }

  FieldMetadataResponse resolveFieldMetadata(UUID tenantId, String fieldId) {
    String id = fieldId == null ? "" : fieldId.trim();
    if (id.isBlank()) throw new CatalogException("FIELD_ID_REQUIRED");
    List<ReferenceEntry> rows = jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_type='LOANPASS_FIELD_METADATA' and code=? order by effective_from desc limit 1",
        (rs, row) -> new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, id);
    if (rows.isEmpty()) throw new CatalogException("FIELD_METADATA_NOT_FOUND");
    return fieldMetadataFrom(rows.get(0));
  }

  EnumerationTypeResponse resolveEnumeration(UUID tenantId, String enumTypeId) {
    String normalized = enumTypeId == null ? "" : enumTypeId.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    if (normalized.isBlank()) throw new CatalogException("ENUM_TYPE_ID_REQUIRED");
    List<ReferenceEntry> rows = jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_type='LOANPASS_ENUMERATION' and code=? order by effective_from desc limit 1",
        (rs, row) -> new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, normalized);
    if (rows.isEmpty()) throw new CatalogException("ENUM_TYPE_NOT_FOUND");
    return enumerationFrom(rows.get(0));
  }

  Optional<ProductSpecificationFieldOrderDraft> productSpecificationFieldOrderDraft(UUID tenantId) {
    List<ReferenceEntry> rows = jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_type='PRODUCT_SPEC_FIELD_ORDER' and code='product-spec-field-order:draft' order by effective_from desc limit 1",
        (rs, row) -> new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId);
    return rows.isEmpty() ? Optional.empty() : Optional.of(productSpecFieldOrderFrom(rows.get(0)));
  }

  Optional<ProductSpecificationTenantFieldDraft> productSpecificationTenantFieldDraft(UUID tenantId) {
    List<ReferenceEntry> rows = jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_type='PRODUCT_SPEC_TENANT_FIELD_DRAFT' and code='product-spec-tenant-field:draft' order by effective_from desc limit 1",
        (rs, row) -> new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId);
    return rows.isEmpty() ? Optional.empty() : Optional.of(productSpecTenantFieldDraftFrom(rows.get(0)));
  }

  Optional<ProductSpecificationConditionDraft> productSpecificationConditionDraft(UUID tenantId) {
    List<ReferenceEntry> rows = jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_type='PRODUCT_SPEC_CONDITION_DRAFT' and code='product-spec-condition:draft' order by effective_from desc limit 1",
        (rs, row) -> new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId);
    return rows.isEmpty() ? Optional.empty() : Optional.of(productSpecConditionDraftFrom(rows.get(0)));
  }

  ProductSpecificationFieldOrderDraft saveProductSpecificationFieldOrderDraft(UUID tenantId, UUID catalogId, ProductSpecificationFieldOrderDraft draft) {
    requireEditable(tenantId, catalogId);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("draftStatus", draft.draftStatus());
    attributes.put("fieldIds", draft.fieldIds());
    attributes.put("savedAt", draft.savedAt().toString());
    attributes.put("actorId", draft.actorId());
    jdbc.update("""
        insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to)
        values (?,?,?,?,?,?,?,?::jsonb,?,null)
        on conflict (tenant_id,catalog_type,code) do update set catalog_id=excluded.catalog_id,label=excluded.label,category=excluded.category,attributes=excluded.attributes,effective_from=excluded.effective_from,effective_to=null
        """, tenantId, UUID.randomUUID(), catalogId, "PRODUCT_SPEC_FIELD_ORDER", "product-spec-field-order:draft",
        "Product specification field order draft", "PRODUCT_SPECIFICATION", json(attributes), java.sql.Date.valueOf(LocalDate.now()));
    bump(tenantId, catalogId);
    return draft;
  }

  ProductSpecificationTenantFieldDraft saveProductSpecificationTenantFieldDraft(UUID tenantId, UUID catalogId, ProductSpecificationTenantFieldDraft draft) {
    requireEditable(tenantId, catalogId);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("draftStatus", draft.draftStatus());
    attributes.put("aliases", draft.aliases());
    attributes.put("nativeFields", draft.nativeFields());
    attributes.put("savedAt", draft.savedAt().toString());
    attributes.put("actorId", draft.actorId());
    jdbc.update("""
        insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to)
        values (?,?,?,?,?,?,?,?::jsonb,?,null)
        on conflict (tenant_id,catalog_type,code) do update set catalog_id=excluded.catalog_id,label=excluded.label,category=excluded.category,attributes=excluded.attributes,effective_from=excluded.effective_from,effective_to=null
        """, tenantId, UUID.randomUUID(), catalogId, "PRODUCT_SPEC_TENANT_FIELD_DRAFT", "product-spec-tenant-field:draft",
        "Product specification tenant field draft", "PRODUCT_SPECIFICATION", json(attributes), java.sql.Date.valueOf(LocalDate.now()));
    bump(tenantId, catalogId);
    return draft;
  }

  ProductSpecificationConditionDraft saveProductSpecificationConditionDraft(UUID tenantId, UUID catalogId, ProductSpecificationConditionDraft draft) {
    requireEditable(tenantId, catalogId);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("draftStatus", draft.draftStatus());
    attributes.put("includeConditions", draft.includeConditions());
    attributes.put("additionalConditions", draft.additionalConditions());
    attributes.put("savedAt", draft.savedAt().toString());
    attributes.put("actorId", draft.actorId());
    jdbc.update("""
        insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to)
        values (?,?,?,?,?,?,?,?::jsonb,?,null)
        on conflict (tenant_id,catalog_type,code) do update set catalog_id=excluded.catalog_id,label=excluded.label,category=excluded.category,attributes=excluded.attributes,effective_from=excluded.effective_from,effective_to=null
        """, tenantId, UUID.randomUUID(), catalogId, "PRODUCT_SPEC_CONDITION_DRAFT", "product-spec-condition:draft",
        "Product specification condition draft", "PRODUCT_SPECIFICATION", json(attributes), java.sql.Date.valueOf(LocalDate.now()));
    bump(tenantId, catalogId);
    return draft;
  }

  TermAmortizationDraftResponse addTermAmortizationDraft(UUID tenantId, UUID catalogId, TermAmortizationDraftRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    TermAmortizationProfilePolicy.validateDraft(request, existsReference(tenantId, "TERM_AMORTIZATION", request.profileCode()));
    LocalDate effectiveFrom = LocalDate.ofInstant(request.effectiveStart(), ZoneOffset.UTC);
    LocalDate effectiveTo = request.effectiveEnd() == null ? null : LocalDate.ofInstant(request.effectiveEnd(), ZoneOffset.UTC);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("termMonths", request.termMonths());
    attributes.put("amortizationType", request.amortizationType());
    attributes.put("interestOnlyAllowed", Boolean.TRUE.equals(request.interestOnlyAllowed()));
    attributes.put("balloonAllowed", Boolean.TRUE.equals(request.balloonAllowed()));
    attributes.put("armIndexCode", request.armIndexCode());
    attributes.put("initialFixedMonths", request.initialFixedMonths());
    attributes.put("adjustmentPeriodMonths", request.adjustmentPeriodMonths());
    attributes.put("lookbackDays", request.lookbackDays());
    attributes.put("roundingIncrementBps", request.roundingIncrementBps());
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), "TERM_AMORTIZATION", request.profileCode(), request.displayName(), request.amortizationType(), attributes, effectiveFrom, effectiveTo);
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    UUID versionId = versionControl(tenantId, catalogId, "TERM_AMORTIZATION", e.entryId(), e.code(), CatalogStatus.DRAFT, e, actorId);
    bump(tenantId, catalogId);
    return new TermAmortizationDraftResponse(e.entryId(), versionId, CatalogStatus.DRAFT, new TermAmortizationValidation(List.of(), List.of()));
  }

  PropertyTypeDraftResponse addPropertyTypeDraft(UUID tenantId, UUID catalogId, PropertyTypeDraftRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    PropertyOccupancyCatalogPolicy.validatePropertyDraft(request, request != null && existsReference(tenantId, "PROPERTY_TYPE", request.code()));
    LocalDate effectiveFrom = LocalDate.ofInstant(request.effectiveStart(), ZoneOffset.UTC);
    LocalDate effectiveTo = request.effectiveEnd() == null ? null : LocalDate.ofInstant(request.effectiveEnd(), ZoneOffset.UTC);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("agencyAliases", safe(request.agencyAliases()));
    attributes.put("eligibleForConventional", Boolean.TRUE.equals(request.eligibleForConventional()));
    attributes.put("requiresProjectReview", Boolean.TRUE.equals(request.requiresProjectReview()));
    attributes.put("unitCountMin", request.unitCountMin());
    attributes.put("unitCountMax", request.unitCountMax());
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), "PROPERTY_TYPE", request.code(), request.displayName(), request.category() == null ? "PROPERTY" : request.category(), attributes, effectiveFrom, effectiveTo);
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    UUID versionId = versionControl(tenantId, catalogId, "PROPERTY_TYPE", e.entryId(), e.code(), CatalogStatus.DRAFT, e, actorId);
    bump(tenantId, catalogId);
    return new PropertyTypeDraftResponse(e.entryId(), versionId, CatalogStatus.DRAFT, new PropertyOccupancyValidation(List.of(), List.of()));
  }

  OccupancyTypeDraftResponse addOccupancyTypeDraft(UUID tenantId, UUID catalogId, OccupancyTypeDraftRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    PropertyOccupancyCatalogPolicy.validateOccupancyDraft(request, request != null && existsReference(tenantId, "OCCUPANCY_TYPE", request.code()));
    LocalDate effectiveFrom = LocalDate.ofInstant(request.effectiveStart(), ZoneOffset.UTC);
    LocalDate effectiveTo = request.effectiveEnd() == null ? null : LocalDate.ofInstant(request.effectiveEnd(), ZoneOffset.UTC);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("agencyAliases", safe(request.agencyAliases()));
    attributes.put("eligibleForConventional", Boolean.TRUE.equals(request.eligibleForConventional()));
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), "OCCUPANCY_TYPE", request.code(), request.displayName(), "OCCUPANCY", attributes, effectiveFrom, effectiveTo);
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    UUID versionId = versionControl(tenantId, catalogId, "OCCUPANCY_TYPE", e.entryId(), e.code(), CatalogStatus.DRAFT, e, actorId);
    bump(tenantId, catalogId);
    return new OccupancyTypeDraftResponse(e.entryId(), versionId, CatalogStatus.DRAFT, new PropertyOccupancyValidation(List.of(), List.of()));
  }

  LoanPurposeDraftResponse addLoanPurposeDraft(UUID tenantId, UUID catalogId, LoanPurposeDraftRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    String requestedCode = request == null || request.purposeCode() == null ? "" : request.purposeCode().trim().toUpperCase(Locale.ROOT);
    LoanPurposeCatalogPolicy.validateDraft(request, existsReference(tenantId, "LOAN_PURPOSE", requestedCode));
    LocalDate effectiveFrom = LocalDate.ofInstant(request.effectiveStart(), ZoneOffset.UTC);
    LocalDate effectiveTo = request.effectiveEnd() == null ? null : LocalDate.ofInstant(request.effectiveEnd(), ZoneOffset.UTC);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("isRefinance", Boolean.TRUE.equals(request.isRefinance()));
    attributes.put("isCashOut", Boolean.TRUE.equals(request.isCashOut()));
    attributes.put("requiresExistingLien", Boolean.TRUE.equals(request.requiresExistingLien()));
    attributes.put("eligibleForConventional", Boolean.TRUE.equals(request.eligibleForConventional()));
    attributes.put("agencyAliases", LoanPurposeCatalogPolicy.canonicalAliases(request));
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), "LOAN_PURPOSE", request.purposeCode().trim().toUpperCase(Locale.ROOT), request.displayName(), request.category(), attributes, effectiveFrom, effectiveTo);
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    UUID versionId = versionControl(tenantId, catalogId, "LOAN_PURPOSE", e.entryId(), e.code(), CatalogStatus.DRAFT, e, actorId);
    bump(tenantId, catalogId);
    return new LoanPurposeDraftResponse(e.entryId(), versionId, CatalogStatus.DRAFT, new LoanPurposeValidation(List.of(), List.of()));
  }

  ProductTaxonomyDraftResponse addProductTaxonomyDraft(UUID tenantId, UUID catalogId, ProductTaxonomyDraftRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    validateTaxonomyRequest(tenantId, catalogId, request);
    LocalDate effectiveFrom = LocalDate.ofInstant(request.effectiveStart(), ZoneOffset.UTC);
    LocalDate effectiveTo = request.effectiveEnd() == null ? null : LocalDate.ofInstant(request.effectiveEnd(), ZoneOffset.UTC);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("level", request.level());
    attributes.put("agencyCategory", request.agencyCategory());
    attributes.put("displayOrder", request.displayOrder());
    if (request.parentCode() != null && !request.parentCode().isBlank()) attributes.put("parentCode", request.parentCode());
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), "PRODUCT_TAXONOMY", request.code(), request.name(), request.agencyCategory(), attributes, effectiveFrom, effectiveTo);
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    UUID versionId = versionControl(tenantId, catalogId, "PRODUCT_TAXONOMY", e.entryId(), e.code(), CatalogStatus.DRAFT, e, actorId);
    bump(tenantId, catalogId);
    return new ProductTaxonomyDraftResponse(e.entryId(), versionId, CatalogStatus.DRAFT, new ProductTaxonomyValidation(List.of(), List.of()));
  }

  ChannelTaxonomyDraftResponse addChannelTaxonomyDraft(UUID tenantId, UUID catalogId, ChannelTaxonomyDraftRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    ChannelTaxonomyPolicy.validateDraft(request, existsReference(tenantId, "CHANNEL", request.channelCode()), (sourceSystem, externalValue) -> channelMappingExists(tenantId, catalogId, sourceSystem, externalValue));
    LocalDate effectiveFrom = LocalDate.ofInstant(request.effectiveStart(), ZoneOffset.UTC);
    LocalDate effectiveTo = request.effectiveEnd() == null ? null : LocalDate.ofInstant(request.effectiveEnd(), ZoneOffset.UTC);
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("description", request.description());
    attributes.put("sourceSystemMappings", request.sourceSystemMappings() == null ? List.of() : request.sourceSystemMappings());
    attributes.put("allowedSourceSystems", request.allowedSourceSystems());
    attributes.put("requiresBranchAssignment", Boolean.TRUE.equals(request.requiresBranchAssignment()));
    attributes.put("defaultMarginGroupCode", request.defaultMarginGroupCode());
    ReferenceEntry e = new ReferenceEntry(UUID.randomUUID(), "CHANNEL", request.channelCode(), request.displayName(), "CHANNEL", attributes, effectiveFrom, effectiveTo);
    jdbc.update("insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to) values (?,?,?,?,?,?,?,?::jsonb,?,?)",
        tenantId, e.entryId(), catalogId, e.catalogType(), e.code(), e.label(), e.category(), json(e.attributes()), java.sql.Date.valueOf(e.effectiveFrom()), date(e.effectiveTo()));
    UUID versionId = versionControl(tenantId, catalogId, "CHANNEL", e.entryId(), e.code(), CatalogStatus.DRAFT, e, actorId);
    bump(tenantId, catalogId);
    return new ChannelTaxonomyDraftResponse(e.entryId(), versionId, CatalogStatus.DRAFT, new ChannelTaxonomyValidation(List.of(), List.of()));
  }

  ConventionalProductDraftResponse addConventionalProductDraft(UUID tenantId, UUID catalogId, ConventionalProductDraftRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    validateConventionalProductStructure(request);
    if (exists("catalog.conventional_product_definition", tenantId, "product_code", request.productCode())) throw new CatalogException("PRODUCT_CODE_DUPLICATE");

    List<ProductTaxonomyValidationMessage> blockingErrors = referenceValidationErrors(tenantId, request);
    UUID definitionId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    String configHash = hash(json(request));
    LocalDate effectiveStart = LocalDate.ofInstant(requiredInstant(request.effectiveStart()), ZoneOffset.UTC);
    LocalDate effectiveEnd = request.effectiveEnd() == null ? null : LocalDate.ofInstant(request.effectiveEnd(), ZoneOffset.UTC);

    jdbc.update("insert into catalog.conventional_product_definition(tenant_id,id,catalog_id,product_code,created_by) values (?,?,?,?,?)",
        tenantId, definitionId, catalogId, required(request.productCode(), "PRODUCT_CODE_REQUIRED"), createdBy(actorId));
    jdbc.update("insert into catalog.conventional_product_version(tenant_id,id,product_definition_id,version_number,product_name,taxonomy_type_code,status,amortization_type,arm_index_code,fixed_period_months,adjustment_period_months,min_loan_amount,max_loan_amount,effective_start,effective_end,config_hash,request_json,blocking_errors_json) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)",
        tenantId, versionId, definitionId, 1, required(request.productName(), "PRODUCT_NAME_REQUIRED"), required(request.taxonomyTypeCode(), "TAXONOMY_TYPE_REQUIRED"), CatalogStatus.DRAFT.name(), required(request.amortizationType(), "AMORTIZATION_TYPE_REQUIRED"), request.armIndexCode(), request.fixedPeriodMonths(), request.adjustmentPeriodMonths(), request.minLoanAmount(), request.maxLoanAmount(), Timestamp.from(requiredInstant(request.effectiveStart())), timestamp(request.effectiveEnd()), configHash, json(request), json(blockingErrors));
    insertAllowedValues(tenantId, versionId, request);
    versionControl(tenantId, catalogId, "CONVENTIONAL_PRODUCT", versionId, request.productCode(), CatalogStatus.DRAFT, request, actorId);
    bump(tenantId, catalogId);
    return new ConventionalProductDraftResponse(definitionId, versionId, CatalogStatus.DRAFT, new ConventionalProductValidation(blockingErrors, List.of()));
  }

  InvestorCatalogDraftResponse addInvestorCatalogDraft(UUID tenantId, UUID catalogId, InvestorCatalogDraftRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    InvestorCatalogPolicy.validateDraft(request, exists("catalog.investor_catalog_entry", tenantId, "investor_code", request.investorCode()));
    UUID investorId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    String configHash = hash(json(request));
    CatalogStatus status = request.status() == null ? CatalogStatus.DRAFT : request.status();
    if (status != CatalogStatus.DRAFT) throw new CatalogException("INVALID_INVESTOR_DRAFT_STATUS");

    jdbc.update("insert into catalog.investor_catalog_entry(tenant_id,id,catalog_id,investor_code,created_by) values (?,?,?,?,?)",
        tenantId, investorId, catalogId, request.investorCode(), createdBy(actorId));
    jdbc.update("insert into catalog.investor_catalog_version(tenant_id,id,investor_id,version_number,legal_name,investor_type,agency,delivery_types,active_channel_codes,requires_mi_validation,status,effective_start,effective_end,config_hash,request_json) values (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?,?::jsonb)",
        tenantId, versionId, investorId, 1, request.legalName(), request.investorType(), request.agency(), json(safe(request.deliveryTypes())), json(safe(request.activeChannelCodes())), Boolean.TRUE.equals(request.requiresMiValidation()), status.name(), Timestamp.from(requiredInstant(request.effectiveStart())), timestamp(request.effectiveEnd()), configHash, json(request));
    for (InvestorSellerServicerId seller : request.sellerServicerIds()) {
      jdbc.update("insert into catalog.investor_seller_servicer(tenant_id,id,investor_version_id,channel_code,seller_id,servicer_id) values (?,?,?,?,?,?)",
          tenantId, UUID.randomUUID(), versionId, seller.channelCode(), seller.sellerId(), seller.servicerId());
    }
    versionControl(tenantId, catalogId, "INVESTOR", versionId, request.investorCode(), CatalogStatus.DRAFT, sanitizedInvestorSnapshot(request), actorId);
    bump(tenantId, catalogId);
    return new InvestorCatalogDraftResponse(investorId, versionId, status, new InvestorCatalogValidation(List.of(), List.of()));
  }

  MarketChange addMarket(UUID tenantId, UUID catalogId, MarketRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    String stateCode = MarketCatalogPolicy.requireStateCode(request.stateCode());
    String countyFips = MarketCatalogPolicy.normalizeCountyFips(stateCode, request.countyFips());
    String marketStatus = MarketCatalogPolicy.requireStatus(request.marketStatus());
    MarketArea m = new MarketArea(UUID.randomUUID(), stateCode, null, countyFips, request.countyName(), marketStatus, null, safe(request.allowedChannels()), List.of(), requiredDate(request.effectiveFrom()), request.effectiveTo());
    jdbc.update("insert into catalog.market_area(tenant_id,market_id,catalog_id,state_code,state_name,county_fips,county_name,market_status,restriction_reason_code,allowed_channels,allowed_product_codes,status,effective_from,effective_to) values (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?)",
        tenantId, m.marketId(), catalogId, m.stateCode(), m.stateName(), m.countyFips(), m.countyName(), m.marketStatus(), m.restrictionReasonCode(), json(m.allowedChannels()), json(m.allowedProducts()), CatalogStatus.DRAFT.name(), java.sql.Date.valueOf(m.effectiveFrom()), date(m.effectiveTo()));
    String configHash = hash(json(m));
    UUID marketVersionId = versionControl(tenantId, catalogId, "MARKET", m.marketId(), m.stateCode() + ":" + Objects.toString(m.countyFips(), "*"), CatalogStatus.DRAFT, m, actorId);
    bump(tenantId, catalogId);
    return new MarketChange(m, MarketCatalogPolicy.changedPayload(tenantId, marketVersionId, m, 1, configHash));
  }

  MarketImportResult importMarkets(UUID tenantId, UUID catalogId, MarketImportRequest request, String actorId) {
    requireEditable(tenantId, catalogId);
    if (request == null || request.importName() == null || request.importName().isBlank()) throw new CatalogException("MARKET_IMPORT_NAME_REQUIRED");
    List<MarketImportRow> rows = request.rows() == null ? List.of() : request.rows();
    if (rows.isEmpty()) throw new CatalogException("MARKET_IMPORT_ROWS_REQUIRED");
    String sourceHash = hash(json(request));
    if (marketImportSourceHashExists(tenantId, sourceHash)) throw new CatalogException("IMPORT_ALREADY_PROCESSED");
    UUID importId = UUID.randomUUID();
    int accepted = 0;
    int rejected = 0;
    List<Map<String, Object>> changedMarkets = new ArrayList<>();
    for (MarketImportRow row : rows) {
      try {
        MarketArea area = insertMarketImportRow(tenantId, catalogId, row);
        String configHash = hash(json(area));
        UUID marketVersionId = versionControl(tenantId, catalogId, "MARKET", area.marketId(), area.stateCode() + ":" + Objects.toString(area.countyFips(), "*"), CatalogStatus.DRAFT, area, actorId);
        changedMarkets.add(MarketCatalogPolicy.changedPayload(tenantId, marketVersionId, area, 1, configHash));
        accepted++;
      } catch (CatalogException ex) {
        rejected++;
      }
    }
    if (accepted == 0) throw new CatalogException("MARKET_IMPORT_NO_VALID_ROWS");
    jdbc.update("insert into catalog.market_import_batch(tenant_id,id,catalog_id,import_name,status,accepted_rows,rejected_rows,created_by,source_hash) values (?,?,?,?,?,?,?,?,?)",
        tenantId, importId, catalogId, request.importName(), "VALIDATED", accepted, rejected, createdBy(actorId), sourceHash);
    bump(tenantId, catalogId);
    return new MarketImportResult(new MarketImportResponse(importId, accepted, rejected, "VALIDATED"), List.copyOf(changedMarkets));
  }

  private boolean marketImportSourceHashExists(UUID tenantId, String sourceHash) {
    Integer count = jdbc.queryForObject("select count(*) from catalog.market_import_batch where tenant_id=? and source_hash=?", Integer.class, tenantId, sourceHash);
    return count != null && count > 0;
  }

  private MarketArea insertMarketImportRow(UUID tenantId, UUID catalogId, MarketImportRow row) {
    if (row == null) throw new CatalogException("MARKET_IMPORT_ROW_REQUIRED");
    String stateCode = MarketCatalogPolicy.requireStateCode(row.stateCode());
    String countyFips = MarketCatalogPolicy.normalizeCountyFips(stateCode, row.countyFips());
    String status = MarketCatalogPolicy.requireStatus(row.marketStatus());
    Instant effectiveStart = requiredInstant(row.effectiveStart());
    LocalDate effectiveFrom = LocalDate.ofInstant(effectiveStart, ZoneOffset.UTC);
    LocalDate effectiveTo = row.effectiveEnd() == null ? null : LocalDate.ofInstant(row.effectiveEnd(), ZoneOffset.UTC);
    MarketArea area = new MarketArea(UUID.randomUUID(), stateCode, row.stateName(), countyFips, row.countyName(), status, row.restrictionReasonCode(), safe(row.allowedChannelCodes()), safe(row.allowedProductCodes()), effectiveFrom, effectiveTo);
    jdbc.update("insert into catalog.market_area(tenant_id,market_id,catalog_id,state_code,state_name,county_fips,county_name,market_status,restriction_reason_code,allowed_channels,allowed_product_codes,status,effective_from,effective_to) values (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?)",
        tenantId, area.marketId(), catalogId, area.stateCode(), area.stateName(), area.countyFips(), area.countyName(), area.marketStatus(), area.restrictionReasonCode(), json(area.allowedChannels()), json(area.allowedProducts()), CatalogStatus.DRAFT.name(), java.sql.Date.valueOf(area.effectiveFrom()), date(area.effectiveTo()));
    return area;
  }

  void publish(UUID tenantId, UUID catalogId) {
    boolean hasTaxonomy = !productTaxonomyEntries(tenantId, catalogId).isEmpty();
    boolean hasInvestorCatalog = hasInvestorCatalogEntries(tenantId, catalogId);
    if (products(tenantId).isEmpty() && !hasTaxonomy && !hasInvestorCatalog) throw new CatalogException("CATALOG_PRODUCTS_REQUIRED");
    if (!products(tenantId).isEmpty() && investors(tenantId).isEmpty() && !hasInvestorCatalog) throw new CatalogException("CATALOG_INVESTORS_REQUIRED");
    Integer unresolvedConventionalDefinitions = jdbc.queryForObject("select count(*) from catalog.conventional_product_version v join catalog.conventional_product_definition d on d.tenant_id=v.tenant_id and d.id=v.product_definition_id where v.tenant_id=? and d.catalog_id=? and jsonb_array_length(v.blocking_errors_json) > 0", Integer.class, tenantId, catalogId);
    if (unresolvedConventionalDefinitions != null && unresolvedConventionalDefinitions > 0) throw new CatalogException("REFERENCE_NOT_PUBLISHED");
    transition(tenantId, catalogId, CatalogStatus.APPROVED, CatalogStatus.PUBLISHED);
  }

  Optional<UUID> publishProductSpecificationVersion(UUID tenantId, UUID catalogId, String actorId) {
    List<FieldMetadataResponse> fields = listFieldMetadata(tenantId);
    Optional<ProductSpecificationFieldOrderDraft> orderDraft = productSpecificationFieldOrderDraft(tenantId);
    Optional<ProductSpecificationTenantFieldDraft> tenantFieldDraft = productSpecificationTenantFieldDraft(tenantId);
    Optional<ProductSpecificationConditionDraft> conditionDraft = productSpecificationConditionDraft(tenantId);
    ProductSpecificationFieldListResponse fieldList = ProductSpecificationFieldListPolicy.list(fields, orderDraft, tenantFieldDraft, true);
    if (fieldList.fields().isEmpty() && orderDraft.isEmpty() && tenantFieldDraft.isEmpty() && conditionDraft.isEmpty()) return Optional.empty();

    UUID entryId = productSpecificationEntryId(tenantId).orElse(UUID.randomUUID());
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("sourceScope", fieldList.sourceScope());
    attributes.put("fieldCount", fieldList.payloadFieldCount());
    attributes.put("fields", fieldList.fields());
    orderDraft.ifPresent(draft -> attributes.put("fieldOrderDraft", draft));
    tenantFieldDraft.ifPresent(draft -> attributes.put("tenantFieldDraft", draft));
    conditionDraft.ifPresent(draft -> attributes.put("conditionDraft", draft));
    attributes.put("publishedAt", Instant.now().toString());
    jdbc.update("""
        insert into catalog.reference_entry(tenant_id,entry_id,catalog_id,catalog_type,code,label,category,attributes,effective_from,effective_to)
        values (?,?,?,?,?,?,?,?::jsonb,?,null)
        on conflict (tenant_id,catalog_type,code) do update set catalog_id=excluded.catalog_id,label=excluded.label,category=excluded.category,attributes=excluded.attributes,effective_from=excluded.effective_from,effective_to=null
        """, tenantId, entryId, catalogId, "PRODUCT_SPECIFICATION", "product-specification:published",
        "Published product specification", "PRODUCT_SPECIFICATION", json(attributes), java.sql.Date.valueOf(LocalDate.now()));
    int versionNumber = nextVersionNumber(tenantId, "PRODUCT_SPECIFICATION", entryId);
    return Optional.of(versionControl(tenantId, catalogId, "PRODUCT_SPECIFICATION", entryId, "product-specification", CatalogStatus.DRAFT, attributes, actorId, versionNumber));
  }

  void transition(UUID tenantId, UUID catalogId, CatalogStatus expected, CatalogStatus next) {
    int updated = jdbc.update("update catalog.product_catalog set status=?, version=version+1, replay_hash=?, updated_at=now() where tenant_id=? and catalog_id=? and status=?",
        next.name(), hash(next + ":" + tenantId + ":" + catalogId + ":" + Instant.now()), tenantId, catalogId, expected.name());
    if (updated == 0) throw new CatalogException("INVALID_CATALOG_STATUS_TRANSITION");
    jdbc.update("update catalog.catalog_version_control set status=?, row_version=row_version+1, updated_at=now() where tenant_id=? and catalog_id=? and status=?", next.name(), tenantId, catalogId, expected.name());
    jdbc.update("update catalog.conventional_product_version v set status=?, row_version=row_version+1, updated_at=now() from catalog.conventional_product_definition d where v.tenant_id=d.tenant_id and v.product_definition_id=d.id and v.tenant_id=? and d.catalog_id=? and v.status=?", next.name(), tenantId, catalogId, expected.name());
    jdbc.update("update catalog.investor_catalog_version v set status=?, row_version=row_version+1, updated_at=now() from catalog.investor_catalog_entry e where v.tenant_id=e.tenant_id and v.investor_id=e.id and v.tenant_id=? and e.catalog_id=? and v.status=?", next.name(), tenantId, catalogId, expected.name());
    jdbc.update("update catalog.market_area set status=?, row_version=row_version+1 where tenant_id=? and catalog_id=? and status=?", next.name(), tenantId, catalogId, expected.name());
  }

  void requireVersion(UUID tenantId, UUID catalogId, int expectedVersion) {
    if (version(tenantId, catalogId) != expectedVersion) throw new CatalogException("CATALOG_VERSION_CONFLICT");
  }

  void forceStatus(UUID tenantId, UUID catalogId, CatalogStatus next) {
    jdbc.update("update catalog.product_catalog set status=?, version=version+1, replay_hash=?, updated_at=now() where tenant_id=? and catalog_id=?",
        next.name(), hash(next + ":" + tenantId + ":" + catalogId + ":" + Instant.now()), tenantId, catalogId);
    jdbc.update("update catalog.catalog_version_control set status=?, row_version=row_version+1, updated_at=now() where tenant_id=? and catalog_id=?", next.name(), tenantId, catalogId);
    jdbc.update("update catalog.conventional_product_version v set status=?, row_version=row_version+1, updated_at=now() from catalog.conventional_product_definition d where v.tenant_id=d.tenant_id and v.product_definition_id=d.id and v.tenant_id=? and d.catalog_id=?", next.name(), tenantId, catalogId);
    jdbc.update("update catalog.investor_catalog_version v set status=?, row_version=row_version+1, updated_at=now() from catalog.investor_catalog_entry e where v.tenant_id=e.tenant_id and v.investor_id=e.id and v.tenant_id=? and e.catalog_id=?", next.name(), tenantId, catalogId);
    jdbc.update("update catalog.market_area set status=?, row_version=row_version+1 where tenant_id=? and catalog_id=?", next.name(), tenantId, catalogId);
  }

  void resetToDraft(UUID tenantId, UUID catalogId) {
    jdbc.update("update catalog.product_catalog set status='DRAFT', version=version+1, replay_hash=?, updated_at=now() where tenant_id=? and catalog_id=?",
        hash("DRAFT:" + tenantId + ":" + catalogId + ":" + Instant.now()), tenantId, catalogId);
    jdbc.update("update catalog.catalog_version_control set status='DRAFT', row_version=row_version+1, updated_at=now() where tenant_id=? and catalog_id=?", tenantId, catalogId);
    jdbc.update("update catalog.conventional_product_version v set status='DRAFT', row_version=row_version+1, updated_at=now() from catalog.conventional_product_definition d where v.tenant_id=d.tenant_id and v.product_definition_id=d.id and v.tenant_id=? and d.catalog_id=?", tenantId, catalogId);
    jdbc.update("update catalog.investor_catalog_version v set status='DRAFT', row_version=row_version+1, updated_at=now() from catalog.investor_catalog_entry e where v.tenant_id=e.tenant_id and v.investor_id=e.id and v.tenant_id=? and e.catalog_id=?", tenantId, catalogId);
    jdbc.update("update catalog.market_area set status='DRAFT', row_version=row_version+1 where tenant_id=? and catalog_id=?", tenantId, catalogId);
  }

  List<CatalogVersionControlRecord> versionControls(UUID tenantId, UUID catalogId) {
    return jdbc.query("select version_control_id,catalog_id,artifact_type,artifact_id,artifact_code,version_number,status,config_hash,row_version from catalog.catalog_version_control where tenant_id=? and catalog_id=? order by artifact_type,artifact_code,version_number",
        (rs, row) -> new CatalogVersionControlRecord(rs.getObject("version_control_id", UUID.class), rs.getObject("catalog_id", UUID.class), rs.getString("artifact_type"), rs.getObject("artifact_id", UUID.class), rs.getString("artifact_code"), rs.getInt("version_number"), CatalogStatus.valueOf(rs.getString("status")), rs.getString("config_hash"), rs.getLong("row_version")), tenantId, catalogId);
  }

  CatalogVersionActionResponse applyVersionAction(UUID tenantId, String artifactType, UUID artifactId, CatalogVersionActionRequest request, String actorId) {
    String action = required(request.action(), "VERSION_ACTION_REQUIRED").toUpperCase(Locale.ROOT);
    Map<String, Object> row = versionControlRow(tenantId, artifactType, artifactId, request.versionId());
    UUID versionControlId = (UUID) row.get("version_control_id");
    CatalogStatus oldStatus = CatalogStatus.valueOf(Objects.toString(row.get("status"), ""));
    long currentRowVersion = ((Number) row.get("row_version")).longValue();
    if (request.rowVersion() == null || request.rowVersion() != currentRowVersion) throw new CatalogException("VERSION_CONFLICT");

    if ("ROLLBACK".equals(action)) {
      CatalogVersionActionResponse rollback = createRollbackDraft(tenantId, row, actorId, request.reason());
      jdbc.update("update catalog.catalog_version_control set status=?, status_reason=?, row_version=row_version+1, updated_at=now() where tenant_id=? and version_control_id=?",
          CatalogStatus.ROLLED_BACK.name(), request.reason(), tenantId, versionControlId);
      return rollback;
    }

    CatalogStatus next = nextStatus(action, oldStatus);
    if (next == CatalogStatus.APPROVED) {
      String submittedBy = Objects.toString(row.get("submitted_by"), null);
      String createdBy = Objects.toString(row.get("created_by"), null);
      if (actorId != null && (actorId.equals(submittedBy) || actorId.equals(createdBy))) throw new CatalogException("SEPARATION_OF_DUTIES_VIOLATION");
    }
    LocalDate publishStart = null;
    if (next == CatalogStatus.PUBLISHED) {
      publishStart = request.effectiveStart() == null ? localDate((java.sql.Date) row.get("effective_start")) : LocalDate.ofInstant(request.effectiveStart(), ZoneOffset.UTC);
      if (publishStart == null) throw new CatalogException("EFFECTIVE_START_REQUIRED");
      rejectOverlappingPublishedWindow(tenantId, row, publishStart);
    }

    int updated = jdbc.update("""
        update catalog.catalog_version_control
        set status=?, submitted_by=coalesce(?, submitted_by), approved_by=coalesce(?, approved_by), published_by=coalesce(?, published_by),
            rejected_by=coalesce(?, rejected_by), status_reason=?, effective_start=coalesce(?, effective_start), row_version=row_version+1, updated_at=now()
        where tenant_id=? and version_control_id=? and row_version=?
        """, next.name(), next == CatalogStatus.PENDING_APPROVAL ? actorId : null, next == CatalogStatus.APPROVED ? actorId : null,
        next == CatalogStatus.PUBLISHED ? actorId : null, next == CatalogStatus.REJECTED ? actorId : null, request.reason(), publishStart, tenantId, versionControlId, currentRowVersion);
    if (updated == 0) throw new CatalogException("VERSION_CONFLICT");
    return versionActionResponse(tenantId, versionControlId, oldStatus);
  }

  CatalogVersionAsOfResponse resolveVersionAsOf(UUID tenantId, String artifactType, String artifactCode, Instant asOfInstant) {
    LocalDate asOf = LocalDate.ofInstant(asOfInstant == null ? Instant.now() : asOfInstant, ZoneOffset.UTC);
    List<CatalogVersionAsOfResponse> rows = jdbc.query("""
        select artifact_type,artifact_code,version_control_id,status,version_number,config_hash,effective_start,effective_end
        from catalog.catalog_version_control
        where tenant_id=? and artifact_type=? and artifact_code=? and status='PUBLISHED'
          and effective_start <= ? and (effective_end is null or effective_end > ?)
        order by version_number desc limit 1
        """, (rs, row) -> new CatalogVersionAsOfResponse(rs.getString("artifact_type"), rs.getString("artifact_code"), rs.getObject("version_control_id", UUID.class), CatalogStatus.valueOf(rs.getString("status")), rs.getInt("version_number"), rs.getString("config_hash"), instant(rs.getDate("effective_start")), instant(rs.getDate("effective_end"))), tenantId, artifactType, artifactCode, java.sql.Date.valueOf(asOf), java.sql.Date.valueOf(asOf));
    if (rows.isEmpty()) throw new CatalogException("NOT_FOUND");
    return rows.get(0);
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
    return resolveMaterialized(tenantId, request, null).snapshot();
  }

  ProductConfigSnapshotMaterialization resolveMaterialized(UUID tenantId, ResolveCatalogRequest request, String correlationId) {
    Instant asOfInstant = request.effectiveAsOf();
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    CatalogResponse activeCatalog = active(tenantId);
    validateResolveIdentifiers(activeCatalog, request, asOf);
    String channel = request.requestedChannel();
    String family = request.requestedProductFamily();
    List<ProductDefinition> products = activeCatalog.products().stream().filter(p -> request.includeInactiveRequested() || active(p.effectiveFrom(), p.effectiveTo(), asOf)).filter(p -> channel == null || p.allowedChannels().contains(channel)).filter(p -> request.stateCode() == null || p.allowedStates().contains(request.stateCode())).filter(p -> family == null || p.productFamily().equals(family)).toList();
    List<InvestorProgram> investors = activeCatalog.investors().stream().filter(i -> request.includeInactiveRequested() || active(i.effectiveFrom(), i.effectiveTo(), asOf)).filter(i -> channel == null || i.channels().contains(channel)).filter(i -> request.investorCode() == null || i.investorCode().equals(request.investorCode())).filter(i -> products.isEmpty() || products.stream().anyMatch(p -> i.productCodes().contains(p.productCode()))).toList();
    List<ReferenceEntry> refs = activeCatalog.references().stream().filter(r -> request.includeInactiveRequested() || active(r.effectiveFrom(), r.effectiveTo(), asOf)).filter(r -> matchesReferenceRequest(r, request)).toList();
    List<MarketArea> markets = activeCatalog.markets().stream().filter(m -> request.includeInactiveRequested() || active(m.effectiveFrom(), m.effectiveTo(), asOf)).filter(m -> request.stateCode() == null || m.stateCode().equals(request.stateCode())).filter(m -> request.countyFips() == null || Objects.equals(m.countyFips(), request.countyFips())).filter(m -> channel == null || m.allowedChannels().isEmpty() || m.allowedChannels().contains(channel)).toList();
    if (products.isEmpty()) throw new CatalogException("PRODUCT_CONFIG_SNAPSHOT_UNAVAILABLE");
    if (investors.isEmpty()) throw new CatalogException("PRODUCT_CONFIG_SNAPSHOT_UNAVAILABLE");
    if (request.stateCode() != null && markets.isEmpty()) throw new CatalogException("MARKET_NOT_ENABLED");
    SnapshotChannel channelComponent = channel == null ? null : new SnapshotChannel(channel, referenceVersionId(tenantId, "CHANNEL", channel).orElse(null));
    List<SnapshotTaxonomy> taxonomy = refs.stream().filter(r -> "PRODUCT_TAXONOMY".equals(r.catalogType())).map(r -> new SnapshotTaxonomy(r.code(), referenceVersionId(tenantId, r.catalogType(), r.code()).orElse(r.entryId()))).toList();
    List<SnapshotProduct> productComponents = products.stream().map(p -> new SnapshotProduct(p.productCode(), versionIdForCode(tenantId, "PRODUCT", p.productCode()).orElse(p.productId()), investors.stream().filter(i -> i.productCodes().contains(p.productCode())).map(InvestorProgram::investorCode).sorted().toList(), termProfileCodes(refs), pricingConfigurationRefs(tenantId, activeCatalog.catalogId(), p.productCode(), asOf))).toList();
    List<SnapshotInvestor> investorComponents = investors.stream().map(i -> new SnapshotInvestor(i.investorCode(), versionIdForCode(tenantId, "INVESTOR", i.investorCode()).orElse(i.investorId()), false)).toList();
    Map<String, List<String>> referenceVersions = referenceVersions(tenantId, refs, markets);
    String requestHash = hash(json(canonicalRequest(request, tenantId, asOfInstant)));
    Map<String, Object> canonical = new LinkedHashMap<>();
    canonical.put("asOf", asOfInstant.toString());
    canonical.put("channel", channel);
    canonical.put("family", family);
    canonical.put("products", productComponents);
    canonical.put("investors", investorComponents);
    canonical.put("referenceVersions", referenceVersions);
    canonical.put("markets", markets.stream().map(m -> m.stateCode() + ":" + Objects.toString(m.countyFips(), "") + ":" + versionIdForMarket(tenantId, m).map(UUID::toString).orElse(m.marketId().toString())).sorted().toList());
    String snapshotHash = hash(json(canonical));
    List<String> warnings = List.of();
    ProductConfigSnapshot snapshot = new ProductConfigSnapshot(null, tenantId, snapshotHash, asOf, products, investors, refs, markets, asOfInstant, channelComponent, taxonomy, productComponents, investorComponents, referenceVersions, warnings, requestHash, correlationId);
    Map<String, Object> snapshotJson = new LinkedHashMap<>();
    snapshotJson.put("products", products);
    snapshotJson.put("investors", investors);
    snapshotJson.put("references", refs);
    snapshotJson.put("markets", markets);
    snapshotJson.put("asOf", asOfInstant.toString());
    snapshotJson.put("channel", channelComponent);
    snapshotJson.put("taxonomy", taxonomy);
    snapshotJson.put("productComponents", productComponents);
    snapshotJson.put("investorComponents", investorComponents);
    snapshotJson.put("referenceVersions", referenceVersions);
    snapshotJson.put("warnings", warnings);
    snapshotJson.put("requestHash", requestHash);
    snapshotJson.put("correlationId", correlationId);
    boolean materialized = !snapshotHashExists(tenantId, snapshotHash);
    UUID newSnapshotId = UUID.randomUUID();
    UUID snapshotId = jdbc.queryForObject("insert into catalog.product_config_snapshot(snapshot_id,tenant_id,catalog_id,snapshot_hash,request_hash,request_json,snapshot_json,as_of_date,created_at) values (?,?,?,?,?,?::jsonb,?::jsonb,?,now()) on conflict (tenant_id,snapshot_hash) do update set snapshot_hash=excluded.snapshot_hash returning snapshot_id",
        UUID.class, newSnapshotId, tenantId, activeCatalog.catalogId(), snapshotHash, requestHash, json(request), json(snapshotJson), java.sql.Date.valueOf(asOf));
    return new ProductConfigSnapshotMaterialization(new ProductConfigSnapshot(snapshotId, tenantId, snapshotHash, asOf, products, investors, refs, markets, asOfInstant, channelComponent, taxonomy, productComponents, investorComponents, referenceVersions, warnings, requestHash, correlationId), materialized);
  }

  private static void validateResolveIdentifiers(CatalogResponse activeCatalog, ResolveCatalogRequest request, LocalDate asOf) {
    String productFamily = request.requestedProductFamily();
    if (productFamily != null && activeCatalog.products().stream().filter(p -> request.includeInactiveRequested() || active(p.effectiveFrom(), p.effectiveTo(), asOf)).noneMatch(p -> p.productFamily().equals(productFamily))) {
      throw new CatalogException("UNKNOWN_PRODUCT_FAMILY");
    }
    if (request.investorCode() != null && activeCatalog.investors().stream().filter(i -> request.includeInactiveRequested() || active(i.effectiveFrom(), i.effectiveTo(), asOf)).noneMatch(i -> i.investorCode().equals(request.investorCode()))) {
      throw new CatalogException("UNKNOWN_INVESTOR_CODE");
    }
  }

  private boolean snapshotHashExists(UUID tenantId, String snapshotHash) {
    Integer count = jdbc.queryForObject("select count(*) from catalog.product_config_snapshot where tenant_id=? and snapshot_hash=?", Integer.class, tenantId, snapshotHash);
    return count != null && count > 0;
  }

  private static Map<String, Object> canonicalRequest(ResolveCatalogRequest request, UUID tenantId, Instant asOf) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("tenantId", tenantId.toString());
    out.put("asOf", asOf.toString());
    out.put("channelCode", request.requestedChannel());
    out.put("productFamilyCode", request.requestedProductFamily());
    out.put("stateCode", request.stateCode());
    out.put("countyFips", request.countyFips());
    out.put("investorCode", request.investorCode());
    out.put("loanPurpose", request.loanPurpose());
    out.put("propertyType", request.propertyType());
    out.put("occupancyType", request.occupancyType());
    out.put("termMonths", request.termMonths());
    out.put("amortizationType", request.amortizationType());
    out.put("includeInactive", request.includeInactiveRequested());
    return out;
  }

  private Optional<UUID> versionIdForCode(UUID tenantId, String artifactType, String artifactCode) {
    List<UUID> ids = jdbc.query("select version_control_id from catalog.catalog_version_control where tenant_id=? and artifact_type=? and artifact_code=? order by case when status='PUBLISHED' then 0 else 1 end, updated_at desc limit 1",
        (rs, row) -> rs.getObject(1, UUID.class), tenantId, artifactType, artifactCode);
    return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
  }

  private Optional<UUID> versionIdForMarket(UUID tenantId, MarketArea market) {
    String code = market.stateCode() + ":" + Objects.toString(market.countyFips(), "*");
    return referenceVersionId(tenantId, "MARKET", code);
  }

  private static List<String> termProfileCodes(List<ReferenceEntry> refs) {
    return refs.stream().filter(r -> "TERM_AMORTIZATION".equals(r.catalogType())).map(ReferenceEntry::code).sorted().toList();
  }

  private Map<String, List<String>> referenceVersions(UUID tenantId, List<ReferenceEntry> refs, List<MarketArea> markets) {
    Map<String, List<String>> versions = new LinkedHashMap<>();
    versions.put("loanPurposes", refs.stream().filter(r -> "LOAN_PURPOSE".equals(r.catalogType())).map(r -> referenceVersionId(tenantId, r.catalogType(), r.code()).orElse(r.entryId()).toString()).sorted().toList());
    versions.put("propertyTypes", refs.stream().filter(r -> "PROPERTY_TYPE".equals(r.catalogType())).map(r -> referenceVersionId(tenantId, r.catalogType(), r.code()).orElse(r.entryId()).toString()).sorted().toList());
    versions.put("occupancyTypes", refs.stream().filter(r -> "OCCUPANCY_TYPE".equals(r.catalogType())).map(r -> referenceVersionId(tenantId, r.catalogType(), r.code()).orElse(r.entryId()).toString()).sorted().toList());
    versions.put("termAmortization", refs.stream().filter(r -> "TERM_AMORTIZATION".equals(r.catalogType())).map(r -> referenceVersionId(tenantId, r.catalogType(), r.code()).orElse(r.entryId()).toString()).sorted().toList());
    versions.put("productSpecifications", refs.stream().filter(r -> "PRODUCT_SPECIFICATION".equals(r.catalogType())).map(r -> referenceVersionId(tenantId, r.catalogType(), "product-specification").orElse(r.entryId()).toString()).sorted().toList());
    versions.put("markets", markets.stream().map(m -> versionIdForMarket(tenantId, m).orElse(m.marketId()).toString()).sorted().toList());
    return versions;
  }

  List<PricingConfigReference> pricingConfigurationRefs(UUID tenantId, UUID catalogId, String productCode, LocalDate asOf) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select r.attributes::text attributes
        from catalog.reference_entry r
        join catalog.catalog_version_control vc on vc.tenant_id=r.tenant_id and vc.artifact_type='PRODUCT_PRICING_CONFIGURATION' and vc.artifact_id=r.entry_id
        where r.tenant_id=? and r.catalog_id=? and r.catalog_type='PRODUCT_PRICING_CONFIGURATION' and r.code=? and vc.status='PUBLISHED'
          and r.effective_from <= ? and (r.effective_to is null or r.effective_to > ?)
        order by r.effective_from desc, vc.updated_at desc limit 1
        """, tenantId, catalogId, productCode, java.sql.Date.valueOf(asOf), java.sql.Date.valueOf(asOf));
    if (rows.isEmpty()) return List.of();
    Map<String, Object> attributes = map(Objects.toString(rows.get(0).get("attributes"), "{}"));
    return convertList(attributes.get("pricingConfigRefs"), PricingConfigReference.class);
  }

  List<ProductTaxonomyResolvedEntry> resolveProductTaxonomy(UUID tenantId, UUID catalogId, Instant asOfInstant, List<String> codes) {
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    Set<String> requested = new LinkedHashSet<>(codes);
    Map<String, ReferenceEntry> byCode = new LinkedHashMap<>();
    for (ReferenceEntry ref : productTaxonomyEntries(tenantId, catalogId)) {
      if (requested.contains(ref.code()) && active(ref.effectiveFrom(), ref.effectiveTo(), asOf)) byCode.put(ref.code(), ref);
    }
    return requested.stream().filter(byCode::containsKey).map(code -> {
      ReferenceEntry ref = byCode.get(code);
      return new ProductTaxonomyResolvedEntry(ref.code(), Objects.toString(ref.attributes().get("level"), ""), ref.entryId(), 1, CatalogStatus.PUBLISHED, (String) ref.attributes().get("parentCode"));
    }).toList();
  }

  ChannelResolveResponse resolveChannel(UUID tenantId, UUID catalogId, Instant asOfInstant, ChannelResolveRequest request) {
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    List<ReferenceEntry> channels = channelEntries(tenantId, catalogId).stream().filter(ref -> active(ref.effectiveFrom(), ref.effectiveTo(), asOf)).toList();
    Optional<ReferenceEntry> match = Optional.empty();
    if (request.submittedChannel() != null && !request.submittedChannel().isBlank()) {
      match = channels.stream().filter(ref -> ref.code().equals(request.submittedChannel())).findFirst();
    } else if (request.sourceSystem() != null && request.externalValue() != null) {
      match = channels.stream().filter(ref -> hasMapping(ref, request.sourceSystem(), request.externalValue())).findFirst();
    }
    ReferenceEntry ref = match.orElseThrow(() -> new CatalogException("CHANNEL_MAPPING_NOT_FOUND"));
    UUID versionId = referenceVersionId(tenantId, "CHANNEL", ref.code()).orElse(ref.entryId());
    return new ChannelResolveResponse(ref.code(), ref.entryId(), versionId, bool(ref.attributes().get("requiresBranchAssignment")), Objects.toString(ref.attributes().get("defaultMarginGroupCode"), ""));
  }

  TermAmortizationResolveResponse resolveTermAmortization(UUID tenantId, UUID catalogId, Instant asOfInstant, TermAmortizationResolveRequest request) {
    if (request.termMonths() == null || request.amortizationType() == null || request.amortizationType().isBlank()) throw new CatalogException("TERM_AMORTIZATION_RESOLVE_FACTS_REQUIRED");
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    List<Map<String, Object>> rows = termAmortizationRows(tenantId, catalogId, asOf);
    for (Map<String, Object> row : rows) {
      Map<String, Object> attributes = map(Objects.toString(row.get("attributes"), "{}"));
      if (termProfileMatches(attributes, request.termMonths(), request.amortizationType(), request.initialFixedMonths(), request.adjustmentPeriodMonths())) {
        return new TermAmortizationResolveResponse(Objects.toString(row.get("code"), ""), (UUID) row.get("version_control_id"), Objects.toString(attributes.get("armIndexCode"), null), Objects.toString(row.get("config_hash"), ""));
      }
    }
    throw new CatalogException("TERM_AMORTIZATION_NOT_SUPPORTED");
  }

  PropertyOccupancyResolveResponse resolvePropertyOccupancy(UUID tenantId, UUID catalogId, Instant asOfInstant, PropertyOccupancyResolveRequest request) {
    if (request.propertyType() == null || request.propertyType().isBlank() || request.occupancyType() == null || request.occupancyType().isBlank()) throw new CatalogException("PROPERTY_OCCUPANCY_FACTS_REQUIRED");
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    PropertyTypeResolved property = propertyTypeRows(tenantId, catalogId, asOf).stream()
        .filter(row -> Objects.equals(Objects.toString(row.get("code"), ""), request.propertyType()))
        .findFirst()
        .map(this::propertyResolved)
        .orElseThrow(() -> new CatalogException("PROPERTY_OCCUPANCY_NOT_PUBLISHED"));
    OccupancyTypeResolved occupancy = occupancyTypeRows(tenantId, catalogId, asOf).stream()
        .filter(row -> Objects.equals(Objects.toString(row.get("code"), ""), request.occupancyType()))
        .findFirst()
        .map(this::occupancyResolved)
        .orElseThrow(() -> new CatalogException("PROPERTY_OCCUPANCY_NOT_PUBLISHED"));
    return new PropertyOccupancyResolveResponse(property, occupancy);
  }

  PropertyOccupancyListResponse listPublishedPropertyOccupancy(UUID tenantId, UUID catalogId, Instant asOfInstant) {
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    return new PropertyOccupancyListResponse(
        propertyTypeRows(tenantId, catalogId, asOf).stream().map(this::propertyResolved).toList(),
        occupancyTypeRows(tenantId, catalogId, asOf).stream().map(this::occupancyResolved).toList());
  }

  LoanPurposeResolveResponse resolveLoanPurpose(UUID tenantId, UUID catalogId, Instant asOfInstant, LoanPurposeResolveRequest request) {
    if (request.loanPurpose() == null || request.loanPurpose().isBlank()) throw new CatalogException("LOAN_PURPOSE_REQUIRED");
    String submitted = request.loanPurpose().trim().toUpperCase(Locale.ROOT);
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    for (Map<String, Object> row : referenceRows(tenantId, catalogId, "LOAN_PURPOSE", asOf)) {
      Map<String, Object> attributes = map(Objects.toString(row.get("attributes"), "{}"));
      String code = Objects.toString(row.get("code"), "");
      if (code.equalsIgnoreCase(submitted) || aliases(attributes).contains(submitted)) {
        if (!bool(attributes.get("eligibleForConventional"))) throw new CatalogException("LOAN_PURPOSE_NOT_SUPPORTED");
        return new LoanPurposeResolveResponse(code, (UUID) row.get("version_control_id"), bool(attributes.get("isRefinance")), bool(attributes.get("isCashOut")), bool(attributes.get("requiresExistingLien")));
      }
    }
    throw new CatalogException("LOAN_PURPOSE_NOT_SUPPORTED");
  }

  UUID activeCatalogId(UUID tenantId) {
    List<UUID> ids = jdbc.query("select catalog_id from catalog.product_catalog where tenant_id=? and status='PUBLISHED' order by updated_at desc limit 1", (rs, row) -> rs.getObject(1, UUID.class), tenantId);
    if (ids.isEmpty()) throw new CatalogException("NO_PUBLISHED_CATALOG");
    return ids.get(0);
  }

  ProductConfigSnapshot snapshot(UUID tenantId, UUID snapshotId) {
    List<ProductConfigSnapshot> snapshots = jdbc.query("select snapshot_id,tenant_id,snapshot_hash,as_of_date,snapshot_json from catalog.product_config_snapshot where tenant_id=? and snapshot_id=?", (rs, row) -> {
      Map<String, Object> snapshot = map(rs.getString("snapshot_json"));
      LocalDate asOfDate = rs.getDate("as_of_date").toLocalDate();
      Object channelValue = snapshot.get("channel");
      SnapshotChannel channel = channelValue == null ? null : mapper.convertValue(channelValue, SnapshotChannel.class);
      Object asOfValue = snapshot.get("asOf");
      Instant asOf = asOfValue == null ? asOfDate.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.parse(Objects.toString(asOfValue));
      return new ProductConfigSnapshot(rs.getObject("snapshot_id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("snapshot_hash"), asOfDate,
          convertList(snapshot.get("products"), ProductDefinition.class), convertList(snapshot.get("investors"), InvestorProgram.class), convertList(snapshot.get("references"), ReferenceEntry.class), convertList(snapshot.get("markets"), MarketArea.class),
          asOf, channel, convertList(snapshot.get("taxonomy"), SnapshotTaxonomy.class), convertList(snapshot.get("productComponents"), SnapshotProduct.class), convertList(snapshot.get("investorComponents"), SnapshotInvestor.class), referenceVersionMap(snapshot.get("referenceVersions")), stringListValue(snapshot.get("warnings")), Objects.toString(snapshot.get("requestHash"), null), Objects.toString(snapshot.get("correlationId"), null));
    }, tenantId, snapshotId);
    if (snapshots.isEmpty()) throw new CatalogException("SNAPSHOT_NOT_FOUND");
    return snapshots.get(0);
  }

  ProductPricingConfigurationResponse resolveProductPricingConfiguration(UUID tenantId, String productCode, Instant asOfInstant) {
    UUID catalogId = activeCatalogId(tenantId);
    Instant asOf = asOfInstant == null ? Instant.now() : asOfInstant;
    ProductDefinition product = productByCode(tenantId, catalogId, productCode).orElseThrow(() -> new CatalogException("PRODUCT_CODE_UNKNOWN"));
    UUID productVersionId = versionIdForCode(tenantId, "PRODUCT", product.productCode()).orElse(product.productId());
    List<PricingConfigReference> refs = pricingConfigurationRefs(tenantId, catalogId, product.productCode(), LocalDate.ofInstant(asOf, ZoneOffset.UTC));
    if (refs.isEmpty()) throw new CatalogException("PRODUCT_PRICING_CONFIGURATION_NOT_FOUND");
    return new ProductPricingConfigurationResponse(product.productCode(), productVersionId, asOf, null, refs, "catalog-audit:" + catalogId + ":pricing-config:" + product.productCode());
  }

  ConventionalProductResolveResponse resolveConventionalProducts(UUID tenantId, ConventionalProductResolveRequest request) {
    requireScenarioFacts(request);
    Instant asOfInstant = request.asOf() == null ? Instant.now() : request.asOf();
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select d.product_code,v.id product_version_id,v.config_hash,v.display_priority
        from catalog.conventional_product_definition d
        join catalog.conventional_product_version v on v.tenant_id=d.tenant_id and v.product_definition_id=d.id
        where d.tenant_id=? and v.status='PUBLISHED' and v.effective_start <= ? and (v.effective_end is null or v.effective_end > ?)
          and v.amortization_type=? and v.min_loan_amount <= ? and v.max_loan_amount >= ?
        order by v.display_priority,d.product_code
        """, tenantId, Timestamp.from(asOfInstant), Timestamp.from(asOfInstant), request.amortizationType(), request.loanAmount(), request.loanAmount());
    List<ConventionalProductMatch> eligible = new ArrayList<>();
    List<ConventionalProductRejected> rejected = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      UUID versionId = (UUID) row.get("product_version_id");
      String productCode = Objects.toString(row.get("product_code"), "");
      String miss = firstMissingMatch(tenantId, versionId, request);
      if (miss == null) {
        eligible.add(new ConventionalProductMatch(productCode, versionId, allowedCodes(tenantId, versionId, "INVESTOR"), Objects.toString(row.get("config_hash"), "")));
      } else {
        rejected.add(new ConventionalProductRejected(productCode, miss, "Scenario fact is outside this product definition."));
      }
    }
    if (eligible.isEmpty()) throw new CatalogException("NO_ELIGIBLE_CONVENTIONAL_PRODUCT");
    return new ConventionalProductResolveResponse(eligible, rejected);
  }

  InvestorResolveResponse resolveInvestors(UUID tenantId, UUID catalogId, Instant asOfInstant, InvestorResolveRequest request, boolean canViewSecret) {
    if (request.channelCode() == null || request.channelCode().isBlank()) throw new CatalogException("CHANNEL_REQUIRED");
    if (request.deliveryType() == null || request.deliveryType().isBlank()) throw new CatalogException("DELIVERY_TYPE_REQUIRED");
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select e.investor_code,v.id investor_version_id,v.requires_mi_validation,s.seller_id
        from catalog.investor_catalog_entry e
        join catalog.investor_catalog_version v on v.tenant_id=e.tenant_id and v.investor_id=e.id
        join catalog.investor_seller_servicer s on s.tenant_id=v.tenant_id and s.investor_version_id=v.id and s.channel_code=?
        where e.tenant_id=? and e.catalog_id=? and v.status='PUBLISHED' and v.effective_start <= ? and (v.effective_end is null or v.effective_end > ?)
          and jsonb_exists(v.active_channel_codes, ?) and jsonb_exists(v.delivery_types, ?)
        order by e.investor_code
        """, request.channelCode(), tenantId, catalogId, Timestamp.from(asOfInstant), Timestamp.from(asOfInstant), request.channelCode(), request.deliveryType());
    List<ResolvedInvestor> investors = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String investorCode = Objects.toString(row.get("investor_code"), "");
      if (request.productCode() != null && !request.productCode().isBlank() && !productAllowsInvestor(tenantId, request.productCode(), investorCode)) continue;
      investors.add(new ResolvedInvestor(investorCode, (UUID) row.get("investor_version_id"), InvestorCatalogPolicy.maskSellerId(Objects.toString(row.get("seller_id"), ""), canViewSecret), Boolean.TRUE.equals(row.get("requires_mi_validation"))));
    }
    return new InvestorResolveResponse(List.copyOf(investors));
  }

  MarketResolveResponse resolveMarket(UUID tenantId, UUID catalogId, Instant asOfInstant, MarketResolveRequest request) {
    MarketCatalogPolicy.requireResolvable(request);
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    String stateCode = MarketCatalogPolicy.requireStateCode(request.stateCode());
    String countyFips = MarketCatalogPolicy.normalizeCountyFips(stateCode, request.countyFips());
    Optional<Map<String, Object>> county = countyFips == null ? Optional.empty() : marketRows(tenantId, catalogId, asOf).stream()
        .filter(row -> stateCode.equals(Objects.toString(row.get("state_code"), "")))
        .filter(row -> countyFips.equals(Objects.toString(row.get("county_fips"), null)))
        .findFirst();
    Optional<Map<String, Object>> state = marketRows(tenantId, catalogId, asOf).stream()
        .filter(row -> stateCode.equals(Objects.toString(row.get("state_code"), "")))
        .filter(row -> row.get("county_fips") == null)
        .findFirst();
    Map<String, Object> row;
    if (countyFips != null) {
      row = county.orElseThrow(() -> new CatalogException("MARKET_NOT_FOUND"));
    } else {
      row = state.orElseThrow(() -> new CatalogException("MARKET_NOT_FOUND"));
    }
    String status = Objects.toString(row.get("market_status"), "");
    List<String> channels = strings(Objects.toString(row.get("allowed_channels"), "[]"));
    List<String> products = strings(Objects.toString(row.get("allowed_product_codes"), "[]"));
    if ("DISABLED".equals(status)) throw new CatalogException("MARKET_RESTRICTED");
    if ("RESTRICTED".equals(status) && (!containsIfConfigured(products, request.productCode()) || !containsIfConfigured(channels, request.channelCode()))) throw new CatalogException("MARKET_RESTRICTED");
    if ("ENABLED".equals(status) && (!containsIfConfigured(products, request.productCode()) || !containsIfConfigured(channels, request.channelCode()))) throw new CatalogException("MARKET_RESTRICTED");
    return new MarketResolveResponse((UUID) row.get("version_control_id"), stateCode, Objects.toString(row.get("county_fips"), null), status, Objects.toString(row.get("restriction_reason_code"), null));
  }

  void event(CatalogEvent event) {
    UUID catalogId = event.catalogId() == null ? currentCatalogId(event.tenantId()) : event.catalogId();
    jdbc.update("insert into catalog.catalog_outbox_event(tenant_id,event_id,catalog_id,event_type,event_version,payload_json,occurred_at) values (?,?,?,?,?,?::jsonb,?)",
        event.tenantId(), event.eventId(), catalogId, event.eventType(), 1, json(event.payload()), Timestamp.from(event.occurredAt()));
  }

  List<Map<String, Object>> publishedConventionalProductDefinitions(UUID tenantId, UUID catalogId) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select d.id product_definition_id,d.product_code,v.id product_version_id,v.status,v.effective_start,v.effective_end,v.config_hash
        from catalog.conventional_product_definition d
        join catalog.conventional_product_version v on v.tenant_id=d.tenant_id and v.product_definition_id=d.id
        where d.tenant_id=? and d.catalog_id=? and v.status='PUBLISHED'
        order by d.product_code,v.version_number
        """, tenantId, catalogId);
    List<Map<String, Object>> payloads = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      UUID versionId = (UUID) row.get("product_version_id");
      Map<String, Object> effectiveWindow = new LinkedHashMap<>();
      effectiveWindow.put("start", instantString(row.get("effective_start")));
      effectiveWindow.put("end", instantString(row.get("effective_end")));
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("productDefinitionId", Objects.toString(row.get("product_definition_id"), ""));
      payload.put("productVersionId", Objects.toString(versionId, ""));
      payload.put("productCode", Objects.toString(row.get("product_code"), ""));
      payload.put("status", Objects.toString(row.get("status"), ""));
      payload.put("effectiveWindow", effectiveWindow);
      payload.put("referencedVersionIds", referencedVersionIds(tenantId, versionId));
      payload.put("configHash", Objects.toString(row.get("config_hash"), ""));
      payloads.add(payload);
    }
    return List.copyOf(payloads);
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

  private List<ReferenceEntry> productTaxonomyEntries(UUID tenantId, UUID catalogId) {
    return jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_id=? and catalog_type='PRODUCT_TAXONOMY' order by code", (rs, row) -> new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, catalogId);
  }

  private List<ReferenceEntry> channelEntries(UUID tenantId, UUID catalogId) {
    return jdbc.query("select * from catalog.reference_entry where tenant_id=? and catalog_id=? and catalog_type='CHANNEL' order by code", (rs, row) -> new ReferenceEntry(rs.getObject("entry_id", UUID.class), rs.getString("catalog_type"), rs.getString("code"), rs.getString("label"), rs.getString("category"), map(rs.getString("attributes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, catalogId);
  }

  private boolean hasInvestorCatalogEntries(UUID tenantId, UUID catalogId) {
    Integer count = jdbc.queryForObject("select count(*) from catalog.investor_catalog_entry where tenant_id=? and catalog_id=?", Integer.class, tenantId, catalogId);
    return count != null && count > 0;
  }

  private boolean productAllowsInvestor(UUID tenantId, String productCode, String investorCode) {
    Integer productCount = jdbc.queryForObject("select count(*) from catalog.conventional_product_definition where tenant_id=? and product_code=?", Integer.class, tenantId, productCode);
    if (productCount == null || productCount == 0) return true;
    Integer count = jdbc.queryForObject("""
        select count(*)
        from catalog.conventional_product_definition d
        join catalog.conventional_product_version v on v.tenant_id=d.tenant_id and v.product_definition_id=d.id
        join catalog.conventional_product_allowed_value a on a.tenant_id=v.tenant_id and a.product_version_id=v.id
        where d.tenant_id=? and d.product_code=? and v.status='PUBLISHED' and a.value_type='INVESTOR' and a.value_code=?
        """, Integer.class, tenantId, productCode, investorCode);
    return count != null && count > 0;
  }

  private static Map<String, Object> sanitizedInvestorSnapshot(InvestorCatalogDraftRequest request) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("investorCode", request.investorCode());
    snapshot.put("legalName", request.legalName());
    snapshot.put("investorType", request.investorType());
    snapshot.put("agency", request.agency());
    snapshot.put("deliveryTypes", safe(request.deliveryTypes()));
    snapshot.put("activeChannelCodes", safe(request.activeChannelCodes()));
    snapshot.put("requiresMiValidation", Boolean.TRUE.equals(request.requiresMiValidation()));
    snapshot.put("status", request.status() == null ? CatalogStatus.DRAFT : request.status());
    snapshot.put("effectiveStart", request.effectiveStart());
    snapshot.put("effectiveEnd", request.effectiveEnd());
    List<InvestorSellerServicerId> sellerServicerIds = request.sellerServicerIds() == null ? List.of() : request.sellerServicerIds();
    snapshot.put("sellerServicerIds", sellerServicerIds.stream().map(seller -> Map.of("channelCode", seller.channelCode(), "sellerIdMasked", InvestorCatalogPolicy.maskSellerId(seller.sellerId(), false), "servicerIdMasked", InvestorCatalogPolicy.maskSellerId(seller.servicerId(), false))).toList());
    return snapshot;
  }

  private List<MarketArea> markets(UUID tenantId) {
    return jdbc.query("select * from catalog.market_area where tenant_id=? order by state_code,county_fips", (rs, row) -> new MarketArea(rs.getObject("market_id", UUID.class), rs.getString("state_code"), rs.getString("state_name"), rs.getString("county_fips"), rs.getString("county_name"), rs.getString("market_status"), rs.getString("restriction_reason_code"), strings(rs.getString("allowed_channels")), strings(rs.getString("allowed_product_codes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId);
  }

  private List<MarketArea> markets(UUID tenantId, UUID catalogId) {
    return jdbc.query("select * from catalog.market_area where tenant_id=? and catalog_id=? order by state_code,county_fips", (rs, row) -> new MarketArea(rs.getObject("market_id", UUID.class), rs.getString("state_code"), rs.getString("state_name"), rs.getString("county_fips"), rs.getString("county_name"), rs.getString("market_status"), rs.getString("restriction_reason_code"), strings(rs.getString("allowed_channels")), strings(rs.getString("allowed_product_codes")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, catalogId);
  }

  private static boolean matchesReferenceRequest(ReferenceEntry ref, ResolveCatalogRequest request) {
    if ("PRODUCT_PRICING_CONFIGURATION".equals(ref.catalogType())) return false;
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

  private UUID versionControl(UUID tenantId, UUID catalogId, String artifactType, UUID artifactId, String artifactCode, CatalogStatus status, Object snapshot, String actorId) {
    return versionControl(tenantId, catalogId, artifactType, artifactId, artifactCode, status, snapshot, actorId, 1);
  }

  private UUID versionControl(UUID tenantId, UUID catalogId, String artifactType, UUID artifactId, String artifactCode, CatalogStatus status, Object snapshot, String actorId, int versionNumber) {
    String configHash = hash(json(snapshot));
    UUID versionControlId = UUID.randomUUID();
    jdbc.update("insert into catalog.catalog_version_control(tenant_id,version_control_id,catalog_id,artifact_type,artifact_id,artifact_code,version_number,status,effective_start,effective_end,config_hash,snapshot_json,created_by) values (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)",
        tenantId, versionControlId, catalogId, artifactType, artifactId, artifactCode, versionNumber, status.name(), extractEffectiveStart(snapshot), extractEffectiveEnd(snapshot), configHash, json(snapshot), createdBy(actorId));
    return versionControlId;
  }

  private Optional<UUID> productSpecificationEntryId(UUID tenantId) {
    List<UUID> ids = jdbc.query("select entry_id from catalog.reference_entry where tenant_id=? and catalog_type='PRODUCT_SPECIFICATION' and code='product-specification:published' limit 1",
        (rs, row) -> rs.getObject(1, UUID.class), tenantId);
    return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
  }

  private static String createdBy(String actorId) {
    return actorId == null || actorId.isBlank() ? "catalog-service" : actorId;
  }

  private Map<String, Object> versionControlRow(UUID tenantId, String artifactType, UUID artifactId, UUID versionId) {
    if (versionId != null) {
      List<Map<String, Object>> rows = jdbc.queryForList("select * from catalog.catalog_version_control where tenant_id=? and artifact_type=? and version_control_id=?", tenantId, artifactType, versionId);
      if (rows.isEmpty()) throw new CatalogException("NOT_FOUND");
      return rows.get(0);
    }
    List<Map<String, Object>> rows = jdbc.queryForList("""
        select * from catalog.catalog_version_control
        where tenant_id=? and artifact_type=? and artifact_id=?
        order by version_number desc limit 1
        """, tenantId, artifactType, artifactId);
    if (rows.isEmpty()) throw new CatalogException("NOT_FOUND");
    return rows.get(0);
  }

  private CatalogVersionActionResponse versionActionResponse(UUID tenantId, UUID versionControlId, CatalogStatus oldStatus) {
    Map<String, Object> row = jdbc.queryForMap("select artifact_type,artifact_id,version_control_id,status,version_number,config_hash,row_version from catalog.catalog_version_control where tenant_id=? and version_control_id=?", tenantId, versionControlId);
    return new CatalogVersionActionResponse(Objects.toString(row.get("artifact_type"), ""), (UUID) row.get("artifact_id"), (UUID) row.get("version_control_id"), oldStatus, CatalogStatus.valueOf(Objects.toString(row.get("status"), "")), ((Number) row.get("version_number")).intValue(), Objects.toString(row.get("config_hash"), ""), ((Number) row.get("row_version")).longValue());
  }

  private CatalogVersionActionResponse createRollbackDraft(UUID tenantId, Map<String, Object> source, String actorId, String reason) {
    CatalogStatus oldStatus = CatalogStatus.valueOf(Objects.toString(source.get("status"), ""));
    if (oldStatus != CatalogStatus.PUBLISHED) throw new CatalogException("INVALID_STATUS_TRANSITION");
    UUID versionControlId = UUID.randomUUID();
    String artifactType = Objects.toString(source.get("artifact_type"), "");
    UUID artifactId = (UUID) source.get("artifact_id");
    int versionNumber = nextVersionNumber(tenantId, artifactType, artifactId);
    jdbc.update("""
        insert into catalog.catalog_version_control(tenant_id,version_control_id,catalog_id,artifact_type,artifact_id,artifact_code,version_number,status,effective_start,effective_end,config_hash,snapshot_json,created_by,status_reason)
        values (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
        """, tenantId, versionControlId, source.get("catalog_id"), artifactType, artifactId, source.get("artifact_code"), versionNumber, CatalogStatus.DRAFT.name(),
        source.get("effective_start"), source.get("effective_end"), source.get("config_hash"), Objects.toString(source.get("snapshot_json"), "{}"), actorId, reason == null ? "rollback" : reason);
    return versionActionResponse(tenantId, versionControlId, oldStatus);
  }

  private int nextVersionNumber(UUID tenantId, String artifactType, UUID artifactId) {
    Integer max = jdbc.queryForObject("select coalesce(max(version_number),0) from catalog.catalog_version_control where tenant_id=? and artifact_type=? and artifact_id=?", Integer.class, tenantId, artifactType, artifactId);
    return (max == null ? 0 : max) + 1;
  }

  private CatalogStatus nextStatus(String action, CatalogStatus oldStatus) {
    return switch (action) {
      case "VALIDATE" -> requireTransition(oldStatus, CatalogStatus.DRAFT, CatalogStatus.VALIDATED);
      case "SUBMIT_APPROVAL" -> requireTransition(oldStatus, CatalogStatus.VALIDATED, CatalogStatus.PENDING_APPROVAL);
      case "APPROVE" -> requireTransition(oldStatus, CatalogStatus.PENDING_APPROVAL, CatalogStatus.APPROVED);
      case "REJECT" -> (oldStatus == CatalogStatus.DRAFT || oldStatus == CatalogStatus.PENDING_APPROVAL) ? CatalogStatus.REJECTED : invalidTransition();
      case "PUBLISH" -> requireTransition(oldStatus, CatalogStatus.APPROVED, CatalogStatus.PUBLISHED);
      case "SUSPEND" -> requireTransition(oldStatus, CatalogStatus.PUBLISHED, CatalogStatus.SUSPENDED);
      case "RETIRE" -> (oldStatus == CatalogStatus.PUBLISHED || oldStatus == CatalogStatus.SUSPENDED) ? CatalogStatus.RETIRED : invalidTransition();
      default -> throw new CatalogException("INVALID_VERSION_ACTION");
    };
  }

  private CatalogStatus requireTransition(CatalogStatus actual, CatalogStatus expected, CatalogStatus next) {
    if (actual != expected) throw new CatalogException("INVALID_STATUS_TRANSITION");
    return next;
  }

  private CatalogStatus invalidTransition() { throw new CatalogException("INVALID_STATUS_TRANSITION"); }

  private void rejectOverlappingPublishedWindow(UUID tenantId, Map<String, Object> row, LocalDate publishStart) {
    Integer count = jdbc.queryForObject("""
        select count(*) from catalog.catalog_version_control
        where tenant_id=? and artifact_type=? and artifact_code=? and status='PUBLISHED' and version_control_id<>?
          and (effective_end is null or effective_end > ?)
        """, Integer.class, tenantId, row.get("artifact_type"), row.get("artifact_code"), row.get("version_control_id"), java.sql.Date.valueOf(publishStart));
    if (count != null && count > 0) throw new CatalogException("EFFECTIVE_WINDOW_OVERLAP");
  }

  private static java.sql.Date extractEffectiveStart(Object snapshot) {
    if (snapshot instanceof ProductCreationSnapshot req) return date(req.effectiveStart());
    if (snapshot instanceof ProductTaxonomyDraftRequest req) return date(req.effectiveStart());
    if (snapshot instanceof ChannelTaxonomyDraftRequest req) return date(req.effectiveStart());
    if (snapshot instanceof ConventionalProductDraftRequest req) return date(req.effectiveStart());
    if (snapshot instanceof InvestorCatalogDraftRequest req) return date(req.effectiveStart());
    if (snapshot instanceof ReferenceEntry ref) return ref.effectiveFrom() == null ? null : java.sql.Date.valueOf(ref.effectiveFrom());
    if (snapshot instanceof ProductDefinition product) return product.effectiveFrom() == null ? null : java.sql.Date.valueOf(product.effectiveFrom());
    if (snapshot instanceof InvestorProgram investor) return investor.effectiveFrom() == null ? null : java.sql.Date.valueOf(investor.effectiveFrom());
    if (snapshot instanceof MarketArea market) return market.effectiveFrom() == null ? null : java.sql.Date.valueOf(market.effectiveFrom());
    return null;
  }

  private static java.sql.Date extractEffectiveEnd(Object snapshot) {
    if (snapshot instanceof ProductCreationSnapshot req) return date(req.effectiveEnd());
    if (snapshot instanceof ProductTaxonomyDraftRequest req) return date(req.effectiveEnd());
    if (snapshot instanceof ChannelTaxonomyDraftRequest req) return date(req.effectiveEnd());
    if (snapshot instanceof ConventionalProductDraftRequest req) return date(req.effectiveEnd());
    if (snapshot instanceof InvestorCatalogDraftRequest req) return date(req.effectiveEnd());
    if (snapshot instanceof ReferenceEntry ref) return ref.effectiveTo() == null ? null : java.sql.Date.valueOf(ref.effectiveTo());
    if (snapshot instanceof ProductDefinition product) return product.effectiveTo() == null ? null : java.sql.Date.valueOf(product.effectiveTo());
    if (snapshot instanceof InvestorProgram investor) return investor.effectiveTo() == null ? null : java.sql.Date.valueOf(investor.effectiveTo());
    if (snapshot instanceof MarketArea market) return market.effectiveTo() == null ? null : java.sql.Date.valueOf(market.effectiveTo());
    return null;
  }

  private static java.sql.Date date(Instant value) { return value == null ? null : java.sql.Date.valueOf(LocalDate.ofInstant(value, ZoneOffset.UTC)); }

  private static Instant instant(java.sql.Date value) { return value == null ? null : value.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC); }

  private void validateTaxonomyRequest(UUID tenantId, UUID catalogId, ProductTaxonomyDraftRequest request) {
    ProductTaxonomyPolicy.validateDraft(request, existsReference(tenantId, "PRODUCT_TAXONOMY", request.code()), parentCode -> productTaxonomyEntries(tenantId, catalogId).stream()
        .filter(r -> r.code().equals(parentCode))
        .anyMatch(r -> "FAMILY".equals(Objects.toString(r.attributes().get("level"), ""))));
  }

  private void validateConventionalProductStructure(ConventionalProductDraftRequest request) {
    ConventionalProductDefinitionPolicy.validateStructure(request);
  }

  private List<ProductTaxonomyValidationMessage> referenceValidationErrors(UUID tenantId, ConventionalProductDraftRequest request) {
    List<ProductTaxonomyValidationMessage> errors = new ArrayList<>();
    requireRefs(tenantId, errors, "taxonomyTypeCode", "PRODUCT_TAXONOMY", List.of(request.taxonomyTypeCode()));
    requireRefs(tenantId, errors, "investorCodes", "INVESTOR", safe(request.investorCodes()));
    requireRefs(tenantId, errors, "channelCodes", "CHANNEL", safe(request.channelCodes()));
    requireTermAmortizationRefs(tenantId, errors, request);
    if ("ARM".equals(request.amortizationType())) requireRefs(tenantId, errors, "armIndexCode", "ARM_INDEX", List.of(request.armIndexCode()));
    requireRefs(tenantId, errors, "allowedPropertyTypes", "PROPERTY_TYPE", safe(request.allowedPropertyTypes()));
    requireRefs(tenantId, errors, "allowedOccupancyTypes", "OCCUPANCY_TYPE", safe(request.allowedOccupancyTypes()));
    requireRefs(tenantId, errors, "allowedLoanPurposes", "LOAN_PURPOSE", safe(request.allowedLoanPurposes()));
    requireRefs(tenantId, errors, "allowedStateCodes", "MARKET", safe(request.allowedStateCodes()));
    return List.copyOf(errors);
  }

  private void requireTermAmortizationRefs(UUID tenantId, List<ProductTaxonomyValidationMessage> errors, ConventionalProductDraftRequest request) {
    List<Integer> terms = safeInts(request.termMonths());
    if (terms.isEmpty()) {
      errors.add(new ProductTaxonomyValidationMessage("termMonths", "REFERENCE_REQUIRED", "termMonths requires at least one configured catalog reference."));
      return;
    }
    for (Integer term : terms) {
      if (termAmortizationVersionId(tenantId, term, request.amortizationType(), request.fixedPeriodMonths(), request.adjustmentPeriodMonths()).isEmpty()) {
        errors.add(new ProductTaxonomyValidationMessage("termMonths", "REFERENCE_NOT_PUBLISHED", "TERM_AMORTIZATION reference is not configured for term " + term));
      }
    }
  }

  private void requireRefs(UUID tenantId, List<ProductTaxonomyValidationMessage> errors, String field, String artifactType, List<String> codes) {
    if (codes.isEmpty()) {
      errors.add(new ProductTaxonomyValidationMessage(field, "REFERENCE_REQUIRED", field + " requires at least one configured catalog reference."));
      return;
    }
    for (String code : codes) if (referenceVersionId(tenantId, artifactType, code).isEmpty()) errors.add(new ProductTaxonomyValidationMessage(field, "REFERENCE_NOT_PUBLISHED", artifactType + " reference is not configured for code " + code));
  }

  private void insertAllowedValues(UUID tenantId, UUID versionId, ConventionalProductDraftRequest request) {
    insertAllowedValues(tenantId, versionId, "INVESTOR", safe(request.investorCodes()));
    insertAllowedValues(tenantId, versionId, "CHANNEL", safe(request.channelCodes()));
    insertTermAllowedValues(tenantId, versionId, request);
    insertAllowedValues(tenantId, versionId, "PROPERTY_TYPE", safe(request.allowedPropertyTypes()));
    insertAllowedValues(tenantId, versionId, "OCCUPANCY", safe(request.allowedOccupancyTypes()));
    insertAllowedValues(tenantId, versionId, "LOAN_PURPOSE", safe(request.allowedLoanPurposes()));
    insertAllowedValues(tenantId, versionId, "STATE", safe(request.allowedStateCodes()));
  }

  private void insertTermAllowedValues(UUID tenantId, UUID versionId, ConventionalProductDraftRequest request) {
    for (Integer term : safeInts(request.termMonths())) {
      UUID ref = termAmortizationVersionId(tenantId, term, request.amortizationType(), request.fixedPeriodMonths(), request.adjustmentPeriodMonths()).orElse(versionId);
      jdbc.update("insert into catalog.conventional_product_allowed_value(tenant_id,id,product_version_id,value_type,value_code,referenced_version_id) values (?,?,?,?,?,?) on conflict do nothing",
          tenantId, UUID.randomUUID(), versionId, "TERM", term.toString(), ref);
    }
  }

  private void insertAllowedValues(UUID tenantId, UUID versionId, String valueType, List<String> codes) {
    for (String code : codes) {
      UUID ref = referenceVersionId(tenantId, artifactTypeForAllowedValue(valueType), code).orElse(versionId);
      jdbc.update("insert into catalog.conventional_product_allowed_value(tenant_id,id,product_version_id,value_type,value_code,referenced_version_id) values (?,?,?,?,?,?) on conflict do nothing",
          tenantId, UUID.randomUUID(), versionId, valueType, code, ref);
    }
  }

  private Optional<UUID> referenceVersionId(UUID tenantId, String artifactType, String code) {
    String pattern = "MARKET".equals(artifactType) ? code + ":%" : code;
    List<UUID> ids = jdbc.query("select version_control_id from catalog.catalog_version_control where tenant_id=? and artifact_type=? and artifact_code like ? order by case when status='PUBLISHED' then 0 else 1 end, updated_at desc limit 1",
        (rs, row) -> rs.getObject(1, UUID.class), tenantId, artifactType, pattern);
    return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
  }

  private Optional<UUID> termAmortizationVersionId(UUID tenantId, Integer termMonths, String amortizationType, Integer initialFixedMonths, Integer adjustmentPeriodMonths) {
    UUID catalogId = currentCatalogId(tenantId);
    LocalDate asOf = LocalDate.now();
    for (Map<String, Object> row : termAmortizationRows(tenantId, catalogId, asOf, true)) {
      Map<String, Object> attributes = map(Objects.toString(row.get("attributes"), "{}"));
      if (termProfileMatches(attributes, termMonths, amortizationType, initialFixedMonths, adjustmentPeriodMonths)) return Optional.of((UUID) row.get("version_control_id"));
    }
    return Optional.empty();
  }

  private List<Map<String, Object>> termAmortizationRows(UUID tenantId, UUID catalogId, LocalDate asOf) {
    return termAmortizationRows(tenantId, catalogId, asOf, false);
  }

  private List<Map<String, Object>> termAmortizationRows(UUID tenantId, UUID catalogId, LocalDate asOf, boolean includeDraftVersions) {
    String statusClause = includeDraftVersions ? "" : " and vc.status='PUBLISHED'";
    return jdbc.queryForList("""
        select r.code,r.attributes::text attributes,vc.version_control_id,vc.config_hash
        from catalog.reference_entry r
        join catalog.catalog_version_control vc on vc.tenant_id=r.tenant_id and vc.artifact_type='TERM_AMORTIZATION' and vc.artifact_id=r.entry_id
        where r.tenant_id=? and r.catalog_id=? and r.catalog_type='TERM_AMORTIZATION'
        """ + statusClause + """
          and r.effective_from <= ? and (r.effective_to is null or r.effective_to > ?)
        order by r.code
        """, tenantId, catalogId, java.sql.Date.valueOf(asOf), java.sql.Date.valueOf(asOf));
  }

  private List<Map<String, Object>> propertyTypeRows(UUID tenantId, UUID catalogId, LocalDate asOf) {
    return referenceRows(tenantId, catalogId, "PROPERTY_TYPE", asOf);
  }

  private List<Map<String, Object>> occupancyTypeRows(UUID tenantId, UUID catalogId, LocalDate asOf) {
    return referenceRows(tenantId, catalogId, "OCCUPANCY_TYPE", asOf);
  }

  private List<Map<String, Object>> referenceRows(UUID tenantId, UUID catalogId, String artifactType, LocalDate asOf) {
    return jdbc.queryForList("""
        select r.code,r.attributes::text attributes,vc.version_control_id
        from catalog.reference_entry r
        join catalog.catalog_version_control vc on vc.tenant_id=r.tenant_id and vc.artifact_type=? and vc.artifact_id=r.entry_id
        where r.tenant_id=? and r.catalog_id=? and r.catalog_type=? and vc.status='PUBLISHED'
          and r.effective_from <= ? and (r.effective_to is null or r.effective_to > ?)
        order by r.code
        """, artifactType, tenantId, catalogId, artifactType, java.sql.Date.valueOf(asOf), java.sql.Date.valueOf(asOf));
  }

  private EnumerationTypeResponse enumerationFrom(ReferenceEntry ref) {
    List<EnumerationVariantResponse> variants = convertList(ref.attributes().get("variants"), EnumerationVariantResponse.class);
    String source = Objects.toString(ref.attributes().get("source"), "LoanPass field library");
    String overrideScope = Objects.toString(ref.attributes().get("overrideScope"), "system/default");
    return new EnumerationTypeResponse(ref.code(), ref.label(), variants, source, overrideScope);
  }

  private FieldMetadataResponse fieldMetadataFrom(ReferenceEntry ref) {
    Map<String, Object> attributes = ref.attributes();
    return new FieldMetadataResponse(ref.code(), Objects.toString(attributes.get("oldId"), null), ref.label(), Objects.toString(attributes.get("description"), null), ref.category(),
        Objects.toString(attributes.get("valueType"), "string"), Objects.toString(attributes.get("sourceGroup"), "rawFields"), conditionMap(attributes.get("conditions")),
        Objects.toString(attributes.get("disposition"), "native"), Objects.toString(attributes.get("source"), "ReferenceFormfields.json"));
  }

  private ProductSpecificationFieldOrderDraft productSpecFieldOrderFrom(ReferenceEntry ref) {
    Map<String, Object> attributes = ref.attributes();
    String savedAt = Objects.toString(attributes.get("savedAt"), Instant.now().toString());
    return new ProductSpecificationFieldOrderDraft(Objects.toString(attributes.get("draftStatus"), "DRAFT"),
        stringListValue(attributes.get("fieldIds")), Instant.parse(savedAt), Objects.toString(attributes.get("actorId"), null));
  }

  private ProductSpecificationTenantFieldDraft productSpecTenantFieldDraftFrom(ReferenceEntry ref) {
    Map<String, Object> attributes = ref.attributes();
    String savedAt = Objects.toString(attributes.get("savedAt"), Instant.now().toString());
    return new ProductSpecificationTenantFieldDraft(Objects.toString(attributes.get("draftStatus"), "DRAFT"),
        convertList(attributes.get("aliases"), ProductSpecificationFieldAliasEdit.class),
        convertList(attributes.get("nativeFields"), ProductSpecificationNativeFieldEdit.class),
        Instant.parse(savedAt), Objects.toString(attributes.get("actorId"), null));
  }

  private ProductSpecificationConditionDraft productSpecConditionDraftFrom(ReferenceEntry ref) {
    Map<String, Object> attributes = ref.attributes();
    String savedAt = Objects.toString(attributes.get("savedAt"), Instant.now().toString());
    return new ProductSpecificationConditionDraft(Objects.toString(attributes.get("draftStatus"), "DRAFT"),
        convertList(attributes.get("includeConditions"), ProductSpecificationConditionRuleEdit.class),
        convertList(attributes.get("additionalConditions"), ProductSpecificationConditionRuleEdit.class),
        Instant.parse(savedAt), Objects.toString(attributes.get("actorId"), null));
  }

  private Map<String, Object> conditionMap(Object value) {
    if (value == null) return Map.of();
    return mapper.convertValue(value, MAP_TYPE);
  }

  private List<Map<String, Object>> marketRows(UUID tenantId, UUID catalogId, LocalDate asOf) {
    return jdbc.queryForList("""
        select m.state_code,m.county_fips,m.market_status,m.restriction_reason_code,m.allowed_channels::text allowed_channels,
               m.allowed_product_codes::text allowed_product_codes,vc.version_control_id
        from catalog.market_area m
        join catalog.catalog_version_control vc on vc.tenant_id=m.tenant_id and vc.artifact_type='MARKET' and vc.artifact_id=m.market_id
        where m.tenant_id=? and m.catalog_id=? and vc.status='PUBLISHED'
          and m.effective_from <= ? and (m.effective_to is null or m.effective_to > ?)
        order by m.state_code,m.county_fips nulls first
        """, tenantId, catalogId, java.sql.Date.valueOf(asOf), java.sql.Date.valueOf(asOf));
  }

  private PropertyTypeResolved propertyResolved(Map<String, Object> row) {
    Map<String, Object> attributes = map(Objects.toString(row.get("attributes"), "{}"));
    return new PropertyTypeResolved(Objects.toString(row.get("code"), ""), (UUID) row.get("version_control_id"), bool(attributes.get("requiresProjectReview")));
  }

  private OccupancyTypeResolved occupancyResolved(Map<String, Object> row) {
    return new OccupancyTypeResolved(Objects.toString(row.get("code"), ""), (UUID) row.get("version_control_id"));
  }

  private static Set<String> aliases(Map<String, Object> attributes) {
    Set<String> values = new LinkedHashSet<>();
    Object aliases = attributes.get("agencyAliases");
    if (aliases instanceof List<?> list) for (Object alias : list) if (alias != null) values.add(alias.toString().trim().toUpperCase(Locale.ROOT));
    return values;
  }

  private static boolean termProfileMatches(Map<String, Object> attributes, Integer termMonths, String amortizationType, Integer initialFixedMonths, Integer adjustmentPeriodMonths) {
    if (!Objects.equals(intAttr(attributes, "termMonths"), termMonths)) return false;
    if (!Objects.equals(Objects.toString(attributes.get("amortizationType"), ""), amortizationType)) return false;
    if ("ARM".equals(amortizationType)) return Objects.equals(intAttr(attributes, "initialFixedMonths"), initialFixedMonths) && Objects.equals(intAttr(attributes, "adjustmentPeriodMonths"), adjustmentPeriodMonths);
    return true;
  }

  private static Integer intAttr(Map<String, Object> attributes, String key) {
    Object value = attributes.get(key);
    if (value == null) return null;
    if (value instanceof Number number) return number.intValue();
    return Integer.valueOf(Objects.toString(value));
  }

  private boolean channelMappingExists(UUID tenantId, UUID catalogId, String sourceSystem, String externalValue) {
    return channelEntries(tenantId, catalogId).stream().anyMatch(ref -> hasMapping(ref, sourceSystem, externalValue));
  }

  private static boolean hasMapping(ReferenceEntry ref, String sourceSystem, String externalValue) {
    Object mappings = ref.attributes().get("sourceSystemMappings");
    if (!(mappings instanceof List<?> list)) return false;
    for (Object item : list) {
      if (item instanceof Map<?, ?> map && Objects.equals(Objects.toString(map.get("sourceSystem"), null), sourceSystem) && Objects.equals(Objects.toString(map.get("externalValue"), null), externalValue)) return true;
      if (item instanceof ChannelSourceSystemMapping mapping && Objects.equals(mapping.sourceSystem(), sourceSystem) && Objects.equals(mapping.externalValue(), externalValue)) return true;
    }
    return false;
  }

  private static boolean bool(Object value) {
    return value instanceof Boolean b ? b : Boolean.parseBoolean(Objects.toString(value, "false"));
  }

  private static boolean containsIfConfigured(List<String> configured, String requested) {
    if (configured == null || configured.isEmpty()) return true;
    if (requested == null || requested.isBlank()) return false;
    return configured.stream().anyMatch(value -> value.equalsIgnoreCase(requested.trim()));
  }

  private static String artifactTypeForAllowedValue(String valueType) {
    return switch (valueType) {
      case "TERM" -> "TERM_AMORTIZATION";
      case "OCCUPANCY" -> "OCCUPANCY_TYPE";
      case "STATE" -> "MARKET";
      default -> valueType;
    };
  }

  private static void requireScenarioFacts(ConventionalProductResolveRequest request) {
    if (request == null || request.channelCode() == null || request.loanPurposeCode() == null || request.propertyTypeCode() == null || request.occupancyTypeCode() == null || request.stateCode() == null || request.loanAmount() == null || request.termMonths() == null || request.amortizationType() == null) throw new CatalogException("MISSING_SCENARIO_FACTS");
  }

  private String firstMissingMatch(UUID tenantId, UUID versionId, ConventionalProductResolveRequest request) {
    if (!allowedValueContains(tenantId, versionId, "CHANNEL", request.channelCode())) return "CHANNEL_NOT_ALLOWED";
    if (!allowedValueContains(tenantId, versionId, "LOAN_PURPOSE", request.loanPurposeCode())) return "LOAN_PURPOSE_NOT_ALLOWED";
    if (!allowedValueContains(tenantId, versionId, "PROPERTY_TYPE", request.propertyTypeCode())) return "PROPERTY_TYPE_NOT_ALLOWED";
    if (!allowedValueContains(tenantId, versionId, "OCCUPANCY", request.occupancyTypeCode())) return "OCCUPANCY_NOT_ALLOWED";
    if (!allowedValueContains(tenantId, versionId, "STATE", request.stateCode())) return "MARKET_NOT_ALLOWED";
    if (!allowedValueContains(tenantId, versionId, "TERM", request.termMonths().toString())) return "TERM_NOT_ALLOWED";
    return null;
  }

  private boolean allowedValueContains(UUID tenantId, UUID versionId, String valueType, String code) {
    Integer count = jdbc.queryForObject("select count(*) from catalog.conventional_product_allowed_value where tenant_id=? and product_version_id=? and value_type=? and value_code=?", Integer.class, tenantId, versionId, valueType, code);
    return count != null && count > 0;
  }

  private List<String> allowedCodes(UUID tenantId, UUID versionId, String valueType) {
    return jdbc.query("select value_code from catalog.conventional_product_allowed_value where tenant_id=? and product_version_id=? and value_type=? order by value_code", (rs, row) -> rs.getString(1), tenantId, versionId, valueType);
  }

  private List<String> referencedVersionIds(UUID tenantId, UUID versionId) {
    return jdbc.query("select distinct referenced_version_id from catalog.conventional_product_allowed_value where tenant_id=? and product_version_id=? order by referenced_version_id", (rs, row) -> rs.getObject(1, UUID.class).toString(), tenantId, versionId);
  }

  private static String instantString(Object value) {
    if (value == null) return null;
    if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
    if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
    return value.toString();
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
  private static List<PricingConfigReference> pricingRefs(List<PricingConfigReference> values) {
    if (values == null || values.isEmpty()) throw new CatalogException("PRICING_CONFIG_REFS_REQUIRED");
    return List.copyOf(values);
  }
  private static List<Integer> safeInts(List<Integer> values) { return values == null ? List.of() : List.copyOf(values); }
  private static String required(String value, String code) { if (value == null || value.isBlank()) throw new CatalogException(code); return value; }
  private static LocalDate requiredDate(LocalDate value) { if (value == null) throw new CatalogException("EFFECTIVE_FROM_REQUIRED"); return value; }
  private static Instant requiredInstant(Instant value) { if (value == null) throw new CatalogException("EFFECTIVE_START_REQUIRED"); return value; }
  private static java.sql.Date date(LocalDate value) { return value == null ? null : java.sql.Date.valueOf(value); }
  private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
  private static LocalDate localDate(java.sql.Date value) { return value == null ? null : value.toLocalDate(); }

  JdbcTemplate getJdbcTemplate() { return jdbc; }

  private Optional<ProductDefinition> productByCode(UUID tenantId, UUID catalogId, String productCode) {
    List<ProductDefinition> rows = jdbc.query("select * from catalog.product_definition where tenant_id=? and catalog_id=? and product_code=? order by effective_from desc limit 1",
        (rs, row) -> new ProductDefinition(rs.getObject("product_id", UUID.class), rs.getString("product_code"), rs.getString("product_name"), rs.getString("product_family"), strings(rs.getString("allowed_channels")), strings(rs.getString("allowed_states")), rs.getDate("effective_from").toLocalDate(), localDate(rs.getDate("effective_to"))), tenantId, catalogId, productCode);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  void validatePricingConfigRefs(UUID tenantId, Instant asOfInstant, List<PricingConfigReference> refs) {
    LocalDate asOf = LocalDate.ofInstant(asOfInstant, ZoneOffset.UTC);
    for (PricingConfigReference ref : refs) {
      if (ref == null || ref.refType() == null || ref.refType().isBlank()) throw new CatalogException("PRICING_CONFIG_REF_TYPE_REQUIRED");
      if (!PRICING_CONFIG_REFERENCE_TYPES.contains(ref.refType())) throw new CatalogException("PRICING_CONFIG_REF_TYPE_UNSUPPORTED");
      if (ref.refCode() == null || ref.refCode().isBlank()) throw new CatalogException("PRICING_CONFIG_REF_CODE_REQUIRED");
      if (ref.versionId() == null) throw new CatalogException("PRICING_CONFIG_VERSION_REQUIRED");
      Integer count = jdbc.queryForObject("""
          select count(*) from catalog.catalog_version_control
          where tenant_id=? and artifact_type=? and artifact_code=? and version_control_id=?
            and effective_start <= ? and (effective_end is null or effective_end > ?)
            and status in ('DRAFT','VALIDATED','PENDING_APPROVAL','APPROVED','PUBLISHED')
          """, Integer.class, tenantId, ref.refType(), ref.refCode(), ref.versionId(), java.sql.Date.valueOf(asOf), java.sql.Date.valueOf(asOf));
      if (count == null || count == 0) throw new CatalogException("PRICING_CONFIG_REFERENCE_NOT_ACTIVE");
    }
  }

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

  private Map<String, List<String>> referenceVersionMap(Object value) {
    if (value == null) return Map.of();
    return mapper.convertValue(value, mapper.getTypeFactory().constructMapType(Map.class, mapper.getTypeFactory().constructType(String.class), mapper.getTypeFactory().constructCollectionType(List.class, String.class)));
  }

  private List<String> stringListValue(Object value) {
    if (value == null) return List.of();
    return mapper.convertValue(value, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
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
