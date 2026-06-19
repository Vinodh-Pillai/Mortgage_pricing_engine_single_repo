package com.wcpe.catalog.domain;

import java.util.*;
import java.util.stream.Collectors;

final class ProductSpecificationFieldListPolicy {
  private static final Set<String> PRODUCT_SPEC_CATEGORIES = Set.of(
      "product", "calculation", "credit", "credit-application", "aus", "custom", "system");

  private ProductSpecificationFieldListPolicy() {}

  static ProductSpecificationFieldListResponse list(List<FieldMetadataResponse> fields,
                                                    Optional<ProductSpecificationFieldOrderDraft> draft,
                                                    boolean tenantSpecific) {
    return list(fields, draft, Optional.empty(), tenantSpecific);
  }

  static ProductSpecificationFieldListResponse list(List<FieldMetadataResponse> fields,
                                                    Optional<ProductSpecificationFieldOrderDraft> draft,
                                                    Optional<ProductSpecificationTenantFieldDraft> tenantFieldDraft,
                                                    boolean tenantSpecific) {
    List<FieldMetadataResponse> productSpecFields = (fields == null ? List.<FieldMetadataResponse>of() : fields).stream()
        .filter(ProductSpecificationFieldListPolicy::isProductSpecificationField)
        .toList();
    ProductSpecificationTenantFieldDraft tenantEdits = tenantFieldDraft.orElse(null);
    Map<String, ProductSpecificationFieldAliasEdit> aliases = aliasEditsByFieldId(tenantEdits);
    List<FieldMetadataResponse> combined = new ArrayList<>(productSpecFields);
    combined.addAll(nativeFields(tenantEdits, productSpecFields));
    List<FieldMetadataResponse> ordered = applyDraftOrder(combined, draft.map(ProductSpecificationFieldOrderDraft::fieldIds).orElse(List.of()));
    List<ProductSpecificationFieldResponse> responseFields = new ArrayList<>();
    for (int i = 0; i < ordered.size(); i++) {
      FieldMetadataResponse field = ordered.get(i);
      ProductSpecificationFieldAliasEdit alias = aliases.get(field.id());
      String sourceCategory = sourceCategory(field);
      String status = status(field);
      Map<String, Object> conditions = sortedConditions(field.conditions());
      responseFields.add(new ProductSpecificationFieldResponse(field.id(), stableApiKey(field.id()), displayName(field, alias), aliases(field), displayDescription(field, alias),
          field.valueType(), sourceCategory, status, sourceCategory + ":" + status, sourceCategory + ":" + status, typeChip(field.valueType()), i + 1, true,
          migrationStatus(conditions), conditions, conditionSummary(conditions)));
    }
    return new ProductSpecificationFieldListResponse(tenantSpecific ? "tenant-draft" : "system/default", tenantSpecific,
        List.copyOf(responseFields), responseFields.size());
  }

  static ProductSpecificationFieldOrderDraft normalizeDraft(ProductSpecificationFieldOrderDraftRequest request,
                                                           List<FieldMetadataResponse> availableFields,
                                                           String actorId) {
    if (request == null || request.fieldIds() == null || request.fieldIds().isEmpty()) throw new CatalogException("PRODUCT_SPEC_FIELD_ORDER_REQUIRED");
    Set<String> available = (availableFields == null ? List.<FieldMetadataResponse>of() : availableFields).stream()
        .filter(ProductSpecificationFieldListPolicy::isProductSpecificationField)
        .map(FieldMetadataResponse::id)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    LinkedHashSet<String> ordered = new LinkedHashSet<>();
    for (String id : request.fieldIds()) {
      String normalized = id == null ? "" : id.trim();
      if (normalized.isBlank()) throw new CatalogException("PRODUCT_SPEC_FIELD_ID_REQUIRED");
      if (!available.contains(normalized)) throw new CatalogException("PRODUCT_SPEC_FIELD_NOT_FOUND");
      if (!ordered.add(normalized)) throw new CatalogException("PRODUCT_SPEC_FIELD_ORDER_DUPLICATE");
    }
    return new ProductSpecificationFieldOrderDraft("DRAFT", List.copyOf(ordered), java.time.Instant.now(), actorId);
  }

