package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MlEligibilityRiskAdvisorySuppressedEventTest {
  @Test
  void shouldRecordBoundaryBlockReason() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.eligibilityRiskServiceWithProhibitedProxySnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();

    EligibilityRiskAdvisoryEvaluation evaluation =
        service
            .evaluateEligibilityRiskAdvisory(
                AdvisoryTestFixtures.eligibilityRiskCommand("idem-eligibility-proxy", snapshotId), new FakeLocalModelAdapter())
            .value()
            .orElseThrow();
    MlAdvisoryOutboxEvent event =
        service.outboxEvents().stream()
            .filter(candidate -> candidate.eventType().equals(MlAdvisoryControlService.ML_ELIGIBILITY_RISK_ADVISORY_SUPPRESSED_EVENT))
            .findFirst()
            .orElseThrow();

    assertEquals("SUPPRESSED", evaluation.status());
    assertEquals("ML_UNDERWRITING_BOUNDARY_BLOCKED", event.payload().get("reason"));
    assertEquals("true", event.payload().get("notAdverseAction"));
    assertEquals("true", event.payload().get("eligibilityUnchanged"));
  }
}
