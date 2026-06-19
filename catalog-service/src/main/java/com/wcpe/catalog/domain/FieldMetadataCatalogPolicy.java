package com.wcpe.catalog.domain;

import java.util.*;

final class FieldMetadataCatalogPolicy {
  private static final Set<String> SUPPORTED_VALUE_TYPES = Set.of(
      "header", "enum", "number", "string", "date", "time", "duration", "us-state", "us-county");

  private FieldMetadataCatalogPolicy() {}

  static List<FieldMetadataResponse> normalize(FieldMetadataImportRequest request) {
    if (request == null) throw new CatalogException("FIELD_METADATA_IMPORT_REQUEST_REQUIRED");
    String source = blank(request.sourceName()) ? "ReferenceFormfields.json" : request.sourceName().trim();
    Map<String, FieldMetadataResponse> byId = new LinkedHashMap<>();
    merge(byId, request.productFields(), "productFields", source);
    merge(byId, request.creditApplicationFields(), "creditApplicationFields", source);
    merge(byId, request.pipelineOnlyFields(), "pipelineOnlyFields", source);
    merge(byId, request.rawFields(), "rawFields", source);
    if (byId.isEmpty()) throw new CatalogException("FIELD_METADATA_REQUIRED");
    return List.copyOf(byId.values());
  }

  private static void merge(Map<String, FieldMetadataResponse> byId, List<FieldMetadataInput> fields, String defaultSourceGroup, String source) {
    for (FieldMetadataInput input : fields == null ? List.<FieldMetadataInput>of() : fields) {
      FieldMetadataResponse field = normalizeOne(input, defaultSourceGroup, source);
      if (byId.containsKey(field.id())) throw new CatalogException("FIELD_ID_DUPLICATE");
      byId.put(field.id(), field);
    }
  }

  private static FieldMetadataResponse normalizeOne(FieldMetadataInput input, String defaultSourceGroup, String source) {
    if (input == null) throw new CatalogException("FIELD_METADATA_RECORD_REQUIRED");
    String id = required(input.id(), "FIELD_ID_REQUIRED");
    String name = required(input.name(), "FIELD_NAME_REQUIRED");
    String category = required(input.category(), "FIELD_CATEGORY_REQUIRED");
    String valueType = canonicalValueType(input.valueType());
    String sourceGroup = blank(input.sourceGroup()) ? defaultSourceGroup : input.sourceGroup().trim();
    String disposition = blank(input.disposition()) ? "native" : input.disposition().trim();
    return new FieldMetadataResponse(id, trimToNull(input.oldId()), name, trimToNull(input.description()), category,
        valueType, sourceGroup, safeConditions(input.conditions()), disposition, source);
  }

  private static String canonicalValueType(String value) {
    String canonical = required(value, "FIELD_VALUE_TYPE_REQUIRED")
        .toLowerCase(Locale.ROOT)
        .replace('_', '-')
        .replace(' ', '-');
    if (!SUPPORTED_VALUE_TYPES.contains(canonical)) throw new CatalogException("FIELD_VALUE_TYPE_UNSUPPORTED");
    return canonical;
  }

  private static Map<String, Object> safeConditions(Map<String, Object> conditions) {
    if (conditions == null || conditions.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(new LinkedHashMap<>(conditions));
  }

  private static String required(String value, String errorCode) {
    if (blank(value)) throw new CatalogException(errorCode);
    return value.trim();
  }

  private static String trimToNull(String value) {
    return blank(value) ? null : value.trim();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