  static ProductSpecificationTenantFieldDraft normalizeTenantFieldDraft(ProductSpecificationTenantFieldDraftRequest request,
                                                                        List<FieldMetadataResponse> availableFields,
                                                                        String actorId) {
    if (request == null) throw new CatalogException("PRODUCT_SPEC_FIELD_DRAFT_REQUIRED");
    List<ProductSpecificationFieldAliasEdit> aliases = safeAliases(request.aliases());
    List<ProductSpecificationNativeFieldEdit> nativeFields = safeNativeFields(request.nativeFields());
    if (aliases.isEmpty() && nativeFields.isEmpty()) throw new CatalogException("PRODUCT_SPEC_FIELD_DRAFT_REQUIRED");
    Map<String, FieldMetadataResponse> available = (availableFields == null ? List.<FieldMetadataResponse>of() : availableFields).stream()
        .filter(ProductSpecificationFieldListPolicy::isProductSpecificationField)
        .collect(Collectors.toMap(FieldMetadataResponse::id, field -> field, (a, b) -> a, LinkedHashMap::new));
    LinkedHashSet<String> aliasIds = new LinkedHashSet<>();
    List<ProductSpecificationFieldAliasEdit> normalizedAliases = new ArrayList<>();
    for (ProductSpecificationFieldAliasEdit alias : aliases) {
      String fieldId = required(alias.fieldId(), "PRODUCT_SPEC_FIELD_ID_REQUIRED");
      FieldMetadataResponse field = available.get(fieldId);
      if (field == null) throw new CatalogException("PRODUCT_SPEC_FIELD_NOT_FOUND");
      if (!"inherited".equals(status(field))) throw new CatalogException("PRODUCT_SPEC_ALIAS_INHERITED_FIELD_REQUIRED");
      if (!aliasIds.add(fieldId)) throw new CatalogException("PRODUCT_SPEC_FIELD_ALIAS_DUPLICATE");
      normalizedAliases.add(new ProductSpecificationFieldAliasEdit(fieldId, trimToNull(alias.nameAlias()), trimToNull(alias.descriptionAlias()),
          field.name(), field.description(), field.valueType(), field.sourceGroup(), sortedConditions(field.conditions())));
    }
    LinkedHashSet<String> nativeIds = new LinkedHashSet<>();
    List<ProductSpecificationNativeFieldEdit> normalizedNative = new ArrayList<>();
    for (ProductSpecificationNativeFieldEdit nativeField : nativeFields) {
      String fieldId = stableApiKey(required(nativeField.fieldId(), "PRODUCT_SPEC_NATIVE_FIELD_ID_REQUIRED"));
      FieldMetadataResponse existingField = available.get(fieldId);
      if (existingField != null) {
        String requestedValueType = canonicalNativeValueType(nativeField);
        if (isConsumerMapped(existingField.conditions()) && !Objects.equals(existingField.valueType(), requestedValueType)) {
          throw new CatalogException("PRODUCT_SPEC_TYPE_BREAKING_EDIT_REQUIRES_MIGRATION");
        }
        throw new CatalogException("PRODUCT_SPEC_NATIVE_FIELD_CONFLICT");
      }
      if (!nativeIds.add(fieldId)) throw new CatalogException("PRODUCT_SPEC_NATIVE_FIELD_DUPLICATE");
      FieldMetadataResponse normalized = FieldMetadataCatalogPolicy.normalize(new FieldMetadataImportRequest("tenant-product-specification",
          List.of(new FieldMetadataInput(fieldId, null, nativeField.name(), nativeField.description(), nativeField.category(), nativeField.valueType(), nativeField.sourceGroup(), nativeField.conditions(), "native")),
          List.of(), List.of(), List.of())).get(0);
      normalizedNative.add(new ProductSpecificationNativeFieldEdit(normalized.id(), normalized.name(), normalized.description(), normalized.category(),
          normalized.valueType(), normalized.sourceGroup(), normalized.conditions()));
    }
    return new ProductSpecificationTenantFieldDraft("DRAFT", List.copyOf(normalizedAliases), List.copyOf(normalizedNative), java.time.Instant.now(), actorId);
  }

