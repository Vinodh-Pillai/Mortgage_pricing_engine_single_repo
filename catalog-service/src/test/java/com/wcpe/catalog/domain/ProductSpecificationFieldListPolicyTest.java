package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProductSpecificationFieldListPolicyTest {
  @Test
  void exposesFieldListDisplayAndDetailMetadataForProductSpecificationSurfaces() {
    ProductSpecificationFieldListResponse response = ProductSpecificationFieldListPolicy.list(fields(), Optional.empty(), true);

    assertThat(response.sourceScope()).isEqualTo("tenant-draft");
    assertThat(response.fields()).extracting(ProductSpecificationFieldResponse::fieldId)
        .containsExactly("field@product-channel", "field@dti", "field@fico", "field@aus-result", "field@custom-note", "field@system-status");
    assertThat(response.fields()).extracting(ProductSpecificationFieldResponse::sourceCategory)
        .containsExactly("product", "calculation", "credit", "aus", "custom", "system");
    assertThat(response.fields()).extracting(ProductSpecificationFieldResponse::provenanceBadge)
        .contains("product:inherited", "calculation:native", "custom:custom", "system:native");
    assertThat(response.fields()).extracting(ProductSpecificationFieldResponse::stableApiKey)
        .containsExactly("field@product-channel", "field@dti", "field@fico", "field@aus-result", "field@custom-note", "field@system-status");
    assertThat(response.fields()).extracting(ProductSpecificationFieldResponse::sourceChip)
        .containsExactly("product:inherited", "calculation:native", "credit:inherited", "aus:native", "custom:custom", "system:native");
    assertThat(response.fields()).extracting(ProductSpecificationFieldResponse::typeChip)
        .containsExactly("type:enum", "type:number", "type:number", "type:string", "type:string", "type:string");
    assertThat(response.fields()).extracting(ProductSpecificationFieldResponse::draggable).containsOnly(true);

    ProductSpecificationFieldResponse dti = response.fields().get(1);
    assertThat(dti.name()).isEqualTo("Debt-to-income Ratio");
    assertThat(dti.valueType()).isEqualTo("number");
    assertThat(dti.aliases()).containsExactly("legacy-dti", "DTI");
    assertThat(dti.description()).contains("Calculated ratio");
    assertThat(dti.conditions()).containsEntry("parentFieldId", "field@product-channel");
    assertThat(dti.conditionSummary()).contains("parentFieldId=field@product-channel");
  }

  @Test
  void savesTenantDraftOrderWithoutMutatingSystemDefaultOrdering() {
    ProductSpecificationFieldOrderDraft draft = ProductSpecificationFieldListPolicy.normalizeDraft(
        new ProductSpecificationFieldOrderDraftRequest(List.of("field@custom-note", "field@product-channel")), fields(), "admin-1");

    ProductSpecificationFieldListResponse tenant = ProductSpecificationFieldListPolicy.list(fields(), Optional.of(draft), true);
    ProductSpecificationFieldListResponse system = ProductSpecificationFieldListPolicy.list(fields(), Optional.empty(), false);

    assertThat(draft.draftStatus()).isEqualTo("DRAFT");
    assertThat(draft.actorId()).isEqualTo("admin-1");
    assertThat(tenant.fields()).extracting(ProductSpecificationFieldResponse::fieldId)
        .startsWith("field@custom-note", "field@product-channel")
        .containsExactly("field@custom-note", "field@product-channel", "field@dti", "field@fico", "field@aus-result", "field@system-status");
    assertThat(tenant.fields()).extracting(ProductSpecificationFieldResponse::displayOrder).containsExactly(1, 2, 3, 4, 5, 6);
    assertThat(system.sourceScope()).isEqualTo("system/default");
    assertThat(system.fields()).extracting(ProductSpecificationFieldResponse::fieldId)
        .containsExactly("field@product-channel", "field@dti", "field@fico", "field@aus-result", "field@custom-note", "field@system-status");
  }

  @Test
  void rejectsUnknownOrDuplicateDraftOrderFields() {
    assertThatThrownBy(() -> ProductSpecificationFieldListPolicy.normalizeDraft(
        new ProductSpecificationFieldOrderDraftRequest(List.of("field@missing")), fields(), "admin-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_FIELD_NOT_FOUND");
    assertThatThrownBy(() -> ProductSpecificationFieldListPolicy.normalizeDraft(
        new ProductSpecificationFieldOrderDraftRequest(List.of("field@fico", "field@fico")), fields(), "admin-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_FIELD_ORDER_DUPLICATE");
  }

  @Test
  void appliesInheritedTenantAliasesWithoutMutatingSystemDefaults() {
    ProductSpecificationTenantFieldDraft tenantDraft = ProductSpecificationFieldListPolicy.normalizeTenantFieldDraft(
        new ProductSpecificationTenantFieldDraftRequest(
            List.of(new ProductSpecificationFieldAliasEdit("field@product-channel", "Tenant Channel", "Tenant-only channel label")),
            List.of()), fields(), "admin-1");

    ProductSpecificationFieldListResponse tenant = ProductSpecificationFieldListPolicy.list(fields(), Optional.empty(), Optional.of(tenantDraft), true);
    ProductSpecificationFieldListResponse system = ProductSpecificationFieldListPolicy.list(fields(), Optional.empty(), Optional.empty(), false);

    assertThat(tenant.fields().get(0).fieldId()).isEqualTo("field@product-channel");
    assertThat(tenant.fields().get(0).name()).isEqualTo("Tenant Channel");
    assertThat(tenant.fields().get(0).description()).isEqualTo("Tenant-only channel label");
    assertThat(system.fields().get(0).name()).isEqualTo("Product Channel");
    assertThat(system.fields().get(0).description()).isEqualTo("Channel selector");
    assertThat(tenantDraft.aliases()).hasSize(1);
  }

  @Test
  void blankAliasesRenderSystemDefaultNameAndDescription() {
    ProductSpecificationTenantFieldDraft tenantDraft = ProductSpecificationFieldListPolicy.normalizeTenantFieldDraft(
        new ProductSpecificationTenantFieldDraftRequest(
            List.of(new ProductSpecificationFieldAliasEdit("field@fico", " ", "")), List.of()), fields(), "admin-1");

    ProductSpecificationFieldResponse fico = ProductSpecificationFieldListPolicy.list(fields(), Optional.empty(), Optional.of(tenantDraft), true)
        .fields().stream().filter(field -> field.fieldId().equals("field@fico")).findFirst().orElseThrow();

    assertThat(fico.name()).isEqualTo("Representative Credit Score");
    assertThat(fico.description()).isEqualTo("Credit score field");
  }

  @Test
  void detectsTenantAliasBaseMetadataDriftBeforeActivation() {
    ProductSpecificationTenantFieldDraft tenantDraft = ProductSpecificationFieldListPolicy.normalizeTenantFieldDraft(
        new ProductSpecificationTenantFieldDraftRequest(
            List.of(new ProductSpecificationFieldAliasEdit("field@product-channel", "Tenant Channel", "Tenant-only channel label")),
            List.of()), fields(), "admin-1");
    List<FieldMetadataResponse> changedBase = fields().stream()
        .map(field -> field.id().equals("field@product-channel")
            ? new FieldMetadataResponse(field.id(), field.oldId(), "Product Channel Updated", "Changed channel selector",
                field.category(), "string", "changedProductFields", Map.of("enumTypeId", "product-channel-v2"), field.disposition(), field.source())
            : field)
        .toList();

    assertThatThrownBy(() -> ProductSpecificationFieldListPolicy.validateTenantDraftAgainstBase(tenantDraft, changedBase))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_TENANT_OVERRIDE_CONFLICT");
  }

  @Test
  void keepsNativeTenantFieldMetadataTenantScopedAndWithinValidationLimits() {
    ProductSpecificationTenantFieldDraft alphaDraft = ProductSpecificationFieldListPolicy.normalizeTenantFieldDraft(
        new ProductSpecificationTenantFieldDraftRequest(List.of(), List.of(
            new ProductSpecificationNativeFieldEdit("field@alpha-custom", "Alpha Custom", "Alpha-only custom field", "custom", "string", "rawFields", Map.of("section", "product-spec")))),
        fields(), "admin-1");

    ProductSpecificationFieldListResponse alpha = ProductSpecificationFieldListPolicy.list(fields(), Optional.empty(), Optional.of(alphaDraft), true);
    ProductSpecificationFieldListResponse beta = ProductSpecificationFieldListPolicy.list(fields(), Optional.empty(), Optional.empty(), true);

    assertThat(alpha.fields()).extracting(ProductSpecificationFieldResponse::fieldId).contains("field@alpha-custom");
    ProductSpecificationFieldResponse nativeField = alpha.fields().stream().filter(field -> field.fieldId().equals("field@alpha-custom")).findFirst().orElseThrow();
    assertThat(nativeField.name()).isEqualTo("Alpha Custom");
    assertThat(nativeField.description()).isEqualTo("Alpha-only custom field");
    assertThat(nativeField.valueType()).isEqualTo("string");
    assertThat(nativeField.status()).isEqualTo("native");
    assertThat(nativeField.conditions()).containsEntry("section", "product-spec");
    assertThat(beta.fields()).extracting(ProductSpecificationFieldResponse::fieldId).doesNotContain("field@alpha-custom");
    assertThatThrownBy(() -> ProductSpecificationFieldListPolicy.normalizeTenantFieldDraft(
        new ProductSpecificationTenantFieldDraftRequest(List.of(), List.of(
            new ProductSpecificationNativeFieldEdit("field@bad", "Bad", "Bad", "custom", "pricing-rate", "rawFields", Map.of()))), fields(), "admin-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("FIELD_VALUE_TYPE_UNSUPPORTED");
  }

  @Test
  void assignsStableApiKeyForNewNativeFieldsIndependentOfDisplayName() {
    ProductSpecificationTenantFieldDraft draft = ProductSpecificationFieldListPolicy.normalizeTenantFieldDraft(
        new ProductSpecificationTenantFieldDraftRequest(List.of(), List.of(
            new ProductSpecificationNativeFieldEdit("Custom Processing Note", "Processing Note", "Tenant note", "custom", "text", "rawFields", Map.of()))),
        fields(), "admin-1");

    assertThat(draft.nativeFields()).extracting(ProductSpecificationNativeFieldEdit::fieldId).containsExactly("field@custom-processing-note");
    ProductSpecificationFieldResponse response = ProductSpecificationFieldListPolicy.list(fields(), Optional.empty(), Optional.of(draft), true)
        .fields().stream().filter(field -> field.fieldId().equals("field@custom-processing-note")).findFirst().orElseThrow();
    assertThat(response.stableApiKey()).isEqualTo("field@custom-processing-note");
    assertThat(response.typeChip()).isEqualTo("type:text");
    assertThat(response.sourceChip()).isEqualTo("custom:native");
  }

  @Test
  void blocksTypeBreakingEditForConsumerMappedFieldUntilMigrationVersionExists() {
    assertThatThrownBy(() -> ProductSpecificationFieldListPolicy.normalizeTenantFieldDraft(
        new ProductSpecificationTenantFieldDraftRequest(List.of(), List.of(
            new ProductSpecificationNativeFieldEdit("field@dti", "Debt-to-income Ratio", "Mapped ratio", "calculation", "string", "rawFields", Map.of()))),
        fields(), "admin-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_TYPE_BREAKING_EDIT_REQUIRES_MIGRATION");
  }

  @Test
  void normalizesSystemImportAsInheritedTenantReferencesWithoutSystemMutation() {
    List<FieldMetadataResponse> selected = ProductSpecificationFieldListPolicy.normalizeSystemImport(
        new ProductSpecificationSystemFieldImportRequest(List.of("field@product-channel", "field@fico")), fields());

    assertThat(selected).extracting(FieldMetadataResponse::id).containsExactly("field@product-channel", "field@fico");
    assertThat(selected).extracting(FieldMetadataResponse::disposition).containsOnly("inherited");
    assertThat(selected.get(0).source()).isEqualTo("ReferenceFormfields.json");

    assertThatThrownBy(() -> ProductSpecificationFieldListPolicy.normalizeSystemImport(
        new ProductSpecificationSystemFieldImportRequest(List.of("field@product-channel", "field@product-channel")), fields()))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_SYSTEM_FIELD_DUPLICATE");
    assertThatThrownBy(() -> ProductSpecificationFieldListPolicy.normalizeSystemImport(
        new ProductSpecificationSystemFieldImportRequest(List.of("field@pipeline-status")), fields()))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_SYSTEM_FIELD_NOT_FOUND");
  }

  @Test
  void savesIncludeAndAdditionalConditionRulesSeparatelyAndEvaluatesVisibility() {
    ProductSpecificationConditionDraft draft = ProductSpecificationConditionRulePolicy.normalizeDraft(
        new ProductSpecificationConditionDraftRequest(
            List.of(new ProductSpecificationConditionRuleEdit("field@dti", "show-dti-for-retail", "parent-field-is-one-of",
                "field@product-channel", null, List.of("retail"), null, null)),
            List.of(new ProductSpecificationConditionRuleEdit("field@dti", "audit-dti-wholesale", "parent-field-is-not-one-of",
                "field@product-channel", "product-channel", List.of("wholesale"), null, null))),
        fields(), enumerations(), "admin-1");

    assertThat(draft.includeConditions()).hasSize(1);
    assertThat(draft.additionalConditions()).hasSize(1);
    assertThat(draft.includeConditions().get(0).enumTypeId()).isEqualTo("product-channel");
    assertThat(draft.includeConditions().get(0).readableExpression()).isEqualTo("field@product-channel is one of [retail]");

    ProductSpecificationFieldConditionEvaluationResponse visible = ProductSpecificationConditionRulePolicy.evaluateField(
        "field@dti", fields(), Optional.of(draft), Map.of("field@product-channel", "retail"), enumerations());
    ProductSpecificationFieldConditionEvaluationResponse hidden = ProductSpecificationConditionRulePolicy.evaluateField(
        "field@dti", fields(), Optional.of(draft), Map.of("field@product-channel", "wholesale"), enumerations());

    assertThat(visible.visible()).isTrue();
    assertThat(visible.status()).isEqualTo("APPLICABLE");
    assertThat(hidden.visible()).isFalse();
    assertThat(hidden.status()).isEqualTo("NOT_APPLICABLE");
  }

  @Test
  void supportsGeneratedConditionIdsBlankOperatorsAndEvaluatesAdditionalConditions() {
    ProductSpecificationConditionDraft draft = ProductSpecificationConditionRulePolicy.normalizeDraft(
        new ProductSpecificationConditionDraftRequest(
            List.of(new ProductSpecificationConditionRuleEdit("field@dti", null, "parent-field-is-not-blank",
                "field@product-channel", null, List.of(), null, null)),
            List.of(new ProductSpecificationConditionRuleEdit("field@dti", "fico-required", "parent-field-is-blank",
                "field@fico", null, List.of(), null, null))),
        fields(), enumerations(), "admin-1");

    assertThat(draft.includeConditions().get(0).conditionId()).isEqualTo("include-condition-1");
    assertThat(draft.includeConditions().get(0).readableExpression()).isEqualTo("field@product-channel is not blank");
    assertThat(draft.additionalConditions().get(0).readableExpression()).isEqualTo("field@fico is blank");

    ProductSpecificationFieldConditionEvaluationResponse visible = ProductSpecificationConditionRulePolicy.evaluateField(
        "field@dti", fields(), Optional.of(draft), Map.of("field@product-channel", "retail", "field@fico", ""), enumerations());
    ProductSpecificationFieldConditionEvaluationResponse hidden = ProductSpecificationConditionRulePolicy.evaluateField(
        "field@dti", fields(), Optional.of(draft), Map.of("field@product-channel", "retail", "field@fico", "720"), enumerations());

    assertThat(visible.visible()).isTrue();
    assertThat(visible.explanations()).extracting(ConditionEvaluationExplanation::conditionId)
        .containsExactly("include-condition-1", "fico-required");
    assertThat(hidden.visible()).isFalse();
    assertThat(hidden.status()).isEqualTo("NOT_APPLICABLE");
  }

  @Test
  void rejectsConditionRulesWithUnavailableParentFieldsOrEnumVariants() {
    assertThatThrownBy(() -> ProductSpecificationConditionRulePolicy.normalizeDraft(
        new ProductSpecificationConditionDraftRequest(
            List.of(new ProductSpecificationConditionRuleEdit("field@dti", "bad-parent", "parent-field-is-one-of",
                "field@pipeline-status", "product-channel", List.of("retail"), null, null)), List.of()),
        fields(), enumerations(), "admin-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_CONDITION_PARENT_FIELD_NOT_AVAILABLE");

    assertThatThrownBy(() -> ProductSpecificationConditionRulePolicy.normalizeDraft(
        new ProductSpecificationConditionDraftRequest(
            List.of(new ProductSpecificationConditionRuleEdit("field@dti", "bad-variant", "parent-field-is-one-of",
                "field@product-channel", null, List.of("broker"), null, null)), List.of()),
        fields(), enumerations(), "admin-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_CONDITION_VARIANT_NOT_AVAILABLE");

    assertThatThrownBy(() -> ProductSpecificationConditionRulePolicy.normalizeDraft(
        new ProductSpecificationConditionDraftRequest(
            List.of(new ProductSpecificationConditionRuleEdit("field@dti", "wrong-enum-type", "parent-field-is-one-of",
                "field@product-channel", "loan-purpose", List.of("cash-out"), null, null)), List.of()),
        fields(), enumerationsWithSecondType(), "admin-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_CONDITION_ENUM_TYPE_NOT_AVAILABLE");
  }

  @Test
  void validatePublishPreservesConcreteDependencyErrorsForStoredConditionDrafts() {
    ProductSpecificationConditionDraft draft = ProductSpecificationConditionRulePolicy.normalizeDraft(
        new ProductSpecificationConditionDraftRequest(
            List.of(new ProductSpecificationConditionRuleEdit("field@dti", "show-dti-for-retail", "parent-field-is-one-of",
                "field@product-channel", null, List.of("retail"), null, null)), List.of()),
        fields(), enumerations(), "admin-1");

    assertThatThrownBy(() -> ProductSpecificationConditionRulePolicy.validatePublish(
        fields().stream().filter(field -> !field.id().equals("field@product-channel")).toList(), Optional.of(draft), enumerations()))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_CONDITION_PARENT_FIELD_NOT_AVAILABLE");

    assertThatThrownBy(() -> ProductSpecificationConditionRulePolicy.validatePublish(fields(), Optional.of(draft), List.of()))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_CONDITION_ENUM_TYPE_NOT_AVAILABLE");

    assertThatThrownBy(() -> ProductSpecificationConditionRulePolicy.validatePublish(fields(), Optional.of(draft), enumerationsWithoutRetailVariant()))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_SPEC_CONDITION_VARIANT_NOT_AVAILABLE");
  }

  private static List<FieldMetadataResponse> fields() {
    return List.of(
        new FieldMetadataResponse("field@product-channel", "field@product-channel", "Product Channel", "Channel selector",
            "product", "enum", "productFields", Map.of(), "inherited", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@dti", "legacy-dti", "Debt-to-income Ratio", "Calculated ratio",
            "calculation", "number", "rawFields", Map.of("parentFieldId", "field@product-channel", "aliases", List.of("DTI"), "consumerMappings", List.of("pricing-formula-runtime")), "native", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@fico", null, "Representative Credit Score", "Credit score field",
            "creditApplication", "number", "creditApplicationFields", Map.of(), "inherited", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@aus-result", null, "AUS Result", "AUS finding",
            "aus", "string", "rawFields", Map.of(), "native", "ReferenceFormfields.json"),
        new FieldMetadataResponse("field@custom-note", null, "Custom Note", "Tenant custom note",
            "custom", "string", "rawFields", Map.of(), "custom", "tenant"),
        new FieldMetadataResponse("field@system-status", null, "System Status", "System managed status",
            "system", "string", "rawFields", Map.of(), "native", "system"),
        new FieldMetadataResponse("field@pipeline-status", null, "Pipeline Status", "Pipeline only",
            "pipeline", "string", "pipelineOnlyFields", Map.of(), "native", "ReferenceFormfields.json"));
  }

  private static List<EnumerationTypeResponse> enumerations() {
    return List.of(new EnumerationTypeResponse("product-channel", "Product Channel",
        List.of(new EnumerationVariantResponse("retail", "R", "Retail"),
            new EnumerationVariantResponse("wholesale", "W", "Wholesale")),
        "ReferenceFormfields.json", "system-default"));
  }

  private static List<EnumerationTypeResponse> enumerationsWithSecondType() {
    List<EnumerationTypeResponse> values = new ArrayList<>(enumerations());
    values.add(new EnumerationTypeResponse("loan-purpose", "Loan Purpose",
        List.of(new EnumerationVariantResponse("purchase", "P", "Purchase"),
            new EnumerationVariantResponse("cash-out", "C", "Cash-Out Refinance")),
        "ReferenceFormfields.json", "system-default"));
    return values;
  }

  private static List<EnumerationTypeResponse> enumerationsWithoutRetailVariant() {
    return List.of(new EnumerationTypeResponse("product-channel", "Product Channel",
        List.of(new EnumerationVariantResponse("wholesale", "W", "Wholesale")),
        "ReferenceFormfields.json", "system-default"));
  }
}
