package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PricingAdvisoryServiceTest {
  @Test
  void shouldNotMutateDeterministicPricingResult() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.pricingServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();

    PricingAdvisoryEvaluation evaluation =
        service
            .evaluatePricingAdvisory(
                AdvisoryTestFixtures.pricingEvaluationCommand("idem-pricing-service", 0.82, snapshotId, false, false, false),
                new FakeLocalModelAdapter())
            .value()
            .orElseThrow();

    assertTrue(evaluation.deterministicPricingUnchanged());
    assertFalse(evaluation.authoritative());
    assertFalse(service.advisoryCardsForTenant(AdvisoryTestFixtures.TENANT).get(0).authoritative());
  }
}
