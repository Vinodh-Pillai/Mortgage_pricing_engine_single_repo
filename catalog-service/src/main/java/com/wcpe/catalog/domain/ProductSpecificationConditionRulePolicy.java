package com.wcpe.catalog.domain;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

final class ProductSpecificationConditionRulePolicy {
  private static final Set<String> PRODUCT_SPEC_CATEGORIES = Set.of(
      "product", "calculation", "credit", "credit-application", "aus", "custom", "system");
  private static final Set<String> ENUM_OPERATORS = Set.of("parent-field-is-one-of", "parent-field-is-not-one-of");

  private ProductSpecificationConditionRulePolicy() {}

  static ProductSpecificationConditionDraft normalizeDraft(ProductSpecificationConditionDraftRequest request,
                                                          List<FieldMetadataResponse> availableFields,
                                                          List<EnumerationTypeResponse> enumerations,
                                                          String actorId) {
    if (request == null) throw new CatalogException("PRODUCT_SPEC_CONDITION_DRAFT_REQUIRED");
    Map<String, FieldMetadataResponse> fieldsById = productSpecFieldsById(availableFields);
    Map<String, EnumerationTypeResponse> enumByType = enumerationsByType(enumerations);
    List<ProductSpecificationConditionRuleEdit> include = normalizeRules(request.includeConditions(), "include", fieldsById, enumByType);
    List<ProductSpecificationConditionRuleEdit> additional = normalizeRules(request.additionalConditions(), "additional", fieldsById, enumByType);
    if (include.isEmpty() && additional.isEmpty()) throw new CatalogException("PRODUCT_SPEC_CONDITION_DRAFT_REQUIRED");
    return new ProductSpecificationConditionDraft("DRAFT", include, additional, Instant.now(), actorId);
  }

  static void validatePublish(List<FieldMetadataResponse> availableFields,
                              Optional<ProductSpecificationConditionDraft> conditionDraft,
                              List<EnumerationTypeResponse> enumerations) {
    if (conditionDraft.isEmpty()) return;
    try {
      normalizeRules(conditionDraft.get().includeConditions(), "include", productSpecFieldsById(availableFields), enumerationsByType(enumerations));
      normalizeRules(conditionDraft.get().additionalConditions(), "additional", productSpecFieldsById(availableFields), enumerationsByType(enumerations));
    } catch (CatalogException ex) {
      if (isConcreteDependencyError(ex)) throw ex;
      throw new CatalogException("PRODUCT_SPEC_CONDITION_RULE_INVALID");
    }
  }

  private static boolean isConcreteDependencyError(CatalogException ex) {
    return Set.of(
        "PRODUCT_SPEC_CONDITION_PARENT_FIELD_NOT_AVAILABLE",
        "PRODUCT_SPEC_CONDITION_ENUM_TYPE_NOT_AVAILABLE",
        "PRODUCT_SPEC_CONDITION_VARIANT_NOT_AVAILABLE")
        .contains(ex.getMessage());
  }

  static ProductSpecificationFieldConditionEvaluationResponse evaluateField(String fieldId,
                                                                           List<FieldMetadataResponse> availableFields,
                                                                           Optional<ProductSpecificationConditionDraft> conditionDraft,
                                                                           Map<String, Object> parentValues,
                                                                           List<EnumerationTypeResponse> enumerations) {
    String normalizedFieldId = required(fieldId, "PRODUCT_SPEC_FIELD_ID_REQUIRED");
    FieldMetadataResponse field = productSpecFieldsById(availableFields).get(normalizedFieldId);
    if (field == null) throw new CatalogException("PRODUCT_SPEC_FIELD_NOT_FOUND");
    List<Map<String, Object>> includeConditions = new ArrayList<>();
    collectExistingConditions(field.conditions(), includeConditions);
    conditionDraft.ifPresent(draft -> draft.includeConditions().stream()
        .filter(rule -> normalizedFieldId.equals(rule.fieldId()))
        .map(ProductSpecificationConditionRulePolicy::conditionMap)
        .forEach(includeConditions::add));
    conditionDraft.ifPresent(draft -> draft.additionalConditions().stream()
        .filter(rule -> normalizedFieldId.equals(rule.fieldId()))
        .map(ProductSpecificationConditionRulePolicy::conditionMap)
        .forEach(includeConditions::add));
    if (includeConditions.isEmpty()) {
      return new ProductSpecificationFieldConditionEvaluationResponse(normalizedFieldId, true, "APPLICABLE", List.of());
    }
    ConditionEvaluationResponse response = ConditionModelEvaluator.evaluate(Map.of("conditions", includeConditions), parentValues, enumerations);
    return new ProductSpecificationFieldConditionEvaluationResponse(normalizedFieldId, response.applicable(), response.status(), response.explanations());
  }

  static Map<String, Object> conditionAuditPayload(ProductSpecificationConditionDraft draft) {
    return Map.of(
        "draftStatus", draft.draftStatus(),
        "includeConditionCount", draft.includeConditions().size(),
        "additionalConditionCount", draft.additionalConditions().size(),
        "includeFieldIds", fieldIds(draft.includeConditions()),
        "additionalFieldIds", fieldIds(draft.additionalConditions()));
  }

