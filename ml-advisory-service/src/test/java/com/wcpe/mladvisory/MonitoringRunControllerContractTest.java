package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class MonitoringRunControllerContractTest {
  @Test
  void shouldReturnInsufficientDataStatus() {
    DriftMonitoringController controller = new DriftMonitoringController(new MlAdvisoryControlService());

    ResponseEntity<?> response =
        controller.startRun(
            AdvisoryTestFixtures.TENANT,
            new DriftMonitoringController.MonitoringRunRequest(
                "idem-monitoring-insufficient",
                "model-risk-admin-1",
                DriftMonitoringTestFixtures.MODEL_VERSION_ID,
                AdvisoryType.PRICING,
                DriftMonitoringTestFixtures.WINDOW_START,
                DriftMonitoringTestFixtures.WINDOW_END,
                "tenant-policy:ml-monitoring:v1",
                "ml-advisory-feature-schema-v1",
                "lineage:feature-snapshots:daily-aggregate",
                10,
                100,
                List.of(DriftMonitoringTestFixtures.criticalDriftMetric()),
                "corr-monitoring-contract-PII-14-S10"));

    assertEquals(422, response.getStatusCode().value());
    assertEquals(MlAdvisoryControlService.START_MONITORING_RUN_ENDPOINT,
        "POST /api/v1/tenants/{tenantId}/ml-advisory/monitoring-runs");
  }
}
