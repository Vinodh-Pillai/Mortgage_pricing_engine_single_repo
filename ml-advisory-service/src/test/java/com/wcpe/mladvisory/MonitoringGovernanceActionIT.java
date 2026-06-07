package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MonitoringGovernanceActionIT {
  @Test
  void shouldSuspendAdvisoryOnlyAndNotPricing() {
    MlAdvisoryControlService service = new MlAdvisoryControlService();
    MonitoringAlert alert =
        service.startMonitoringRun(DriftMonitoringTestFixtures.driftRunCommand("idem-monitoring-governance"))
            .value()
            .orElseThrow()
            .alerts()
            .get(0);

    MlAdvisoryResult<MonitoringAlert> disposition =
        service.requestAdvisorySuspensionReview(
            new MonitoringDispositionCommand(
                AdvisoryTestFixtures.TENANT,
                alert.alertId(),
                "model-risk-reviewer-1",
                Set.of(MlAdvisoryControlService.MONITORING_REVIEWER_ROLE),
                "Critical configured drift indicator requires advisory suppression review.",
                "MRM-DRIFT-PII-14-S10",
                "corr-monitoring-disposition-PII-14-S10"));

    assertTrue(disposition.valid());
    assertTrue(disposition.value().orElseThrow().advisoryOnly());
    assertEquals("SUSPENSION_REVIEW_REQUESTED", disposition.value().orElseThrow().status());
    assertTrue(service.outboxEvents().stream().noneMatch(event -> event.payload().containsKey("pricingChanged")));
  }
}