  private static List<ProductSpecificationConditionRuleEdit> normalizeRules(List<ProductSpecificationConditionRuleEdit> rules,
                                                                           String type,
                                                                           Map<String, FieldMetadataResponse> fieldsById,
                                                                           Map<String, EnumerationTypeResponse> enumByType) {
    List<ProductSpecificationConditionRuleEdit> safeRules = rules == null ? List.of() : rules.stream().filter(Objects::nonNull).toList();
    List<ProductSpecificationConditionRuleEdit> normalized = new ArrayList<>();
    Set<String> conditionIds = new LinkedHashSet<>();
    for (int i = 0; i < safeRules.size(); i++) {
      ProductSpecificationConditionRuleEdit rule = safeRules.get(i);
      String fieldId = required(rule.fieldId(), "PRODUCT_SPEC_FIELD_ID_REQUIRED");
      FieldMetadataResponse field = fieldsById.get(fieldId);
      if (field == null) throw new CatalogException("PRODUCT_SPEC_FIELD_NOT_FOUND");
      String parentFieldId = required(rule.parentFieldId(), "PRODUCT_SPEC_CONDITION_PARENT_FIELD_REQUIRED");
      FieldMetadataResponse parentField = fieldsById.get(parentFieldId);
      if (parentField == null) throw new CatalogException("PRODUCT_SPEC_CONDITION_PARENT_FIELD_NOT_AVAILABLE");
      if (fieldId.equals(parentFieldId)) throw new CatalogException("PRODUCT_SPEC_CONDITION_PARENT_FIELD_CONFLICT");
      String operator = normalizeOperator(required(rule.operator(), "PRODUCT_SPEC_CONDITION_OPERATOR_REQUIRED"));
      String conditionId = rule.conditionId() == null || rule.conditionId().isBlank() ? type + "-condition-" + (i + 1) : rule.conditionId().trim();
      if (!conditionIds.add(type + ":" + conditionId)) throw new CatalogException("PRODUCT_SPEC_CONDITION_ID_DUPLICATE");
      String enumTypeId = normalizeEnumType(rule.enumTypeId());
      List<String> variantIds = normalizeVariantIds(rule.variantIds());
      Object value = rule.value();
      if (ENUM_OPERATORS.contains(operator)) {
        String parentEnumTypeId = enumTypeIdForParent(parentField);
        if (enumTypeId != null && !enumTypeId.equals(parentEnumTypeId)) {
          throw new CatalogException("PRODUCT_SPEC_CONDITION_ENUM_TYPE_NOT_AVAILABLE");
        }
        enumTypeId = parentEnumTypeId;
        EnumerationTypeResponse enumeration = enumByType.get(normalizeEnumType(enumTypeId));
        if (enumeration == null) throw new CatalogException("PRODUCT_SPEC_CONDITION_ENUM_TYPE_NOT_AVAILABLE");
        if (variantIds.isEmpty()) throw new CatalogException("PRODUCT_SPEC_CONDITION_VARIANTS_REQUIRED");
        Set<String> availableVariants = enumeration.variants() == null ? Set.of() : enumeration.variants().stream()
            .map(EnumerationVariantResponse::variantId)
            .map(ProductSpecificationConditionRulePolicy::normalizeEnumType)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!availableVariants.containsAll(variantIds)) throw new CatalogException("PRODUCT_SPEC_CONDITION_VARIANT_NOT_AVAILABLE");
      } else if ("parent-field-has-value".equals(operator)) {
        if (value == null || value.toString().isBlank()) throw new CatalogException("PRODUCT_SPEC_CONDITION_VALUE_REQUIRED");
      } else if ("parent-field-is-not-blank".equals(operator) || "parent-field-is-blank".equals(operator)) {
        value = null;
      } else {
        throw new CatalogException("PRODUCT_SPEC_CONDITION_OPERATOR_UNSUPPORTED");
      }
      ProductSpecificationConditionRuleEdit normalizedRule = new ProductSpecificationConditionRuleEdit(
          fieldId, conditionId, operator, parentFieldId, enumTypeId, variantIds, value,
          readableExpression(operator, parentFieldId, enumTypeId, variantIds, value));
      validateEvaluatorShape(normalizedRule, enumByType);
      normalized.add(normalizedRule);
    }
    return List.copyOf(normalized);
  }

  private static void validateEvaluatorShape(ProductSpecificationConditionRuleEdit rule, Map<String, EnumerationTypeResponse> enumByType) {
    Object sampleParentValue = sampleParentValue(rule);
    ConditionEvaluationResponse response = ConditionModelEvaluator.evaluate(conditionMap(rule), Map.of(rule.parentFieldId(), sampleParentValue), List.copyOf(enumByType.values()));
    if ("INVALID".equals(response.status())) throw new CatalogException("PRODUCT_SPEC_CONDITION_RULE_INVALID");
  }

  private static Object sampleParentValue(ProductSpecificationConditionRuleEdit rule) {
    if (ENUM_OPERATORS.contains(rule.operator())) return rule.variantIds().get(0);
    if ("parent-field-is-blank".equals(rule.operator())) return "";
    if ("parent-field-is-not-blank".equals(rule.operator())) return "sample-value";
    return rule.value();
  }

  private static Map<String, Object> conditionMap(ProductSpecificationConditionRuleEdit rule) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("conditionId", rule.conditionId());
    map.put("operator", rule.operator());
    map.put("parentFieldId", rule.parentFieldId());
    if (rule.enumTypeId() != null) map.put("enumTypeId", rule.enumTypeId());
    if (rule.variantIds() != null && !rule.variantIds().isEmpty()) map.put("variantIds", rule.variantIds());
    if (rule.value() != null) map.put("value", rule.value());
    if (rule.readableExpression() != null) map.put("readableExpression", rule.readableExpression());
    return Collections.unmodifiableMap(map);
  }

  private static String readableExpression(String operator,
                                           String parentFieldId,
                                           String enumTypeId,
                                           List<String> variantIds,
                                           Object value) {
    return switch (operator) {
      case "parent-field-is-one-of" -> parentFieldId + " is one of " + variantIds;
      case "parent-field-is-not-one-of" -> parentFieldId + " is not one of " + variantIds;
      case "parent-field-has-value" -> parentFieldId + " has value " + Objects.toString(value, "");
      case "parent-field-is-not-blank" -> parentFieldId + " is not blank";
      case "parent-field-is-blank" -> parentFieldId + " is blank";
      default -> enumTypeId == null ? parentFieldId + " " + operator : parentFieldId + " " + operator + " " + enumTypeId;
    };
  }

  @SuppressWarnings("unchecked")
  private static void collectExistingConditions(Map<String, Object> conditions, List<Map<String, Object>> out) {
    if (conditions == null || conditions.isEmpty()) return;
    Object nested = conditions.get("conditions");
    if (nested instanceof List<?> list) {
      for (Object item : list) if (item instanceof Map<?, ?> map) out.add((Map<String, Object>) map);
    } else if ((conditions.containsKey("operator") || conditions.containsKey("conditionOperator")) && conditions.containsKey("parentFieldId")) {
      out.add(conditions);
    }
  }

  private static Map<String, FieldMetadataResponse> productSpecFieldsById(List<FieldMetadataResponse> fields) {
    Map<String, FieldMetadataResponse> byId = new LinkedHashMap<>();
    for (FieldMetadataResponse field : fields == null ? List.<FieldMetadataResponse>of() : fields) {
      if (isProductSpecificationField(field)) byId.put(field.id(), field);
    }
    return byId;
  }

  private static boolean isProductSpecificationField(FieldMetadataResponse field) {
    String sourceGroup = normalizeCategory(field.sourceGroup());
    String category = "productfields".equals(sourceGroup) ? "product" : normalizeCategory(field.category()).replace("creditapplication", "credit");
    if (category.isBlank()) category = "system";
    return PRODUCT_SPEC_CATEGORIES.contains(category.replace("_", "-"));
  }

  private static Map<String, EnumerationTypeResponse> enumerationsByType(List<EnumerationTypeResponse> enumerations) {
    Map<String, EnumerationTypeResponse> byType = new LinkedHashMap<>();
    for (EnumerationTypeResponse enumeration : enumerations == null ? List.<EnumerationTypeResponse>of() : enumerations) {
      byType.put(normalizeEnumType(enumeration.enumTypeId()), enumeration);
    }
    return byType;
  }

  private static String enumTypeIdForParent(FieldMetadataResponse parentField) {
    Object fromConditions = parentField.conditions() == null ? null : first(parentField.conditions(), "enumTypeId", "enumType");
    if (fromConditions != null && !fromConditions.toString().isBlank()) return normalizeEnumType(fromConditions.toString());
    if (!"enum".equals(parentField.valueType())) throw new CatalogException("PRODUCT_SPEC_CONDITION_PARENT_ENUM_REQUIRED");
    return normalizeEnumType(parentField.id().replace("field@", ""));
  }

  private static Object first(Map<String, Object> values, String... keys) {
    for (String key : keys) if (values.containsKey(key)) return values.get(key);
    return null;
  }

  private static List<String> normalizeVariantIds(List<String> variantIds) {
    if (variantIds == null) return List.of();
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String variantId : variantIds) {
      String id = normalizeEnumType(variantId);
      if (id != null) normalized.add(id);
    }
    return List.copyOf(normalized);
  }

  private static List<String> fieldIds(List<ProductSpecificationConditionRuleEdit> rules) {
    return rules.stream().map(ProductSpecificationConditionRuleEdit::fieldId).distinct().toList();
  }

  private static String required(String value, String errorCode) {
    if (value == null || value.isBlank()) throw new CatalogException(errorCode);
    return value.trim();
  }

  private static String normalizeOperator(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
  }

  private static String normalizeEnumType(String value) {
    String normalized = normalizeOperator(value);
    return normalized.isBlank() ? null : normalized;
  }

  private static String normalizeCategory(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
  }
}
