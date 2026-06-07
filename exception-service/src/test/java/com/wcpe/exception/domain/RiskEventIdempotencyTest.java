package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskEventIdempotencyTest {

  @Test
  void sameSourceProducesSameEventId() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());
    ExceptionModels.MapRiskMonitoringEventCommand source = RiskEventMapperTest.sourceEvent(
      "RISK-IDEMP-REPLAY-001",
      Map.of("concessionStatus", "APPLIED", "amountBucket", "configured-small")
    );

    ExceptionModels.RiskMonitoringEventEnvelope first = service.publishRiskMonitoringEvent(
      source,
      RiskEventMapperTest.mappingVersion(),
      false
    );
    ExceptionModels.RiskMonitoringEventEnvelope replayed = service.publishRiskMonitoringEvent(
      source,
      RiskEventMapperTest.mappingVersion(),
      false
    );

    assertEquals(first.riskEventId(), replayed.riskEventId());
    assertEquals(first.payloadHash(), replayed.payloadHash());
    assertEquals(first.headers().get("sourceEventId"), replayed.headers().get("sourceEventId"));
  }

  @Test
  void rejectsSameIdempotencyKeyForChangedPayload() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());
    service.publishRiskMonitoringEvent(
      RiskEventMapperTest.sourceEvent("RISK-IDEMP-CONFLICT-001", Map.of("concessionStatus", "APPLIED")),
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
