package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DriftMonitoringServiceTest {
  @Test
  void shouldFlagPopulationShiftAboveThreshold() {
    MlAdvisoryControlService service = new MlAdvisoryControlService();

    MlAdvisoryResult<ModelMonitoringRun> result =
        service.startMonitoringRun(DriftMonitoringTestFixtures.driftRunCommand("idem-monitoring-drift"));

    assertTrue(result.valid());
    ModelMonitoringRun run = result.value().orElseThrow();
    assertEquals("COMPLETED_WITH_ALERTS", run.status());
    assertEquals("CRITICAL", run.highestSeverity());
    assertEquals(1, run.alerts().size());
    assertEquals(MlAdvisoryControlService.ML_MODEL_DRIFT_ALERT_RAISED_EVENT, run.alerts().get(0).eventRef().isBlank() ? "" : service.outboxEvents().stream()
        .filter(event -> event.eventId().equals(run.alerts().get(0).eventRef()))
        .findFirst()
        .orElseThrow()
        .eventType());
  }
}
