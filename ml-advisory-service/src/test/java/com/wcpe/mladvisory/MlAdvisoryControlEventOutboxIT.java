package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MlAdvisoryControlEventOutboxIT {
  @Test
  void shouldEmitControlChangedWithAuditMetadata() {
    MlAdvisoryControlService service =
        new MlAdvisoryControlService(Clock.fixed(Instant.parse("2026-06-04T01:00:00Z"), ZoneOffset.UTC));

    MlAdvisoryControlResponse response =
        service
            .createControl(
                new CreateControlCommand(
                    "11111111-1111-1111-1111-111111111111",
                    "idem-control",
                    "ml-admin-1",
                    Set.of(MlAdvisoryControlService.ADMIN_ROLE),
                    "retail",
                    "conventional",
                    AdvisoryType.PRICING,
                    AdvisoryMode.SHADOW_ONLY,
                    Instant.parse("2026-06-04T00:00:00Z"),
                    null,
                    "Enable shadow mode",
                    "MRM-2026-0001",
                    "REGISTERED",
                    null,
                    null,
                    "corr-PII-14-S01"))
            .value()
            .orElseThrow();

    MlAdvisoryOutboxEvent event = service.outboxEvents().get(0);
    assertEquals(response.eventRef(), event.eventId());
    assertEquals(MlAdvisoryControlService.CONTROL_CHANGED_EVENT, event.eventType());
    assertEquals("corr-PII-14-S01", event.correlationId());
    assertEquals("SHADOW_ONLY", event.payload().get("newMode"));
    assertFalse(response.auditRef().isBlank());
    assertEquals("ML_ADVISORY_CONTROL_CHANGED", service.auditRecords().get(0).action());
  }
}
