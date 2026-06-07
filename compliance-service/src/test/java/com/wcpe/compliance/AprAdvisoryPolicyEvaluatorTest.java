package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.AprAdvisoryPolicyEvaluator.AprAdvisoryConfigVersion;
import com.wcpe.compliance.AprAdvisoryPolicyEvaluator.AprAdvisoryRequest;
import com.wcpe.compliance.AprAdvisoryPolicyEvaluator.AprAdvisoryResult;
import com.wcpe.compliance.AprAdvisoryPolicyEvaluator.FeeTreatmentConfig;
import com.wcpe.compliance.AprAdvisoryPolicyEvaluator.FinanceChargeComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AprAdvisoryPolicyEvaluatorTest {
  @Test
  void usesConfiguredWarningBand() {
    AprAdvisoryResult result =
        AprAdvisoryPolicyEvaluator.evaluate(
            request(
                new BigDecimal("6.75241"),
                List.of(fee("ORIGINATION", "1250.225", "pricing-fee-1")),
                List.of(config(new BigDecimal("0.5000"), false, standardTreatments()))));

    assertEquals("warning", result.status());
    assertEquals(new BigDecimal("6.7524"), result.apr());
    assertEquals(new BigDecimal("6.0000"), result.noteRate());
    assertEquals(new BigDecimal("0.7524"), result.spread());
    assertEquals(List.of("apr-warning-band-configured"), result.warnings());
    assertEquals("formula-configured", result.ledger().get(0).formulaRef());
    assertEquals("HALF_UP", result.ledger().get(0).roundingMode());
    assertEquals(List.of("AprAdvisoryCompleted.v1", "AprAdvisoryConfigReferenced.v1"), result.outboxEventTypes());
  }

  @Test
  void recordsFinanceChargeTreatmentAndRoundingMode() {
    AprAdvisoryResult result =
        AprAdvisoryPolicyEvaluator.evaluate(
            request(
                new BigDecimal("6.25004"),
                List.of(
                    fee("APPRAISAL", "450.129", "appraisal-fee"),
                    fee("ORIGINATION", "1250.225", "origination-fee")),
                List.of(config(new BigDecimal("1.0000"), false, standardTreatments()))));

    assertEquals("clear", result.status());
    assertEquals(new BigDecimal("1250.23"), result.includedFinanceChargeTotal());
    assertEquals("APPRAISAL", result.includedFinanceCharges().get(0).componentCode());
    assertEquals(new BigDecimal("450.13"), result.includedFinanceCharges().get(0).amount());
    assertEquals(false, result.includedFinanceCharges().get(0).included());
    assertEquals("rule-exclude-third-party", result.includedFinanceCharges().get(0).inclusionRuleRef());
    assertEquals("ORIGINATION", result.includedFinanceCharges().get(1).componentCode());
    assertEquals(true, result.includedFinanceCharges().get(1).included());
    assertEquals("APR_ADVISORY_CLEAR", result.reasonCodes().get(0));
  }

  @Test
  void missingFormulaConfigFailsClosed() {
    AprAdvisoryResult result =
        AprAdvisoryPolicyEvaluator.evaluate(
            request(
                new BigDecimal("6.75241"),
                List.of(fee("ORIGINATION", "1250.225", "pricing-fee-1")),
                List.of()));

    assertEquals("blocked_missing_config", result.status());
    assertEquals(List.of("MISSING_APR_FORMULA_CONFIG"), result.reasonCodes());
    assertEquals(List.of("AprAdvisoryFailedClosed.v1"), result.outboxEventTypes());
  }

  @Test
  void rejectsAmbiguousFeeTreatment() {
    List<FeeTreatmentConfig> treatments =
        List.of(
            new FeeTreatmentConfig("ORIGINATION", true, "rule-include-origination"),
            new FeeTreatmentConfig("ORIGINATION", false, "rule-conflict"));

    AprAdvisoryResult result =
        AprAdvisoryPolicyEvaluator.evaluate(
            request(
                new BigDecimal("6.75241"),
                List.of(fee("ORIGINATION", "1250.225", "pricing-fee-1")),
                List.of(config(new BigDecimal("0.5000"), false, treatments))));

    assertEquals("blocked_missing_config", result.status());
    assertEquals(List.of("AMBIGUOUS_FEE_TREATMENT:ORIGINATION"), result.reasonCodes());
  }

  @Test
  void invalidFeeComponentFailsClosed() {
    AprAdvisoryResult result =
        AprAdvisoryPolicyEvaluator.evaluate(
            request(
                new BigDecimal("6.75241"),
                List.of(fee("UNKNOWN", "1250.225", "pricing-fee-1")),
                List.of(config(new BigDecimal("0.5000"), false, standardTreatments()))));

    assertEquals("blocked_missing_config", result.status());
    assertEquals(List.of("INVALID_FEE_COMPONENT:UNKNOWN"), result.reasonCodes());
  }

  @Test
  void missingPaymentStreamFailsClosed() {
    AprAdvisoryRequest request =
        new AprAdvisoryRequest(
            "tenant-a",
            "request-123",
            "pricing-service",
            "scenario-456",
            "quote-789",
            LocalDate.parse("2026-06-15"),
            "CONVENTIONAL",
            "RETAIL",
            "CA",
            null,
            new BigDecimal("6.0000"),
            new BigDecimal("6.75241"),
            List.of(fee("ORIGINATION", "1250.225", "pricing-fee-1")),
            List.of(config(new BigDecimal("0.5000"), false, standardTreatments())),
            "idem-123",
            "correlation-456");

    AprAdvisoryResult result = AprAdvisoryPolicyEvaluator.evaluate(request);

    assertEquals("blocked_missing_config", result.status());
    assertEquals(List.of("MISSING_PAYMENT_STREAM"), result.reasonCodes());
  }

  @Test
  void replayHashIsDeterministic() {
    AprAdvisoryRequest request =
        request(
            new BigDecimal("6.75241"),
            List.of(fee("ORIGINATION", "1250.225", "pricing-fee-1")),
            List.of(config(new BigDecimal("0.5000"), false, standardTreatments())));

    AprAdvisoryResult first = AprAdvisoryPolicyEvaluator.evaluate(request);
    AprAdvisoryResult second = AprAdvisoryPolicyEvaluator.evaluate(request);

    assertEquals(first.resultHash(), second.resultHash());
    assertEquals(first, AprAdvisoryPolicyEvaluator.replay(request, first.resultHash()));
    assertFalse(first.resultHash().contains("borrower"));
    assertTrue(first.auditRef().startsWith("apr-advisory-audit:sha256:"));
  }

  @Test
  void replayHashMismatchFailsClosed() {
    AprAdvisoryResult result =
        AprAdvisoryPolicyEvaluator.replay(
            request(
                new BigDecimal("6.75241"),
                List.of(fee("ORIGINATION", "1250.225", "pricing-fee-1")),
                List.of(config(new BigDecimal("0.5000"), false, standardTreatments()))),
            "sha256:wrong");

    assertEquals("POLICY_NOT_SATISFIED", result.status());
    assertTrue(result.reasonCodes().contains("APR_REPLAY_HASH_MISMATCH"));
    assertEquals(List.of("AprAdvisoryFailedClosed.v1"), result.outboxEventTypes());
  }

  @Test
  void malformedRequestReturnsProjectStandardValidationErrorShape() {
    RuntimeException thrown = assertThrows(RuntimeException.class, () -> AprAdvisoryPolicyEvaluator.evaluate(null));

    ComplianceShellValidationError error = assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertEquals(List.of("request"), error.getDetails());
  }

  private static AprAdvisoryRequest request(
      BigDecimal apr,
      List<FinanceChargeComponent> components,
      List<AprAdvisoryConfigVersion> configs) {
    return new AprAdvisoryRequest(
        "tenant-a",
        "request-123",
        "pricing-service",
        "scenario-456",
        "quote-789",
        LocalDate.parse("2026-06-15"),
        "CONVENTIONAL",
        "RETAIL",
        "CA",
        "payment-stream-123",
        new BigDecimal("6.0000"),
        apr,
        components,
        configs,
        "idem-123",
        "correlation-456");
  }

  private static FinanceChargeComponent fee(String componentCode, String amount, String sourceRef) {
    return new FinanceChargeComponent(componentCode, new BigDecimal(amount), sourceRef, "FINANCIAL_CONFIDENTIAL");
  }

  private static AprAdvisoryConfigVersion config(
      BigDecimal warningBandValue, boolean blockingWhenWarning, List<FeeTreatmentConfig> treatments) {
    return new AprAdvisoryConfigVersion(
        "tenant-a",
        "apr-config-version-2026-a",
        LocalDate.parse("2026-01-01"),
        LocalDate.parse("2026-12-31"),
        "CONVENTIONAL",
        "RETAIL",
        "CA",
        "formula-configured",
        "tolerance-configured",
        "rounding-configured",
        4,
        2,
        RoundingMode.HALF_UP,
        warningBandValue,
        "apr-warning-band-configured",
        blockingWhenWarning,
        "APR_SPREAD_WARNING",
        "APR_ADVISORY_CLEAR",
        treatments);
  }

  private static List<FeeTreatmentConfig> standardTreatments() {
    return List.of(
        new FeeTreatmentConfig("ORIGINATION", true, "rule-include-origination"),
        new FeeTreatmentConfig("APPRAISAL", false, "rule-exclude-third-party"));
  }
}
