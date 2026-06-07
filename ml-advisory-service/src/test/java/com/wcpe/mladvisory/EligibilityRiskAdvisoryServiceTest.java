package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EligibilityRiskAdvisoryServiceTest {
  @Test
  void shouldNotMutateEligibilityDecision() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.eligibilityRiskServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();

    EligibilityRiskAdvisoryEvaluation evaluation =
        service
            .evaluateEligibilityRiskAdvisory(
                AdvisoryTestFixtures.eligibilityRiskCommand("idem-eligibility-service", snapshotId), new FakeLocalModelAdapter())
            .value()
            .orElseThrow();

    assertFalse(evaluation.authoritative());
    assertTrue(evaluation.notAdverseAction());
    assertTrue(evaluation.eligibilityUnchanged());
    assertFalse(service.advisoryCardsForTenant(AdvisoryTestFixtures.TENANT).get(0).authoritative());
  }
}
