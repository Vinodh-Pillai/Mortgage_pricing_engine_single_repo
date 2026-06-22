package com.wcpe.exception.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskEventMapperTest {

  @Test
  void mappingFailsClosedBeforePersistingWithoutDurableRepository() {
    ExceptionService service = new ExceptionService(new ExceptionRepository());

    ExceptionServiceException error = assertThrows(
      ExceptionServiceException.class,
      () -> service.publishRiskMonitoringEvent(
        sourceEvent("RISK-IDEMP-001", Map.of("concessionStatus", "APPLIED", "amountBucket", "configured-small")),
        mappingVersion(),
        false
      )
    );

    assertEquals("PERSISTENCE_BACKEND_REQUIRED", error.code());
  }

  static ExceptionModels.MapRiskMonitoringEventCommand sourceEvent(String idempotencyKey, Map<String, String> payload) {
    return new ExceptionModels.MapRiskMonitoringEventCommand(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      ExceptionModels.RiskEventSourceType.CONCESSION_APPLIED,
      "ConcessionAppliedToQuote:evt-risk-001",
      "exception-service",
      "QUOTE-PII11",
      Map.of("concessionRequestId", "PCR-PII11", "quoteId", "QUOTE-PII11"),
      payload,
      "risk-platform-consumer-1",
      idempotencyKey,
      "corr-risk-pii11",
      "ConcessionAppliedToQuote:evt-risk-001",
      "trace-risk-pii11"
    );
  }

  static ExceptionModels.RiskEventMappingVersion mappingVersion() {
    return new ExceptionModels.RiskEventMappingVersion(
      "RISK-MAPPING-V1",
      ExceptionModels.MonitoringPolicyStatus.PUBLISHED,
      "pricing.risk-monitoring.events",
      "1",
      "SUBJECT",
      List.of(new ExceptionModels.RiskEventMappingRule(
        ExceptionModels.RiskEventSourceType.CONCESSION_APPLIED,
        ExceptionModels.MonitoringSignalType.OVERRIDE_USAGE,
        ExceptionModels.AlertSeverity.HIGH,
        "CONCESSION_LIFECYCLE",
        "schema://risk-events/concession-applied/v1",
        "risk-platform-primary",
        true
      )),
      List.of("borrowerEmail", "ssn", "borrowerName"),
      Set.of(),
      "compliance-admin-1",
      "2026-06-06T00:00:00Z"
    );
  }
}
