package com.wcpe.catalog.domain;

import java.util.*;

final class ConditionModelEvaluator {
  private static final String APPLICABLE = "APPLICABLE";
  private static final String NOT_APPLICABLE = "NOT_APPLICABLE";
  private static final String BLOCKED = "BLOCKED";
  private static final String INVALID = "INVALID";

  private ConditionModelEvaluator() {}

  static ConditionEvaluationResponse evaluate(ConditionEvaluationRequest request) {
    if (request == null) throw new CatalogException("CONDITION_EVALUATION_REQUEST_REQUIRED");
    return evaluate(request.conditions(), request.parentValues(), request.enumerations());
  }

  static ConditionEvaluationResponse evaluate(Map<String, Object> conditions,
                                              Map<String, Object> parentValues,
                                              List<EnumerationTypeResponse> enumerations) {
    if (conditions == null || conditions.isEmpty()) {
      return new ConditionEvaluationResponse(true, APPLICABLE, List.of());
    }
    List<Map<String, Object>> conditionItems = conditionItems(conditions);
    if (conditionItems.isEmpty()) {
      return new ConditionEvaluationResponse(false, INVALID,
          List.of(explanation("condition-1", "", "", false, INVALID, "At least one condition is required.")));
    }

    String combination = combination(conditions);
    Map<String, Object> safeParentValues = parentValues == null ? Map.of() : parentValues;
    Map<String, EnumerationTypeResponse> enumByType = enumerationsByType(enumerations);
    List<ConditionEvaluationExplanation> explanations = new ArrayList<>();
    for (int i = 0; i < conditionItems.size(); i++) {
      explanations.add(evaluateOne(conditionItems.get(i), safeParentValues, enumByType, i + 1));
    }

    if (explanations.stream().anyMatch(e -> INVALID.equals(e.status()))) {
      return new ConditionEvaluationResponse(false, INVALID, List.copyOf(explanations));
    }
    if (explanations.stream().anyMatch(e -> BLOCKED.equals(e.status()))) {
      return new ConditionEvaluationResponse(false, BLOCKED, List.copyOf(explanations));
    }
    boolean applicable = "any".equals(combination)
        ? explanations.stream().anyMatch(ConditionEvaluationExplanation::matched)
        : explanations.stream().allMatch(ConditionEvaluationExplanation::matched);
    return new ConditionEvaluationResponse(applicable, applicable ? APPLICABLE : NOT_APPLICABLE, List.copyOf(explanations));
  }

  private static ConditionEvaluationExplanation evaluateOne(Map<String, Object> condition,
                                                            Map<String, Object> parentValues,
                                                            Map<String, EnumerationTypeResponse> enumByType,
                                                            int index) {
    String conditionId = stringValue(first(condition, "conditionId", "id"));
    if (blank(conditionId)) conditionId = "condition-" + index;
    String operator = normalizeOperator(stringValue(first(condition, "operator", "conditionOperator", "type")));
    String parentFieldId = stringValue(first(condition, "parentFieldId", "parentField"));
    if (blank(operator)) return explanation(conditionId, operator, parentFieldId, false, INVALID, "Condition operator is required.");
    if (blank(parentFieldId)) return explanation(conditionId, operator, parentFieldId, false, INVALID, "Parent field id is required.");
    if (!parentValues.containsKey(parentFieldId)) {
      return explanation(conditionId, operator, parentFieldId, false, BLOCKED, "Parent field value is missing.");
    }
    Object parentValue = parentValues.get(parentFieldId);

    return switch (operator) {
      case "parent-field-is-one-of" -> evaluateOneOf(conditionId, operator, parentFieldId, parentValue, condition, enumByType, false);
      case "parent-field-is-not-one-of" -> evaluateOneOf(conditionId, operator, parentFieldId, parentValue, condition, enumByType, true);
      case "parent-field-has-value" -> evaluateHasValue(conditionId, operator, parentFieldId, parentValue, condition);
      case "parent-field-is-not-blank" -> evaluateBlank(conditionId, operator, parentFieldId, parentValue, false);
      case "parent-field-is-blank" -> evaluateBlank(conditionId, operator, parentFieldId, parentValue, true);
      default -> explanation(conditionId, operator, parentFieldId, false, INVALID, "Unsupported condition operator.");
    };
  }

