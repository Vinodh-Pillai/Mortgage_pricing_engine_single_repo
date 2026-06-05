package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.HighCostThresholdEvaluator.HighCostEvaluationRequest;
import com.wcpe.compliance.HighCostThresholdEvaluator.HighCostEvaluationResult;
import com.wcpe.compliance.HighCostThresholdEvaluator.HighCostScenarioInputs;
import com.wcpe.compliance.HighCostThresholdEvaluator.ThresholdConfigVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighCostThresholdEvaluatorTest {
  @Test
  void flagsConfiguredThresholdCrossing() {
    HighCostEvaluationResult result =
        HighCostThresholdEvaluator.evaluate(
            request(
                new HighCostScenarioInputs(new BigDecimal("6.1249"), null, null),
                List.of(config("APR_SPREAD", new BigDecimal("6.1200"), new BigDecimal("0.0100"), true))));

    assertEquals("high_cost", result.status());
    assertEquals("BLOCKING", result.advisorySeverity());
    assertEquals(List.of("APR_SPREAD_TEST"), result.triggeredTests());
    assertEquals("APR_SPREAD", result.ledger().get(0).inputRef());
    assertEquals(new BigDecimal("6.1249"), result.ledger().get(0).rawValue());
    assertEquals(new BigDecimal("6.1249"), result.ledger().get(0).roundedValue());
    assertEquals("HighCostEvaluationCompleted.v1", result.outboxEventTypes().get(0));
    assertEquals("HighCostThresholdConfigReferenced.v1", result.outboxEventTypes().get(1));
  }

  @Test
  void failsClosedWhenThresholdConfigMissing() {
    HighCostEvaluationResult result =
        HighCostThresholdEvaluator.evaluate(
            request(new HighCostScenarioInputs(new BigDecimal("6.1249"), null, null), List.of()));

    assertEquals("blocked_missing_config", result.status());
    assertEquals("BLOCKING", result.advisorySeverity());
    assertEquals(List.of("MISSING_THRESHOLD_CONFIG"), result.reasonCodes());
    assertEquals(List.of("HighCostEvaluationFailedClosed.v1"), result.outboxEventTypes());
  }

  @Test
  void recordsFormulaAndRounding() {
    HighCostEvaluationResult result =
        HighCostThresholdEvaluator.evaluate(
            request(
                new HighCostScenarioInputs(new BigDecimal("6.12494"), null, null),
                List.of(config("APR_SPREAD", new BigDecimal("6.1300"), new BigDecimal("0.0100"), false))));

    assertEquals("near_threshold", result.status());
    assertEquals("WARNING", result.advisorySeverity());
    assertEquals(List.of("APR_SPREAD_TEST:band-configured"), result.proximityBands());
    assertEquals("formula-configured", result.ledger().get(0).formulaRef());
    assertEquals("threshold-configured", result.ledger().get(0).thresholdRef());
    assertEquals(new BigDecimal("6.1249"), result.ledger().get(0).roundedValue());
  }

  @Test
  void resultHashIsDeterministicForReplayEvidence() {
    HighCostEvaluationRequest request =
        request(
            new HighCostScenarioInputs(new BigDecimal("6.1249"), null, null),
            List.of(config("APR_SPREAD", new BigDecimal("6.1200"), new BigDecimal("0.0100"), true)));

    HighCostEvaluationResult first = HighCostThresholdEvaluator.evaluate(request);
    HighCostEvaluationResult second = HighCostThresholdEvaluator.evaluate(request);

    assertEquals(first.resultHash(), second.resultHash());
    assertEquals(first, HighCostThresholdEvaluator.replay(request, first.resultHash()));
    assertFalse(first.resultHash().contains("borrower"));
    assertTrue(first.auditRef().startsWith("high-cost-audit:sha256:"));
  }

  @Test
  void replayHashMismatchFailsClosed() {
    HighCostEvaluationResult result =
        HighCostThresholdEvaluator.replay(
            request(
                new HighCostScenarioInputs(new BigDecimal("6.1249"), null, null),
                List.of(config("APR_SPREAD", new BigDecimal("6.1200"), new BigDecimal("0.0100"), true))),
            "sha256:wrong");

    assertEquals("POLICY_NOT_SATISFIED", result.status());
    assertTrue(result.reasonCodes().contains("REPLAY_HASH_MISMATCH"));
    assertEquals(List.of("HighCostEvaluationFailedClosed.v1"), result.outboxEventTypes());
  }

  @Test
  void ambiguousRulePackVersionFailsClosed() {
    ThresholdConfigVersion first =
        config("APR_SPREAD", new BigDecimal("6.1200"), new BigDecimal("0.0100"), true);
    ThresholdConfigVersion second =
        config("APR_SPREAD", new BigDecimal("6.1300"), new BigDecimal("0.0100"), true);

    HighCostEvaluationResult result =
        HighCostThresholdEvaluator.evaluate(
            request(new HighCostScenarioInputs(new BigDecimal("6.1249"), null, null), List.of(first, second)));

    assertEquals("blocked_missing_config", result.status());
    assertEquals(List.of("AMBIGUOUS_RULE_PACK_VERSION:APR_SPREAD_TEST"), result.reasonCodes());
  }

  @Test
  void missingInputFailsClosed() {
    HighCostEvaluationResult result =
        HighCostThresholdEvaluator.evaluate(
            request(
                new HighCostScenarioInputs(null, null, null),
                List.of(config("APR_SPREAD", new BigDecimal("6.1200"), new BigDecimal("0.0100"), true))));

    assertEquals("blocked_missing_config", result.status());
    assertEquals(List.of("MISSING_APR_SPREAD_INPUT"), result.reasonCodes());
  }

  @Test
  void malformedRequestReturnsProjectStandardValidationErrorShape() {
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> HighCostThresholdEvaluator.evaluate(null));

    ComplianceShellValidationError error = assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertEquals(List.of("request"), error.getDetails());
  }

  private static HighCostEvaluationRequest request(
      HighCostScenarioInputs inputs, List<ThresholdConfigVersion> configs) {
    return new HighCostEvaluationRequest(
        "tenant-a",
        "request-123",
        "pricing-service",
        "scenario-456",
        "quote-789",
        LocalDate.parse("2026-06-15"),
        "CONVENTIONAL",
        "RETAIL",
        "CA",
        "FIRST",
        "PRIMARY",
        inputs,
        configs,
        "idem-123",
        "correlation-456");
  }

  private static ThresholdConfigVersion config(
      String inputType, BigDecimal threshold, BigDecimal proximityBand, boolean blocking) {
    return new ThresholdConfigVersion(
        "tenant-a",
        "config-version-2026-a",
        inputType + "_TEST",
        "FEDERAL",
        "HIGH_COST",
        7,
        LocalDate.parse("2026-01-01"),
        LocalDate.parse("2026-12-31"),
        "CONVENTIONAL",
        "RETAIL",
        "CA",
        "FIRST",
        "PRIMARY",
        inputType,
        "formula-configured",
        threshold,
        ">=",
        4,
        RoundingMode.HALF_UP,
        proximityBand,
        "band-configured",
        blocking,
        "threshold-configured",
        "configured-citation",
        null);
  }
}
