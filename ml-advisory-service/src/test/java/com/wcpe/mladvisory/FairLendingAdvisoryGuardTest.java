package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FairLendingAdvisoryGuardTest {
  @Test
  void shouldSuppressProhibitedProxyDrivenAdvice() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.pricingServiceWithProhibitedProxySnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();

    PricingAdvisoryEvaluation evaluation =
        service
            .evaluatePricingAdvisory(
                AdvisoryTestFixtures.pricingEvaluationCommand("idem-proxy-suppressed", 0.82, snapshotId, false, false, false),
                new FakeLocalModelAdapter())
            .value()
            .orElseThrow();

    assertEquals("SUPPRESSED", evaluation.status());
    assertEquals("FAIR_LENDING_GUARD_SUPPRESSED", evaluation.suppressionReason());
    assertTrue(service.advisoryCardsForTenant(AdvisoryTestFixtures.TENANT).isEmpty());
  }
}