  static void validateTenantDraftAgainstBase(ProductSpecificationTenantFieldDraft draft,
                                             List<FieldMetadataResponse> availableFields) {
    if (draft == null) return;
    Map<String, FieldMetadataResponse> available = (availableFields == null ? List.<FieldMetadataResponse>of() : availableFields).stream()
        .filter(ProductSpecificationFieldListPolicy::isProductSpecificationField)
        .collect(Collectors.toMap(FieldMetadataResponse::id, field -> field, (a, b) -> a, LinkedHashMap::new));
    for (ProductSpecificationFieldAliasEdit alias : safeAliases(draft.aliases())) {
      String fieldId = required(alias.fieldId(), "PRODUCT_SPEC_FIELD_ID_REQUIRED");
      FieldMetadataResponse field = available.get(fieldId);
      if (field == null || !"inherited".equals(status(field))) throw new CatalogException("PRODUCT_SPEC_TENANT_OVERRIDE_CONFLICT");
      if (baseMetadataChanged(alias, field)) throw new CatalogException("PRODUCT_SPEC_TENANT_OVERRIDE_CONFLICT");
    }
    for (ProductSpecificationNativeFieldEdit nativeField : safeNativeFields(draft.nativeFields())) {
      String fieldId = stableApiKey(required(nativeField.fieldId(), "PRODUCT_SPEC_NATIVE_FIELD_ID_REQUIRED"));
      FieldMetadataResponse field = available.get(fieldId);
      if (field != null) throw new CatalogException("PRODUCT_SPEC_TENANT_OVERRIDE_CONFLICT");
    }
  }

  static List<FieldMetadataResponse> normalizeSystemImport(ProductSpecificationSystemFieldImportRequest request,
                                                           List<FieldMetadataResponse> systemFields) {
    if (request == null || request.fieldIds() == null || request.fieldIds().isEmpty()) throw new CatalogException("PRODUCT_SPEC_SYSTEM_FIELD_IDS_REQUIRED");
    Map<String, FieldMetadataResponse> available = (systemFields == null ? List.<FieldMetadataResponse>of() : systemFields).stream()
        .filter(ProductSpecificationFieldListPolicy::isProductSpecificationField)
        .collect(Collectors.toMap(FieldMetadataResponse::id, field -> field, (a, b) -> a, LinkedHashMap::new));
    LinkedHashSet<String> requested = new LinkedHashSet<>();
    List<FieldMetadataResponse> selected = new ArrayList<>();
    for (String id : request.fieldIds()) {
      String fieldId = required(id, "PRODUCT_SPEC_FIELD_ID_REQUIRED");
      if (!requested.add(fieldId)) throw new CatalogException("PRODUCT_SPEC_SYSTEM_FIELD_DUPLICATE");
      FieldMetadataResponse field = available.get(fieldId);
      if (field == null) throw new CatalogException("PRODUCT_SPEC_SYSTEM_FIELD_NOT_FOUND");
      selected.add(new FieldMetadataResponse(field.id(), field.oldId(), field.name(), field.description(), field.category(), field.valueType(),
          field.sourceGroup(), field.conditions(), "inherited", field.source()));
    }
    return List.copyOf(selected);
  }

  private static List<FieldMetadataResponse> applyDraftOrder(List<FieldMetadataResponse> fields, List<String> draftOrder) {
    if (draftOrder == null || draftOrder.isEmpty()) return List.copyOf(fields);
    Map<String, FieldMetadataResponse> byId = fields.stream().collect(Collectors.toMap(FieldMetadataResponse::id, field -> field, (a, b) -> a, LinkedHashMap::new));
    List<FieldMetadataResponse> ordered = new ArrayList<>();
    for (String id : draftOrder) {
      FieldMetadataResponse field = byId.remove(id);
      if (field != null) ordered.add(field);
    }
    ordered.addAll(byId.values());
    return List.copyOf(ordered);
  }

