package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExplanationSafetyPolicyTest {
  @Test
  void shouldSuppressProtectedAndProhibitedDrivers() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(protectedFeatureCommand()).value().orElseThrow();

    AdvisoryExplanation explanation =
        service
            .getAdvisoryExplanation(
                AdvisoryTestFixtures.TENANT,
                card.advisoryId(),
                "model-risk-reviewer-1",
                Set.of(MlAdvisoryControlService.EXPLANATION_READ_ROLE),
                "pricing-workbench",
                "corr-explanation-safety")
            .value()
            .orElseThrow();

    ExplanationDriver driver = explanation.drivers().get(0);
    assertTrue(driver.suppressed());
    assertEquals("Suppressed governed feature", driver.featureLabel());
    assertEquals("SAFE_FEATURE_POLICY_SUPPRESSED", driver.suppressionReason());
    assertFalse(driver.toString().contains("720"));
  }

  @Test
  void shouldRedactUnregisteredReasonCodeAndDirection() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(unsafeUnsuppressedFeatureCommand()).value().orElseThrow();

    AdvisoryExplanation explanation =
        service
            .getAdvisoryExplanation(
                AdvisoryTestFixtures.TENANT,
                card.advisoryId(),
                "model-risk-reviewer-1",
                Set.of(MlAdvisoryControlService.EXPLANATION_READ_ROLE),
                "pricing-workbench",
                "corr-explanation-unsafe-unsuppressed")
            .value()
            .orElseThrow();

    ExplanationDriver driver = explanation.drivers().get(0);
    assertFalse(driver.suppressed());
    assertEquals("Configured advisory signal", driver.featureLabel());
    assertEquals("ADVISORY_SIGNAL", driver.direction());
    assertFalse(driver.toString().contains("credit_score_720"));
    assertFalse(driver.toString().contains("raw upward impact 720"));
  }

  static GenerateAdvisoryCommand protectedFeatureCommand() {
    return new GenerateAdvisoryCommand(
        AdvisoryTestFixtures.TENANT,
        "idem-protected-explanation",
        "pricing-analyst-1",
        "scenario-123",
        "pricing-result-456",
        "snapshot-789",
        "model-version-approved-1",
        AdvisoryType.PRICING,
        "Review advisory explanation before final pricing decision",
        0.76,
        "VISIBLE",
        AdvisoryTestFixtures.NOW,
        AdvisoryTestFixtures.NOW.plusSeconds(3600),
        List.of(
            new AdvisoryReason(
                "CREDIT_PROFILE_SIGNAL",
                1,
                "raw credit score 720 increased the model signal",
                "INFORMATIONAL",
                "feature-hash:credit-score",
                "PROTECTED")),
        Map.of("snapshot", "snapshot-789", "pricingResult", "pricing-result-456"),
        AdvisoryDisplayPolicy.configured(0.50),
        "corr-protected-explanation");
  }

  static GenerateAdvisoryCommand unsafeUnsuppressedFeatureCommand() {
    return new GenerateAdvisoryCommand(
        AdvisoryTestFixtures.TENANT,
        "idem-unsafe-unsuppressed-explanation",
        "pricing-analyst-1",
        "scenario-123",
        "pricing-result-456",
        "snapshot-789",
        "model-version-approved-1",
        AdvisoryType.PRICING,
        "Review advisory explanation before final pricing decision",
        0.76,
        "VISIBLE",
        AdvisoryTestFixtures.NOW,
        AdvisoryTestFixtures.NOW.plusSeconds(3600),
        List.of(
            new AdvisoryReason(
                "credit_score_720",
                1,
                "raw credit score 720 increased the model signal",
                "raw upward impact 720",
                "feature-hash:credit-score",
                "NON_PUBLIC")),
        Map.of("snapshot", "snapshot-789", "pricingResult", "pricing-result-456"),
        AdvisoryDisplayPolicy.configured(0.50),
        "corr-unsafe-unsuppressed-explanation");
  }
}
