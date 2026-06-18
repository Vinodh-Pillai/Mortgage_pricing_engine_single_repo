package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NonQmProductTest {
  private final NonQmProductSchemaRegistry schemas = new NonQmProductSchemaRegistry();

  @Test
  void dscrAttributesValid() {
    NonQmValidationResult result = schemas.validate("DSCR", Map.of(
        "dscrRatio", new BigDecimal("1.25"),
        "propertyType", "SFR",
        "loanPurpose", "PURCHASE",
        "maxLtv", new BigDecimal("80.00"),
        "minFico", 680,
        "interestOnly", true));

    assertThat(result.valid()).isTrue();
    assertThat(schemas.schema("DSCR").productFamily()).isEqualTo("NON_QM");
  }

  @Test
  void bankStatementAttributesValid() {
    NonQmValidationResult result = schemas.validate("BANK_STATEMENT", Map.of(
        "monthsOfStatements", 12,
        "statementType", "BUSINESS",
        "expenseRatio", new BigDecimal("0.50"),
        "depositTrend", "STABLE",
        "businessType", "SELF_EMPLOYED",
        "maxLtv", new BigDecimal("75.00"),
        "minFico", 660));

    assertThat(result.valid()).isTrue();
  }

  @Test
  void assetDepletionAttributesValid() {
    NonQmValidationResult result = schemas.validate("ASSET_DEPLETION", Map.of(
        "assetType", "INVESTMENT",
        "depletionRate", new BigDecimal("0.05"),
        "minAssetBalance", new BigDecimal("250000.00"),
        "maxLtv", new BigDecimal("70.00"),
        "seasoningMonths", 6));

    assertThat(result.valid()).isTrue();
  }

  @Test
  void noRatioForeignNationalItinAnd1099TypesRegistered() {
    assertThat(schemas.supportedProductTypes())
        .containsExactlyInAnyOrder("DSCR", "BANK_STATEMENT", "ASSET_DEPLETION", "NO_RATIO", "FOREIGN_NATIONAL", "ITIN", "1099_ONLY");
    assertThat(schemas.schema("NO_RATIO").channels()).containsExactly("RETAIL", "CORRESPONDENT", "WHOLESALE");
    assertThat(schemas.schema("FOREIGN_NATIONAL").productFamily()).isEqualTo("NON_QM");
    assertThat(schemas.schema("ITIN").productFamily()).isEqualTo("NON_QM");
    assertThat(schemas.validate("1099_ONLY", Map.of("incomeDocType", "1099_ONLY", "yearsInBusiness", 2, "taxReturnWaiver", true)).valid()).isTrue();
  }

  @Test
  void schemaValidationRejectsInvalid() {
    NonQmValidationResult missing = schemas.validate("DSCR", Map.of("propertyType", "SFR"));
    NonQmValidationResult wrongType = schemas.validate("DSCR", Map.of(
        "dscrRatio", "not-a-number",
        "propertyType", "SFR",
        "loanPurpose", "PURCHASE",
        "maxLtv", new BigDecimal("80.00"),
        "minFico", 680));

    assertThat(missing.valid()).isFalse();
    assertThat(missing.errors()).extracting(NonQmValidationError::field).contains("dscrRatio", "loanPurpose", "maxLtv", "minFico");
    assertThat(wrongType.valid()).isFalse();
    assertThat(wrongType.errors()).extracting(NonQmValidationError::code).contains("TYPE_MISMATCH");
  }

  @Test
  void investorChannelMapping() {
    assertThatCode(() -> schemas.requireAllowedChannel("RETAIL")).doesNotThrowAnyException();
    assertThatCode(() -> schemas.requireAllowedChannel("CORRESPONDENT")).doesNotThrowAnyException();
    assertThatCode(() -> schemas.requireAllowedChannel("WHOLESALE")).doesNotThrowAnyException();
    assertThatThrownBy(() -> schemas.requireAllowedChannel("TPO"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("NON_QM_CHANNEL_NOT_SUPPORTED");
  }

  @Test
  void productEligibilityMetadataIsNormalizedIntoCompleteReadinessForProductDetailResponses() throws Exception {
    Object draft = validate(new NonQmProductRequest("DSCR_30YR", "DSCR 30 Year", "DSCR", dscrAttributes(), Map.of(),
        new ProductEligibilityMetadata(
            List.of(new EligibilityRuleRef("loanpass:dscr:v3", "dscr", "loanpass", 3, Instant.parse("2026-01-01T00:00:00Z"), null)),
            List.of(new EligibilityFieldMetadata("nonQm.dscr.ratio", "loanpass:field:DSCR_RATIO", "DSCR ratio is required for eligibility.")),
            List.of(new EligibilityFieldMetadata("borrower.fico", "loanpass:field:FICO", "FICO may be required by configured rule refs.")),
            List.of("loanpass:explain:eligibility"), null, null),
        List.of(new NonQmInvestorChannelMapping("INV-A", "RETAIL", "LP-DSCR", "ACTIVE", 1, LocalDate.of(2026, 1, 1), null)),
        "ACTIVE"));

    Method pricingMetadataMethod = draft.getClass().getDeclaredMethod("pricingMetadata");
    pricingMetadataMethod.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> pricingMetadata = (Map<String, Object>) pricingMetadataMethod.invoke(draft);
    ProductEligibilityMetadata eligibility = (ProductEligibilityMetadata) pricingMetadata.get("eligibility");

    assertThat(eligibility.readinessStatus()).isEqualTo("COMPLETE");
    assertThat(eligibility.ruleRefs()).extracting(EligibilityRuleRef::sourceSystem).containsExactly("LOANPASS");
    assertThat(eligibility.requiredFields()).extracting(EligibilityFieldMetadata::configRef).contains("loanpass:field:DSCR_RATIO");
  }

  @Test
  void invalidEligibilityRuleRefsFailClosedBeforeProductMutation() throws Exception {
    NonQmProductRequest request = new NonQmProductRequest("DSCR_30YR", "DSCR 30 Year", "DSCR", dscrAttributes(), Map.of(),
        new ProductEligibilityMetadata(
            List.of(new EligibilityRuleRef("loanpass:dscr", "dscr", "loanpass", null, Instant.parse("2026-01-01T00:00:00Z"), null)),
            List.of(new EligibilityFieldMetadata("nonQm.dscr.ratio", "loanpass:field:DSCR_RATIO", "DSCR ratio is required for eligibility.")),
            List.of(), List.of(), null, null),
        List.of(new NonQmInvestorChannelMapping("INV-A", "RETAIL", "LP-DSCR", "ACTIVE", 1, LocalDate.of(2026, 1, 1), null)),
        "ACTIVE");

    assertThatThrownBy(() -> validate(request))
        .hasRootCauseInstanceOf(CatalogException.class)
        .hasRootCauseMessage("ELIGIBILITY_RULE_VERSION_REQUIRED");
  }

  @Test
  void createValidationAcceptsTopLevelEligibilityWhenPricingMetadataIsNull() throws Exception {
    Object draft = validate(new NonQmProductRequest("DSCR_30YR", "DSCR 30 Year", "DSCR", dscrAttributes(), null,
        completeEligibility(), investorMappings(), "ACTIVE"));

    Method pricingMetadataMethod = draft.getClass().getDeclaredMethod("pricingMetadata");
    pricingMetadataMethod.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> pricingMetadata = (Map<String, Object>) pricingMetadataMethod.invoke(draft);
    ProductEligibilityMetadata eligibility = (ProductEligibilityMetadata) pricingMetadata.get("eligibility");

    assertThat(eligibility.readinessStatus()).isEqualTo("COMPLETE");
    assertThat(eligibility.ruleRefs()).extracting(EligibilityRuleRef::ruleRef).containsExactly("loanpass:dscr:v3");
  }

  @Test
  void updatePreservesTopLevelEligibilityWhenPricingMetadataIsNull() throws Exception {
    NonQmProductRequest update = updateRequest("DSCR_30YR", new NonQmProductRequest("IGNORED", "DSCR 30 Year", "DSCR",
        dscrAttributes(), null, completeEligibility(), investorMappings(), "ACTIVE"));

    Object draft = validate(update);
    Method pricingMetadataMethod = draft.getClass().getDeclaredMethod("pricingMetadata");
    pricingMetadataMethod.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> pricingMetadata = (Map<String, Object>) pricingMetadataMethod.invoke(draft);
    ProductEligibilityMetadata eligibility = (ProductEligibilityMetadata) pricingMetadata.get("eligibility");

    assertThat(update.productCode()).isEqualTo("DSCR_30YR");
    assertThat(eligibility.readinessStatus()).isEqualTo("COMPLETE");
    assertThat(eligibility.ruleRefs()).extracting(EligibilityRuleRef::ruleRef).containsExactly("loanpass:dscr:v3");
  }

  private Object validate(NonQmProductRequest request) throws Exception {
    NonQmProductRepository repository = new NonQmProductRepository(null, new ObjectMapper(), schemas);
    Method validate = NonQmProductRepository.class.getDeclaredMethod("validateRequest", NonQmProductRequest.class);
    validate.setAccessible(true);
    return validate.invoke(repository, request);
  }

  private NonQmProductRequest updateRequest(String code, NonQmProductRequest request) throws Exception {
    NonQmProductRepository repository = new NonQmProductRepository(null, new ObjectMapper(), schemas);
    Method updateRequest = NonQmProductRepository.class.getDeclaredMethod("updateRequest", String.class, NonQmProductRequest.class);
    updateRequest.setAccessible(true);
    return (NonQmProductRequest) updateRequest.invoke(repository, code, request);
  }

  private static Map<String, Object> dscrAttributes() {
    return Map.of(
        "dscrRatio", new BigDecimal("1.25"),
        "propertyType", "SFR",
        "loanPurpose", "PURCHASE",
        "maxLtv", new BigDecimal("80.00"),
        "minFico", 680,
        "interestOnly", false);
  }

  private static ProductEligibilityMetadata completeEligibility() {
    return new ProductEligibilityMetadata(
        List.of(new EligibilityRuleRef("loanpass:dscr:v3", "dscr", "loanpass", 3, null, null)),
        List.of(new EligibilityFieldMetadata("nonQm.dscr.ratio", "loanpass:field:DSCR_RATIO", "DSCR ratio is required for eligibility.")),
        List.of(), List.of("loanpass:explain:eligibility"), null, null);
  }

  private static List<NonQmInvestorChannelMapping> investorMappings() {
    return List.of(new NonQmInvestorChannelMapping("INV-A", "RETAIL", "LP-DSCR", "ACTIVE", 1, LocalDate.of(2026, 1, 1), null));
  }

}
