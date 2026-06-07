package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MlModelDriftAlertRaisedEventTest {
  @Test
  void shouldExcludeRawFeatureValues() {
    MlAdvisoryControlService service = new MlAdvisoryControlService();

    MonitoringAlert alert =
        service.startMonitoringRun(DriftMonitoringTestFixtures.driftRunCommand("idem-monitoring-event"))
            .value()
            .orElseThrow()
            .alerts()
            .get(0);

    MlAdvisoryOutboxEvent event =
        service.outboxEvents().stream().filter(candidate -> candidate.eventId().equals(alert.eventRef())).findFirst().orElseThrow();

    assertTrue(event.payload().containsKey("rawSensitiveDataExposed"));
    assertFalse(event.payload().containsValue("raw-borrower-feature-value"));
  }
}