  private static ConditionEvaluationExplanation evaluateOneOf(String conditionId,
                                                              String operator,
                                                              String parentFieldId,
                                                              Object parentValue,
                                                              Map<String, Object> condition,
                                                              Map<String, EnumerationTypeResponse> enumByType,
                                                              boolean negated) {
    String enumTypeId = normalizeId(stringValue(first(condition, "enumTypeId", "enumType")));
    if (blank(enumTypeId)) return explanation(conditionId, operator, parentFieldId, false, INVALID, "Enum type id is required.");
    EnumerationTypeResponse enumeration = enumByType.get(enumTypeId);
    if (enumeration == null) return explanation(conditionId, operator, parentFieldId, false, INVALID, "Enum type is not available.");
    Set<String> variantIds = variantIds(condition);
    if (variantIds.isEmpty()) return explanation(conditionId, operator, parentFieldId, false, INVALID, "At least one enum variant id is required.");
    Set<String> available = new LinkedHashSet<>();
    for (EnumerationVariantResponse variant : enumeration.variants() == null ? List.<EnumerationVariantResponse>of() : enumeration.variants()) {
      available.add(normalizeId(variant.variantId()));
    }
    if (!available.containsAll(variantIds)) return explanation(conditionId, operator, parentFieldId, false, INVALID, "Configured enum variant is not available.");

    boolean contains = variantIds.contains(normalizeId(stringValue(parentValue)));
    boolean matched = negated ? !contains : contains;
    return explanation(conditionId, operator, parentFieldId, matched, matched ? APPLICABLE : NOT_APPLICABLE,
        matched ? "Parent field satisfies enum condition." : "Parent field does not satisfy enum condition.");
  }

  private static ConditionEvaluationExplanation evaluateHasValue(String conditionId,
                                                                 String operator,
                                                                 String parentFieldId,
                                                                 Object parentValue,
                                                                 Map<String, Object> condition) {
    Object requiredValue = first(condition, "value", "requiredValue", "parentValue");
    if (requiredValue == null) return explanation(conditionId, operator, parentFieldId, false, INVALID, "Required parent value is missing.");
    boolean matched = Objects.equals(stringValue(parentValue), stringValue(requiredValue));
    return explanation(conditionId, operator, parentFieldId, matched, matched ? APPLICABLE : NOT_APPLICABLE,
        matched ? "Parent field has the configured value." : "Parent field does not have the configured value.");
  }

  private static ConditionEvaluationExplanation evaluateBlank(String conditionId,
                                                              String operator,
                                                              String parentFieldId,
                                                              Object parentValue,
                                                              boolean blankExpected) {
    boolean blank = blank(stringValue(parentValue));
    boolean matched = blankExpected ? blank : !blank;
    return explanation(conditionId, operator, parentFieldId, matched, matched ? APPLICABLE : NOT_APPLICABLE,
        matched ? (blankExpected ? "Parent field is blank." : "Parent field is not blank.")
            : (blankExpected ? "Parent field is not blank." : "Parent field is blank."));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> conditionItems(Map<String, Object> conditions) {
    Object nested = conditions.get("conditions");
    if (nested instanceof List<?> list) {
      List<Map<String, Object>> items = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) items.add((Map<String, Object>) map);
      }
      return items;
    }
    return List.of(conditions);
  }

  private static String combination(Map<String, Object> conditions) {
    String value = normalizeOperator(stringValue(first(conditions, "combination", "combinationSemantics", "match")));
    if ("any".equals(value) || "or".equals(value)) return "any";
    return "all";
  }

  private static Map<String, EnumerationTypeResponse> enumerationsByType(List<EnumerationTypeResponse> enumerations) {
    Map<String, EnumerationTypeResponse> byType = new LinkedHashMap<>();
    for (EnumerationTypeResponse enumeration : enumerations == null ? List.<EnumerationTypeResponse>of() : enumerations) {
      byType.put(normalizeId(enumeration.enumTypeId()), enumeration);
    }
    return byType;
  }

  private static Set<String> variantIds(Map<String, Object> condition) {
    Set<String> ids = new LinkedHashSet<>();
    addVariant(ids, condition.get("variantId"));
    addVariant(ids, condition.get("variantIds"));
    return ids;
  }

  private static void addVariant(Set<String> ids, Object value) {
    if (value instanceof Collection<?> values) {
      for (Object item : values) addVariant(ids, item);
    } else {
      String id = normalizeId(stringValue(value));
      if (!blank(id)) ids.add(id);
    }
  }

  private static Object first(Map<String, Object> values, String... keys) {
    for (String key : keys) {
      if (values.containsKey(key)) return values.get(key);
    }
    return null;
  }

  private static ConditionEvaluationExplanation explanation(String conditionId, String operator, String parentFieldId,
                                                            boolean matched, String status, String message) {
    return new ConditionEvaluationExplanation(conditionId, operator, parentFieldId, matched, status, message);
  }

  private static String normalizeOperator(String value) {
    return blank(value) ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
  }

  private static String normalizeId(String value) {
    return normalizeOperator(value);
  }

  private static String stringValue(Object value) {
    return value == null ? null : Objects.toString(value).trim();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
