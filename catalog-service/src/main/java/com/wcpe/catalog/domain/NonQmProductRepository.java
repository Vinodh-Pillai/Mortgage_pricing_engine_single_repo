package com.wcpe.catalog.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class NonQmProductRepository {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final String ELIGIBILITY_METADATA_KEY = "eligibility";
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final NonQmProductSchemaRegistry schemas;

  NonQmProductRepository(JdbcTemplate jdbc, ObjectMapper mapper, NonQmProductSchemaRegistry schemas) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.schemas = schemas;
  }

  List<NonQmProductResponse> list(UUID tenantId, String status, String productType, String investorCode, String channelCode) {
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("""
        select distinct p.product_id, p.code, p.name, p.status, p.created_at, p.updated_at,
               coalesce(p.product_family, p.type) as product_family,
               p.product_type, p.non_qm_attributes::text, p.pricing_metadata::text
        from catalog.product p
        left join catalog.product_investor_channel pic on pic.tenant_id = p.tenant_id and pic.product_code = p.code
        where p.tenant_id = ? and coalesce(p.product_family, p.type) = 'NON_QM'
        """);
    args.add(tenantId);
    if (status != null && !status.isBlank()) { sql.append(" and p.status = ?"); args.add(status.trim().toUpperCase(Locale.ROOT)); }
    if (productType != null && !productType.isBlank()) { sql.append(" and p.product_type = ?"); args.add(NonQmProductType.fromExternal(productType).externalCode()); }
    if (investorCode != null && !investorCode.isBlank()) { sql.append(" and pic.investor_code = ?"); args.add(investorCode.trim().toUpperCase(Locale.ROOT)); }
    if (channelCode != null && !channelCode.isBlank()) { schemas.requireAllowedChannel(channelCode); sql.append(" and pic.channel_code = ?"); args.add(channelCode.trim().toUpperCase(Locale.ROOT)); }
    sql.append(" order by p.product_type, p.code");
    return jdbc.query(sql.toString(), (rs, rowNum) -> toResponse(rs, mappings(tenantId, rs.getString("code"))), args.toArray());
  }

  Optional<NonQmProductResponse> find(UUID tenantId, String productCode) {
    List<NonQmProductResponse> rows = jdbc.query("""
        select p.product_id, p.code, p.name, p.status, p.created_at, p.updated_at,
               coalesce(p.product_family, p.type) as product_family,
               p.product_type, p.non_qm_attributes::text, p.pricing_metadata::text
        from catalog.product p
        where p.tenant_id = ? and p.code = ? and coalesce(p.product_family, p.type) = 'NON_QM'
        """, (rs, rowNum) -> toResponse(rs, mappings(tenantId, rs.getString("code"))), tenantId, requireCode(productCode));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  @Transactional
  NonQmProductResponse create(UUID tenantId, NonQmProductRequest request) {
    ProductDraft draft = validateRequest(request);
    if (find(tenantId, draft.code()).isPresent()) throw new CatalogException("PRODUCT_CODE_DUPLICATE");
    UUID productId = UUID.randomUUID();
    jdbc.update("""
        insert into catalog.product
          (product_id, tenant_id, code, name, type, product_family, product_type, non_qm_attributes, pricing_metadata, status)
        values (?, ?, ?, ?, 'NON_QM', 'NON_QM', ?, ?::jsonb, ?::jsonb, ?)
        """, productId, tenantId, draft.code(), draft.name(), draft.productType(), json(draft.attributes()), json(draft.pricingMetadata()), draft.status());
    replaceMappings(tenantId, draft.code(), draft.mappings());
    return find(tenantId, draft.code()).orElseThrow(() -> new CatalogException("NON_QM_PRODUCT_NOT_FOUND"));
  }

  @Transactional
  NonQmProductResponse update(UUID tenantId, String productCode, NonQmProductRequest request) {
    String code = requireCode(productCode);
    if (request == null) throw new CatalogException("NON_QM_PRODUCT_REQUIRED");
    if (find(tenantId, code).isEmpty()) throw new CatalogException("NON_QM_PRODUCT_NOT_FOUND");
    ProductDraft draft = validateRequest(updateRequest(code, request));
    jdbc.update("""
        update catalog.product
        set name = ?, type = 'NON_QM', product_family = 'NON_QM', product_type = ?, non_qm_attributes = ?::jsonb,
            pricing_metadata = ?::jsonb, status = ?, updated_at = now()
        where tenant_id = ? and code = ? and coalesce(product_family, type) = 'NON_QM'
        """, draft.name(), draft.productType(), json(draft.attributes()), json(draft.pricingMetadata()), draft.status(), tenantId, code);
    replaceMappings(tenantId, code, draft.mappings());
    return find(tenantId, code).orElseThrow(() -> new CatalogException("NON_QM_PRODUCT_NOT_FOUND"));
  }

  @Transactional
  NonQmProductResponse retire(UUID tenantId, String productCode) {
    String code = requireCode(productCode);
    int updated = jdbc.update("update catalog.product set status = 'RETIRED', updated_at = now() where tenant_id = ? and code = ? and coalesce(product_family, type) = 'NON_QM'", tenantId, code);
    if (updated == 0) throw new CatalogException("NON_QM_PRODUCT_NOT_FOUND");
    return find(tenantId, code).orElseThrow(() -> new CatalogException("NON_QM_PRODUCT_NOT_FOUND"));
  }

  NonQmImportResult importProducts(UUID tenantId, NonQmImportRequest request) {
    if (request == null || request.products() == null || request.products().isEmpty()) throw new CatalogException("NON_QM_IMPORT_EMPTY");
    int accepted = 0;
    List<String> rejected = new ArrayList<>();
    for (NonQmProductRequest product : request.products()) {
      try {
        if (product.productCode() != null && find(tenantId, product.productCode()).isPresent()) update(tenantId, product.productCode(), product);
        else create(tenantId, product);
        accepted++;
      } catch (RuntimeException ex) {
        rejected.add(product == null ? "<null>" : Objects.toString(product.productCode(), "<missing>"));
      }
    }
    return new NonQmImportResult(accepted, rejected.size(), List.copyOf(rejected));
  }

  List<NonQmProductExport> export(UUID tenantId, String productType) {
    return list(tenantId, "ACTIVE", productType, null, null).stream()
        .map(product -> new NonQmProductExport(product.productCode(), product.productFamily(), product.productType(), product.productName(), product.attributes(), product.investorMappings(), product.pricingMetadata(), "v1", Instant.now()))
        .toList();
  }

  private ProductDraft validateRequest(NonQmProductRequest request) {
    if (request == null) throw new CatalogException("NON_QM_PRODUCT_REQUIRED");
    String code = requireCode(request.productCode());
    String name = requireText(request.productName(), "PRODUCT_NAME_REQUIRED");
    String productType = NonQmProductType.fromExternal(request.productType()).externalCode();
    Map<String, Object> attributes = request.attributes() == null ? Map.of() : Map.copyOf(request.attributes());
    Map<String, Object> pricingMetadata = request.pricingMetadata() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.pricingMetadata());
    ProductEligibilityMetadata eligibility = normalizeEligibility(firstEligibility(request.eligibility(), pricingMetadata.get(ELIGIBILITY_METADATA_KEY)));
    pricingMetadata.put(ELIGIBILITY_METADATA_KEY, eligibility);
    schemas.requireValid(productType, attributes);
    List<NonQmInvestorChannelMapping> mappings = request.investorMappings() == null ? List.of() : request.investorMappings();
    if (mappings.isEmpty()) throw new CatalogException("NON_QM_INVESTOR_MAPPING_REQUIRED");
    for (NonQmInvestorChannelMapping mapping : mappings) validateMapping(mapping);
    String status = request.status() == null || request.status().isBlank() ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT);
    if (!Set.of("ACTIVE", "SUSPENDED", "RETIRED").contains(status)) throw new CatalogException("INVALID_PRODUCT_STATUS");
    return new ProductDraft(code, name, productType, attributes, Map.copyOf(pricingMetadata), List.copyOf(mappings), status);
  }

  private NonQmProductRequest updateRequest(String code, NonQmProductRequest request) {
    return new NonQmProductRequest(code, request.productName(), request.productType(), request.attributes(), request.pricingMetadata(), request.eligibility(), request.investorMappings(), request.status());
  }

  private ProductEligibilityMetadata firstEligibility(ProductEligibilityMetadata requestEligibility, Object metadataEligibility) {
    if (requestEligibility != null) return requestEligibility;
    if (metadataEligibility == null) return null;
    if (metadataEligibility instanceof ProductEligibilityMetadata eligibility) return eligibility;
    if (metadataEligibility instanceof Map<?, ?> map) return eligibilityFromMap(map);
    throw new CatalogException("INVALID_ELIGIBILITY_METADATA");
  }

  private ProductEligibilityMetadata eligibilityFromMap(Map<?, ?> map) {
    return new ProductEligibilityMetadata(
        ruleRefsFrom(map.get("ruleRefs")),
        fieldsFrom(map.get("requiredFields")),
        fieldsFrom(map.get("conditionalFields")),
        stringsFrom(map.get("explainabilityRefs")),
        stringOrNull(map.get("readinessStatus")),
        List.of());
  }

  private List<EligibilityRuleRef> ruleRefsFrom(Object value) {
    if (!(value instanceof List<?> refs)) return List.of();
    List<EligibilityRuleRef> out = new ArrayList<>();
    for (Object ref : refs) {
      if (!(ref instanceof Map<?, ?> map)) throw new CatalogException("INVALID_ELIGIBILITY_METADATA");
      out.add(new EligibilityRuleRef(stringOrNull(map.get("ruleRef")), stringOrNull(map.get("ruleType")), stringOrNull(map.get("sourceSystem")), integerOrNull(map.get("effectiveVersion")), instantOrNull(map.get("effectiveStart")), instantOrNull(map.get("effectiveEnd"))));
    }
    return out;
  }

  private List<EligibilityFieldMetadata> fieldsFrom(Object value) {
    if (!(value instanceof List<?> fields)) return List.of();
    List<EligibilityFieldMetadata> out = new ArrayList<>();
    for (Object field : fields) {
      if (!(field instanceof Map<?, ?> map)) throw new CatalogException("INVALID_ELIGIBILITY_METADATA");
      out.add(new EligibilityFieldMetadata(stringOrNull(map.get("fieldPath")), stringOrNull(map.get("configRef")), stringOrNull(map.get("message"))));
    }
    return out;
  }

  private static List<String> stringsFrom(Object value) { return value instanceof List<?> values ? values.stream().filter(Objects::nonNull).map(Object::toString).toList() : List.of(); }
  private static String stringOrNull(Object value) { return value == null ? null : value.toString(); }
  private static Integer integerOrNull(Object value) { return value == null ? null : Integer.valueOf(value.toString()); }
  private static Instant instantOrNull(Object value) { return value == null || value.toString().isBlank() ? null : Instant.parse(value.toString()); }

  private ProductEligibilityMetadata normalizeEligibility(ProductEligibilityMetadata metadata) {
    if (metadata == null) {
      return new ProductEligibilityMetadata(List.of(), List.of(), List.of(), List.of(), "INCOMPLETE",
          List.of(new NonQmValidationError("eligibility.ruleRefs", "ELIGIBILITY_RULE_REFS_REQUIRED", "Configure product eligibility rule refs before this product is ready for LOS eligibility evaluation.")));
    }
    List<EligibilityRuleRef> ruleRefs = safeRules(metadata.ruleRefs());
    List<EligibilityFieldMetadata> requiredFields = safeFields(metadata.requiredFields(), "requiredFields");
    List<EligibilityFieldMetadata> conditionalFields = safeFields(metadata.conditionalFields(), "conditionalFields");
    List<String> explainabilityRefs = metadata.explainabilityRefs() == null ? List.of() : metadata.explainabilityRefs().stream()
        .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).distinct().sorted().toList();
    List<NonQmValidationError> messages = new ArrayList<>();
    if (ruleRefs.isEmpty()) messages.add(new NonQmValidationError("eligibility.ruleRefs", "ELIGIBILITY_RULE_REFS_REQUIRED", "Configure at least one eligibility rule ref."));
    if (requiredFields.isEmpty() && conditionalFields.isEmpty()) messages.add(new NonQmValidationError("eligibility.fields", "ELIGIBILITY_FIELD_METADATA_REQUIRED", "Configure required or conditional LOS field metadata."));
    String readiness = messages.isEmpty() ? "COMPLETE" : "INCOMPLETE";
    return new ProductEligibilityMetadata(ruleRefs, requiredFields, conditionalFields, explainabilityRefs, readiness, List.copyOf(messages));
  }

  private List<EligibilityRuleRef> safeRules(List<EligibilityRuleRef> refs) {
    if (refs == null) return List.of();
    List<EligibilityRuleRef> normalized = new ArrayList<>();
    for (EligibilityRuleRef ref : refs) {
      if (ref == null) throw new CatalogException("ELIGIBILITY_RULE_REF_REQUIRED");
      String ruleRef = requireText(ref.ruleRef(), "ELIGIBILITY_RULE_REF_REQUIRED");
      String ruleType = requireText(ref.ruleType(), "ELIGIBILITY_RULE_TYPE_REQUIRED").toUpperCase(Locale.ROOT);
      String sourceSystem = requireText(ref.sourceSystem(), "ELIGIBILITY_RULE_SOURCE_REQUIRED").toUpperCase(Locale.ROOT);
      if (ref.effectiveVersion() == null || ref.effectiveVersion() <= 0) throw new CatalogException("ELIGIBILITY_RULE_VERSION_REQUIRED");
      if (ref.effectiveEnd() != null && ref.effectiveStart() != null && !ref.effectiveEnd().isAfter(ref.effectiveStart())) throw new CatalogException("ELIGIBILITY_RULE_EFFECTIVE_WINDOW_INVALID");
      normalized.add(new EligibilityRuleRef(ruleRef, ruleType, sourceSystem, ref.effectiveVersion(), ref.effectiveStart(), ref.effectiveEnd()));
    }
    return normalized.stream().distinct().toList();
  }

  private List<EligibilityFieldMetadata> safeFields(List<EligibilityFieldMetadata> fields, String scope) {
    if (fields == null) return List.of();
    List<EligibilityFieldMetadata> normalized = new ArrayList<>();
    for (EligibilityFieldMetadata field : fields) {
      if (field == null) throw new CatalogException("ELIGIBILITY_FIELD_METADATA_REQUIRED");
      normalized.add(new EligibilityFieldMetadata(
          requireText(field.fieldPath(), "ELIGIBILITY_FIELD_PATH_REQUIRED"),
          requireText(field.configRef(), "ELIGIBILITY_FIELD_CONFIG_REF_REQUIRED"),
          requireText(field.message(), "ELIGIBILITY_FIELD_MESSAGE_REQUIRED")));
    }
    return normalized.stream().distinct().toList();
  }

  private void validateMapping(NonQmInvestorChannelMapping mapping) {
    if (mapping == null) throw new CatalogException("NON_QM_INVESTOR_MAPPING_REQUIRED");
    requireText(mapping.investorCode(), "INVESTOR_CODE_REQUIRED");
    schemas.requireAllowedChannel(mapping.channelCode());
    String status = mapping.status() == null || mapping.status().isBlank() ? "ACTIVE" : mapping.status().trim().toUpperCase(Locale.ROOT);
    if (!Set.of("ACTIVE", "INACTIVE", "PENDING").contains(status)) throw new CatalogException("INVALID_INVESTOR_CHANNEL_STATUS");
    if (mapping.effectiveEnd() != null && mapping.effectiveStart() != null && !mapping.effectiveEnd().isAfter(mapping.effectiveStart())) throw new CatalogException("INVALID_EFFECTIVE_WINDOW");
  }

  private void replaceMappings(UUID tenantId, String productCode, List<NonQmInvestorChannelMapping> mappings) {
    jdbc.update("delete from catalog.product_investor_channel where tenant_id = ? and product_code = ?", tenantId, productCode);
    for (NonQmInvestorChannelMapping mapping : mappings) {
      jdbc.update("""
          insert into catalog.product_investor_channel
            (tenant_id, product_code, investor_code, channel_code, investor_product_code, status, pricing_priority, effective_start, effective_end)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, tenantId, productCode, mapping.investorCode().trim().toUpperCase(Locale.ROOT), mapping.channelCode().trim().toUpperCase(Locale.ROOT),
          mapping.investorProductCode(), mapping.status() == null || mapping.status().isBlank() ? "ACTIVE" : mapping.status().trim().toUpperCase(Locale.ROOT),
          mapping.pricingPriority(), date(mapping.effectiveStart()), date(mapping.effectiveEnd()));
    }
  }

  private List<NonQmInvestorChannelMapping> mappings(UUID tenantId, String productCode) {
    return jdbc.query("""
        select investor_code, channel_code, investor_product_code, status, pricing_priority, effective_start, effective_end
        from catalog.product_investor_channel
        where tenant_id = ? and product_code = ?
        order by investor_code, channel_code
        """, (rs, rowNum) -> new NonQmInvestorChannelMapping(
        rs.getString("investor_code"), rs.getString("channel_code"), rs.getString("investor_product_code"), rs.getString("status"),
        integerOrNull(rs, "pricing_priority"), localDateOrNull(rs, "effective_start"), localDateOrNull(rs, "effective_end")), tenantId, productCode);
  }

  private NonQmProductResponse toResponse(ResultSet rs, List<NonQmInvestorChannelMapping> mappings) throws SQLException {
    List<String> channels = mappings.stream().map(NonQmInvestorChannelMapping::channelCode).distinct().sorted().toList();
    Map<String, Object> pricingMetadata = parseMap(rs.getString("pricing_metadata"));
    ProductEligibilityMetadata eligibility = normalizeEligibility(firstEligibility(null, pricingMetadata.get(ELIGIBILITY_METADATA_KEY)));
    return new NonQmProductResponse(
        rs.getObject("product_id", UUID.class), rs.getString("code"), rs.getString("name"), "NON_QM", rs.getString("product_type"),
        parseMap(rs.getString("non_qm_attributes")), pricingMetadata, eligibility, mappings, channels,
        rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
  }

  private String json(Object value) {
    try { return mapper.writeValueAsString(value == null ? Map.of() : value); }
    catch (Exception ex) { throw new CatalogException("INVALID_JSON"); }
  }

  private Map<String, Object> parseMap(String json) {
    try { return json == null || json.isBlank() ? Map.of() : mapper.readValue(json, MAP_TYPE); }
    catch (Exception ex) { throw new CatalogException("INVALID_JSON"); }
  }

  private static String requireCode(String value) {
    String code = requireText(value, "PRODUCT_CODE_REQUIRED").trim().toUpperCase(Locale.ROOT);
    if (code.length() > 32) throw new CatalogException("PRODUCT_CODE_TOO_LONG");
    return code;
  }

  private static String requireText(String value, String error) {
    if (value == null || value.isBlank()) throw new CatalogException(error);
    return value.trim();
  }

  private static Date date(LocalDate value) { return value == null ? null : Date.valueOf(value); }
  private static LocalDate localDateOrNull(ResultSet rs, String column) throws SQLException { Date value = rs.getDate(column); return value == null ? null : value.toLocalDate(); }
  private static Integer integerOrNull(ResultSet rs, String column) throws SQLException { int value = rs.getInt(column); return rs.wasNull() ? null : value; }

  private record ProductDraft(String code, String name, String productType, Map<String, Object> attributes, Map<String, Object> pricingMetadata, List<NonQmInvestorChannelMapping> mappings, String status) {}
}
