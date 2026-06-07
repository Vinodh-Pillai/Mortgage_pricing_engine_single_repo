package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PricingAdvisoryPolicyTest {
  @Test
  void shouldSuppressAdviceWhenConfidenceBelowThreshold() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.pricingServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();

    PricingAdvisoryEvaluation evaluation =
        service
            .evaluatePricingAdvisory(
                AdvisoryTestFixtures.pricingEvaluationCommand("idem-low-confidence", 0.49, snapshotId, false, false, false),
                new FakeLocalModelAdapter())
            .value()
            .orElseThrow();

    assertEquals("SUPPRESSED", evaluation.status());
    assertEquals("ML_PRICING_ADVISORY_SUPPRESSED", evaluation.suppressionReason());
    assertTrue(service.advisoryCardsForTenant(AdvisoryTestFixtures.TENANT).isEmpty());
  }
}
