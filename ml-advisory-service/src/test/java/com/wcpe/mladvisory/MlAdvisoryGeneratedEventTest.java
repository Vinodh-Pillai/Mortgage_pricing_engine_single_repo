package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class MlAdvisoryGeneratedEventTest {
  @Test
  void shouldNotContainSensitiveFeatureValues() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-generated-event", 0.66)).value().orElseThrow();

    MlAdvisoryOutboxEvent event = service.outboxEvents().stream()
        .filter(candidate -> candidate.eventType().equals(MlAdvisoryControlService.ML_ADVISORY_GENERATED_EVENT))
        .findFirst()
        .orElseThrow();

    assertEquals(card.eventRef(), event.eventId());
    assertEquals("false", event.payload().get("authoritative"));
    assertEquals("model-version-approved-1", event.payload().get("modelVersionId"));
    assertEquals("snapshot-789", event.payload().get("snapshotId"));
    assertFalse(event.payload().toString().contains("Tenant-configured model signal"));
    assertEquals("ML_ADVISORY_GENERATED", service.auditRecords().get(1).action());
  }
}
