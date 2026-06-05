package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class MlFeatureSnapshotCapturedEventTest {
  @Test
  void shouldIncludeLineageAndSchemaVersion() {
    MlAdvisoryControlService service = FeatureSnapshotBuilderTest.service();
    FeatureSnapshotResponse response = service.captureFeatureSnapshot(FeatureSnapshotBuilderTest.command("idem-event")).value().orElseThrow();

    MlAdvisoryOutboxEvent event = service.outboxEvents().get(0);
    assertEquals(response.eventRef(), event.eventId());
    assertEquals(MlAdvisoryControlService.FEATURE_SNAPSHOT_CAPTURED_EVENT, event.eventType());
    assertEquals(response.featureSchemaVersion(), event.payload().get("schemaVersion"));
    assertEquals(response.featureHash(), event.payload().get("featureHash"));
    assertEquals("eligibilityResult,pricingResult", event.payload().get("sourceRefs"));
    assertFalse(event.payload().toString().contains("720-739"));
    assertEquals("SHADOW_INPUT_CAPTURE_COMPLETED", service.auditRecords().get(0).action());
  }
}