  private static boolean isProductSpecificationField(FieldMetadataResponse field) {
    String category = sourceCategory(field);
    return PRODUCT_SPEC_CATEGORIES.contains(category);
  }

  private static Map<String, ProductSpecificationFieldAliasEdit> aliasEditsByFieldId(ProductSpecificationTenantFieldDraft draft) {
    if (draft == null || draft.aliases() == null || draft.aliases().isEmpty()) return Map.of();
    Map<String, ProductSpecificationFieldAliasEdit> byId = new LinkedHashMap<>();
    for (ProductSpecificationFieldAliasEdit alias : draft.aliases()) if (alias != null && alias.fieldId() != null) byId.put(alias.fieldId(), alias);
    return byId;
  }

  private static List<FieldMetadataResponse> nativeFields(ProductSpecificationTenantFieldDraft draft, List<FieldMetadataResponse> baseFields) {
    if (draft == null || draft.nativeFields() == null || draft.nativeFields().isEmpty()) return List.of();
    Set<String> baseIds = baseFields.stream().map(FieldMetadataResponse::id).collect(Collectors.toSet());
    List<FieldMetadataResponse> response = new ArrayList<>();
    for (ProductSpecificationNativeFieldEdit nativeField : draft.nativeFields()) {
      if (nativeField == null || baseIds.contains(nativeField.fieldId())) continue;
      response.add(new FieldMetadataResponse(nativeField.fieldId(), null, nativeField.name(), nativeField.description(), nativeField.category(),
          nativeField.valueType(), nativeField.sourceGroup() == null || nativeField.sourceGroup().isBlank() ? "productFields" : nativeField.sourceGroup(),
          nativeField.conditions() == null ? Map.of() : nativeField.conditions(), "native", "tenant-product-specification"));
    }
    return List.copyOf(response);
  }

  private static String displayName(FieldMetadataResponse field, ProductSpecificationFieldAliasEdit alias) {
    return alias == null || alias.nameAlias() == null || alias.nameAlias().isBlank() ? field.name() : alias.nameAlias();
  }

  private static String displayDescription(FieldMetadataResponse field, ProductSpecificationFieldAliasEdit alias) {
    return alias == null || alias.descriptionAlias() == null || alias.descriptionAlias().isBlank() ? field.description() : alias.descriptionAlias();
  }

  private static String sourceCategory(FieldMetadataResponse field) {
    String sourceGroup = normalize(field.sourceGroup());
    if ("productfields".equals(sourceGroup)) return "product";
    if ("creditapplicationfields".equals(sourceGroup)) return "credit";
    String category = normalize(field.category()).replace("creditapplication", "credit");
    if (category.isBlank()) return "system";
    return category.replace("_", "-");
  }

  private static String status(FieldMetadataResponse field) {
    String disposition = field.disposition() == null || field.disposition().isBlank() ? "native" : field.disposition().trim().toLowerCase(Locale.ROOT);
    return switch (disposition) {
      case "inherited" -> "inherited";
      case "disabled" -> "disabled";
      case "custom", "native" -> disposition;
      default -> "native";
    };
  }

  private static List<String> aliases(FieldMetadataResponse field) {
    LinkedHashSet<String> values = new LinkedHashSet<>();
    if (field.oldId() != null && !field.oldId().isBlank() && !field.oldId().equals(field.id())) values.add(field.oldId());
    Object configured = field.conditions() == null ? null : field.conditions().get("aliases");
    if (configured instanceof List<?> list) for (Object item : list) if (item != null && !item.toString().isBlank()) values.add(item.toString().trim());
    if (configured instanceof String value && !value.isBlank()) values.add(value.trim());
    return List.copyOf(values);
  }

