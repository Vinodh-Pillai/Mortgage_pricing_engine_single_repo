package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MonitoringAlertPolicyTest {
  @Test
  void shouldRecommendAdvisorySuspensionForCriticalDrift() {
    MlAdvisoryControlService service = new MlAdvisoryControlService();

    MonitoringAlert alert =
        service.startMonitoringRun(DriftMonitoringTestFixtures.driftRunCommand("idem-monitoring-policy"))
            .value()
            .orElseThrow()
            .alerts()
            .get(0);

    assertEquals("REQUEST_ADVISORY_SUSPENSION_REVIEW", alert.recommendedAction());
    assertTrue(alert.advisoryOnly());
  }
}
