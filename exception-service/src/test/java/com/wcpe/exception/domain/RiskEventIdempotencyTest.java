package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskEventIdempotencyTest {

  @Test
  void idempotencyReplaysSameRiskEventFromDurableRepository() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());
    ExceptionModels.MapRiskMonitoringEventCommand source = RiskEventMapperTest.sourceEvent(
      "RISK-IDEMP-REPLAY-001",
      Map.of("concessionStatus", "APPLIED", "amountBucket", "configured-small")
    );

    ExceptionModels.RiskMonitoringEventEnvelope first = service.publishRiskMonitoringEvent(source, RiskEventMapperTest.mappingVersion(), false);
    ExceptionModels.RiskMonitoringEventEnvelope replay = service.publishRiskMonitoringEvent(source, RiskEventMapperTest.mappingVersion(), false);

    assertEquals(first.riskEventId(), replay.riskEventId());
    assertEquals(first.payloadHash(), replay.payloadHash());
  }

  @Test
  void changedPayloadConflictUsesDurableIdempotencyRecord() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());
    service.publishRiskMonitoringEvent(
      RiskEventMapperTest.sourceEvent("RISK-IDEMP-CONFLICT-001", Map.of("concessionStatus", "APPLIED", "amountBucket", "configured-small")),
      RiskEventMapperTest.mappingVersion(),
      false
    );

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.publishRiskMonitoringEvent(
        RiskEventMapperTest.sourceEvent("RISK-IDEMP-CONFLICT-001", Map.of("concessionStatus", "APPLIED", "amountBucket", "changed")),
        RiskEventMapperTest.mappingVersion(),
        false
      )
    );

    assertEquals("IDEMPOTENCY_CONFLICT", error.code());
  }
}