  private static Map<String, Object> sortedConditions(Map<String, Object> conditions) {
    if (conditions == null || conditions.isEmpty()) return Map.of();
    Map<String, Object> sorted = new LinkedHashMap<>();
    conditions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
    return Collections.unmodifiableMap(sorted);
  }

  private static String conditionSummary(Map<String, Object> conditions) {
    if (conditions == null || conditions.isEmpty()) return "none";
    return sortedConditions(conditions).entrySet().stream()
        .map(entry -> entry.getKey() + "=" + Objects.toString(entry.getValue()))
        .collect(Collectors.joining("; "));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
  }

  private static List<ProductSpecificationFieldAliasEdit> safeAliases(List<ProductSpecificationFieldAliasEdit> aliases) {
    return aliases == null ? List.of() : aliases.stream().filter(Objects::nonNull).toList();
  }

  private static boolean baseMetadataChanged(ProductSpecificationFieldAliasEdit alias, FieldMetadataResponse current) {
    if (!hasBaseSnapshot(alias)) return false;
    return !Objects.equals(alias.baseName(), current.name())
        || !Objects.equals(alias.baseDescription(), current.description())
        || !Objects.equals(alias.baseValueType(), current.valueType())
        || !Objects.equals(alias.baseSourceGroup(), current.sourceGroup())
        || !Objects.equals(sortedConditions(alias.baseConditions()), sortedConditions(current.conditions()));
  }

  private static boolean hasBaseSnapshot(ProductSpecificationFieldAliasEdit alias) {
    return alias.baseName() != null
        || alias.baseDescription() != null
        || alias.baseValueType() != null
        || alias.baseSourceGroup() != null
        || (alias.baseConditions() != null && !alias.baseConditions().isEmpty());
  }

  private static List<ProductSpecificationNativeFieldEdit> safeNativeFields(List<ProductSpecificationNativeFieldEdit> fields) {
    return fields == null ? List.of() : fields.stream().filter(Objects::nonNull).toList();
  }

  private static String required(String value, String errorCode) {
    if (value == null || value.isBlank()) throw new CatalogException(errorCode);
    return value.trim();
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String stableApiKey(String value) {
    String normalized = required(value, "PRODUCT_SPEC_NATIVE_FIELD_ID_REQUIRED").toLowerCase(Locale.ROOT).trim()
        .replace('_', '-').replace(' ', '-');
    if (normalized.startsWith("field@")) return "field@" + normalized.substring("field@".length()).replaceAll("[^a-z0-9@._-]", "-");
    return "field@" + normalized.replaceAll("[^a-z0-9._-]", "-");
  }

  private static String typeChip(String valueType) {
    String value = valueType == null || valueType.isBlank() ? "unknown" : valueType.trim().toLowerCase(Locale.ROOT);
    return "type:" + value;
  }

  private static String migrationStatus(Map<String, Object> conditions) {
    return isConsumerMapped(conditions) ? "migration-controlled" : "editable";
  }

  private static String canonicalNativeValueType(ProductSpecificationNativeFieldEdit nativeField) {
    return FieldMetadataCatalogPolicy.normalize(new FieldMetadataImportRequest("tenant-product-specification",
        List.of(new FieldMetadataInput("field@type-check", null, "Type Check", "Type check", blankToCustom(nativeField.category()), nativeField.valueType(), nativeField.sourceGroup(), Map.of(), "native")),
        List.of(), List.of(), List.of())).get(0).valueType();
  }

  private static String blankToCustom(String value) {
    return value == null || value.isBlank() ? "custom" : value;
  }

  private static boolean isConsumerMapped(Map<String, Object> conditions) {
    if (conditions == null || conditions.isEmpty()) return false;
    for (String key : List.of("consumerMapping", "consumerMappings", "mappedConsumers", "mappingConsumers", "usedByConsumerMapping")) {
      Object value = conditions.get(key);
      if (Boolean.TRUE.equals(value)) return true;
      if (value instanceof Collection<?> collection && !collection.isEmpty()) return true;
      if (value instanceof String text && !text.isBlank()) return true;
    }
    return false;
  }
}
