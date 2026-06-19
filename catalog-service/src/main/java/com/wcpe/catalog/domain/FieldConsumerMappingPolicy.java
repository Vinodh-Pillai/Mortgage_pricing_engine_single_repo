package com.wcpe.catalog.domain;

import java.util.*;
import java.util.stream.Collectors;

final class FieldConsumerMappingPolicy {
  private FieldConsumerMappingPolicy() {}

  static FieldConsumerMappingResponse normalize(String pathConsumer, FieldConsumerMappingRequest request,
                                                List<FieldMetadataResponse> availableFields,
                                                String fallbackActiveVersion) {
    String consumer = required(firstNonBlank(pathConsumer, request == null ? null : request.consumer()), "FIELD_CONSUMER_REQUIRED");
    String mappingScope = slug(firstNonBlank(request == null ? null : request.mappingScope(), "tenant-active"));
    String activeVersion = required(fallbackActiveVersion, "FIELD_CONSUMER_ACTIVE_VERSION_REQUIRED");
    List<FieldConsumerMappingItem> requested = request == null || request.fields() == null ? List.of() : request.fields().stream().filter(Objects::nonNull).toList();
    if (requested.isEmpty()) throw new CatalogException("FIELD_CONSUMER_MAPPING_FIELDS_REQUIRED");

    Map<String, FieldMetadataResponse> byFieldId = (availableFields == null ? List.<FieldMetadataResponse>of() : availableFields).stream()
        .collect(Collectors.toMap(FieldMetadataResponse::id, field -> field, (a, b) -> a, LinkedHashMap::new));
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    List<FieldConsumerMappedFieldResponse> fields = new ArrayList<>();
    for (FieldConsumerMappingItem item : requested) {
      String fieldId = stableFieldId(item.fieldId());
      FieldMetadataResponse field = byFieldId.get(fieldId);
      if (field == null) throw new CatalogException("FIELD_CONSUMER_FIELD_NOT_FOUND");
      String consumerFieldKey = required(item.consumerFieldKey(), "FIELD_CONSUMER_KEY_REQUIRED");
      String duplicateKey = fieldId + "|" + consumerFieldKey;
      if (!seen.add(duplicateKey)) throw new CatalogException("FIELD_CONSUMER_MAPPING_DUPLICATE");
      fields.add(new FieldConsumerMappedFieldResponse(field.id(), field.id(), field.name(), field.description(), field.category(),
          field.valueType(), field.sourceGroup(), consumerFieldKey, trimToNull(item.dataTableKey()), trimToNull(item.usageType()),
          sorted(item.attributes())));
    }
    return new FieldConsumerMappingResponse(slug(consumer), mappingScope, activeVersion, "tenant:" + mappingScope,
        List.copyOf(fields), fields.size(), null, sorted(request == null ? null : request.metadata()));
  }

  static FieldConsumerReferenceResponse references(String fieldId, List<FieldConsumerMappingResponse> mappings) {
    String stableFieldId = stableFieldId(fieldId);
    List<FieldConsumerReference> references = new ArrayList<>();
    for (FieldConsumerMappingResponse mapping : mappings == null ? List.<FieldConsumerMappingResponse>of() : mappings) {
      for (FieldConsumerMappedFieldResponse field : mapping.fields() == null ? List.<FieldConsumerMappedFieldResponse>of() : mapping.fields()) {
        if (stableFieldId.equals(field.fieldId())) references.add(new FieldConsumerReference(mapping.consumer(), mapping.mappingScope(),
            mapping.activeVersion(), field.consumerFieldKey(), field.dataTableKey(), field.usageType()));
      }
    }
    references.sort(Comparator.comparing(FieldConsumerReference::consumer).thenComparing(FieldConsumerReference::mappingScope));
    return new FieldConsumerReferenceResponse(stableFieldId, List.copyOf(references), references.size());
  }

  static String mappingCode(String consumer, String mappingScope) {
    return slug(required(consumer, "FIELD_CONSUMER_REQUIRED")) + ":" + slug(firstNonBlank(mappingScope, "tenant-active"));
  }

  static FieldConsumerMappingResponse withAuditRef(FieldConsumerMappingResponse response, String auditRef) {
    return new FieldConsumerMappingResponse(response.consumer(), response.mappingScope(), response.activeVersion(), response.sourceScope(),
        response.fields(), response.fieldCount(), auditRef, response.metadata());
  }

  static FieldConsumerMappingResponse withActiveVersion(FieldConsumerMappingResponse response, String activeVersion) {
    return new FieldConsumerMappingResponse(response.consumer(), response.mappingScope(), required(activeVersion, "FIELD_CONSUMER_ACTIVE_VERSION_REQUIRED"), response.sourceScope(),
        response.fields(), response.fieldCount(), response.auditRef(), response.metadata());
  }

  private static String stableFieldId(String value) {
    String normalized = required(value, "FIELD_CONSUMER_FIELD_ID_REQUIRED").toLowerCase(Locale.ROOT).trim()
        .replace('_', '-').replace(' ', '-');
    if (normalized.startsWith("field@")) return "field@" + normalized.substring("field@".length()).replaceAll("[^a-z0-9@._-]", "-");
    return "field@" + normalized.replaceAll("[^a-z0-9._-]", "-");
  }

  private static String slug(String value) {
    return required(value, "FIELD_CONSUMER_REQUIRED").toLowerCase(Locale.ROOT).trim()
        .replace('_', '-').replace(' ', '-').replaceAll("[^a-z0-9._:-]", "-");
  }

  private static String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private static String required(String value, String code) {
    if (value == null || value.isBlank()) throw new CatalogException(code);
    return value.trim();
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static Map<String, Object> sorted(Map<String, Object> values) {
    if (values == null || values.isEmpty()) return Map.of();
    Map<String, Object> sorted = new LinkedHashMap<>();
    values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
    return Collections.unmodifiableMap(sorted);
  }
}
