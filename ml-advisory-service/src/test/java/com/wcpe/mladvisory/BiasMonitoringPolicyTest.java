package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class BiasMonitoringPolicyTest {
  @Test
  void shouldUseOnlyApprovedAggregateCohorts() {
    MlAdvisoryControlService service = new MlAdvisoryControlService();

    MlAdvisoryResult<ModelMonitoringRun> result =
        service.startMonitoringRun(
            DriftMonitoringTestFixtures.command(
                "idem-monitoring-bias-raw", List.of(DriftMonitoringTestFixtures.biasMetric(false, true))));

    assertEquals("ML_MONITORING_ACTION_UNAUTHORIZED", result.errorCode().orElseThrow());
  }
}
