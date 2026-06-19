package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.entry;

import java.util.*;
import org.junit.jupiter.api.Test;

class ConditionModelEvaluatorTest {
  @Test
  void oneOfMatchesConfiguredEnumVariant() {
    ConditionEvaluationResponse response = evaluate(Map.of(
        "conditionId", "channel-one-of",
        "operator", "parent-field-is-one-of",
        "parentFieldId", "field@product-channel",
        "enumTypeId", "product-channel",
        "variantIds", List.of("retail", "wholesale")),
        Map.of("field@product-channel", "retail"));

    assertThat(response.applicable()).isTrue();
    assertThat(response.status()).isEqualTo("APPLICABLE");
    assertThat(response.explanations()).extracting(ConditionEvaluationExplanation::conditionId)
        .containsExactly("channel-one-of");
    assertThat(response.explanations()).extracting(ConditionEvaluationExplanation::matched)
        .containsExactly(true);
  }

  @Test
  void notOneOfAppliesWhenParentValueIsOutsideConfiguredVariants() {
    ConditionEvaluationResponse response = evaluate(Map.of(
        "conditionId", "channel-not-one-of",
        "operator", "parent-field-is-not-one-of",
        "parentFieldId", "field@product-channel",
        "enumTypeId", "product-channel",
        "variantIds", List.of("retail")),
        Map.of("field@product-channel", "wholesale"));

    assertThat(response.applicable()).isTrue();
    assertThat(response.status()).isEqualTo("APPLICABLE");
  }

  @Test
  void hasValueMatchesRequiredParentValue() {
    ConditionEvaluationResponse response = ConditionModelEvaluator.evaluate(
        Map.of("conditionId", "loan-purpose-value", "operator", "parent-field-has-value", "parentFieldId", "field@loan-purpose", "value", "purchase"),
        Map.of("field@loan-purpose", "purchase"),
        List.of(enumeration()));

    assertThat(response.applicable()).isTrue();
    assertThat(response.status()).isEqualTo("APPLICABLE");
    assertThat(response.explanations()).singleElement()
        .extracting(ConditionEvaluationExplanation::message)
        .isEqualTo("Parent field has the configured value.");
  }

  @Test
  void multipleConditionsUseAllSemanticsAndExplainEachResult() {
    ConditionEvaluationResponse response = ConditionModelEvaluator.evaluate(Map.of(
            "combination", "all",
            "conditions", List.of(
                Map.of("conditionId", "channel", "operator", "parent-field-is-one-of", "parentFieldId", "field@product-channel", "enumTypeId", "product-channel", "variantIds", List.of("retail")),
                Map.of("conditionId", "purpose", "operator", "parent-field-has-value", "parentFieldId", "field@loan-purpose", "value", "purchase"))),
        Map.of("field@product-channel", "retail", "field@loan-purpose", "purchase"),
        List.of(enumeration()));

    assertThat(response.applicable()).isTrue();
    assertThat(response.status()).isEqualTo("APPLICABLE");
    assertThat(response.explanations()).extracting(ConditionEvaluationExplanation::conditionId)
        .containsExactly("channel", "purpose");
    assertThat(response.explanations()).extracting(ConditionEvaluationExplanation::status)
        .containsExactly("APPLICABLE", "APPLICABLE");
  }

  @Test
  void missingParentFieldReturnsBlockedInsteadOfGuessing() {
    ConditionEvaluationResponse response = evaluate(Map.of(
        "conditionId", "missing-parent",
        "operator", "parent-field-is-one-of",
        "parentFieldId", "field@product-channel",
        "enumTypeId", "product-channel",
        "variantIds", List.of("retail")),
        Map.of());

    assertThat(response.applicable()).isFalse();
    assertThat(response.status()).isEqualTo("BLOCKED");
    assertThat(response.explanations()).singleElement()
        .extracting(ConditionEvaluationExplanation::message)
        .isEqualTo("Parent field value is missing.");
  }

  @Test
  void missingEnumTypeReturnsInvalidInsteadOfGuessing() {
    ConditionEvaluationResponse response = ConditionModelEvaluator.evaluate(
        Map.of("conditionId", "missing-enum", "operator", "parent-field-is-one-of", "parentFieldId", "field@product-channel", "enumTypeId", "missing-channel", "variantIds", List.of("retail")),
        Map.of("field@product-channel", "retail"),
        List.of(enumeration()));

    assertThat(response.applicable()).isFalse();
    assertThat(response.status()).isEqualTo("INVALID");
    assertThat(response.explanations()).singleElement()
        .extracting(ConditionEvaluationExplanation::message)
        .isEqualTo("Enum type is not available.");
  }

  @Test
  void missingVariantReturnsInvalidInsteadOfGuessing() {
    ConditionEvaluationResponse response = evaluate(Map.of(
        "conditionId", "missing-variant",
        "operator", "parent-field-is-one-of",
        "parentFieldId", "field@product-channel",
        "enumTypeId", "product-channel",
        "variantIds", List.of("correspondent")),
        Map.of("field@product-channel", "retail"));

    assertThat(response.applicable()).isFalse();
    assertThat(response.status()).isEqualTo("INVALID");
    assertThat(response.explanations()).singleElement()
        .extracting(ConditionEvaluationExplanation::message)
        .isEqualTo("Configured enum variant is not available.");
  }

  @Test
  void exposesConditionEvaluationPayloadContractForUiEditors() {
    ConditionEvaluationResponse response = evaluate(Map.of(
        "conditionId", "channel-contract",
        "operator", "parent-field-is-one-of",
        "parentFieldId", "field@product-channel",
        "enumTypeId", "product-channel",
        "variantIds", List.of("retail")),
        Map.of("field@product-channel", "retail"));

    Map<String, Object> explanation = new LinkedHashMap<>();
    ConditionEvaluationExplanation first = response.explanations().get(0);
    explanation.put("conditionId", first.conditionId());
    explanation.put("operator", first.operator());
    explanation.put("parentFieldId", first.parentFieldId());
    explanation.put("matched", first.matched());
    explanation.put("status", first.status());
    explanation.put("message", first.message());

    assertThat(new LinkedHashMap<>(Map.of(
        "applicable", response.applicable(),
        "status", response.status(),
        "explanations", List.of(explanation))))
        .contains(
            entry("applicable", true),
            entry("status", "APPLICABLE"),
            entry("explanations", List.of(explanation)));
    assertThat(explanation).containsExactly(
        entry("conditionId", "channel-contract"),
        entry("operator", "parent-field-is-one-of"),
        entry("parentFieldId", "field@product-channel"),
        entry("matched", true),
        entry("status", "APPLICABLE"),
        entry("message", "Parent field satisfies enum condition."));
  }

  private static ConditionEvaluationResponse evaluate(Map<String, Object> conditions, Map<String, Object> parentValues) {
    return ConditionModelEvaluator.evaluate(conditions, parentValues, List.of(enumeration()));
  }

  private static EnumerationTypeResponse enumeration() {
    return new EnumerationTypeResponse("product-channel", "Product Channel", List.of(
        new EnumerationVariantResponse("retail", "101", "Retail"),
        new EnumerationVariantResponse("wholesale", "102", "Wholesale")),
        "ReferenceFormfields.json", "system/default");
  }
}
