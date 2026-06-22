package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskEventIdempotencyTest {

  @Test
  void idempotencyCannotUseProcessLocalMemoryWhenPersistenceIsUnavailable() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());
    ExceptionModels.MapRiskMonitoringEventCommand source = RiskEventMapperTest.sourceEvent(
      "RISK-IDEMP-REPLAY-001",
      Map.of("concessionStatus", "APPLIED", "amountBucket", "configured-small")
    );

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.publishRiskMonitoringEvent(source, RiskEventMapperTest.mappingVersion(), false)
    );

    assertEquals("PERSISTENCE_BACKEND_REQUIRED", error.code());
  }

  @Test
  void changedPayloadConflictRequiresDurableStoreAndFailsClosedWithoutIt() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.publishRiskMonitoringEvent(
        RiskEventMapperTest.sourceEvent("RISK-IDEMP-CONFLICT-001", Map.of("concessionStatus", "APPLIED", "amountBucket", "changed")),
        RiskEventMapperTest.mappingVersion(),
        false
      )
    );

    assertEquals("PERSISTENCE_BACKEND_REQUIRED", error.code());
  }
}
