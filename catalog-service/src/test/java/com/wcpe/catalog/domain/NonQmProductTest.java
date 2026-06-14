package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
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
}
