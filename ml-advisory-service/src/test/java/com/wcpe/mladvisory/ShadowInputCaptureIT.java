package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShadowInputCaptureIT {
  @Test
  void shouldNotChangePricingResponseWhenCaptureFails() {
    MlAdvisoryControlService service = FeatureSnapshotBuilderTest.service();
    String pricingResponseBeforeCapture = "deterministic-pricing-response-v1";

    MlAdvisoryResult<FeatureSnapshotResponse> failedCapture =
        service.captureFeatureSnapshot(
            new CaptureFeatureSnapshotCommand(
                "11111111-1111-1111-1111-111111111111",
                "idem-fail",
                "pricing-api",
                "scenario-1001",
                "pricing-result-v3",
                "eligibility-result-v2",
                "schema-2026-06",
                CaptureMode.SHADOW_ONLY,
                "",
                "AUDIT_EVIDENCE",
                "corr-PII-14-S02",
                FeatureSnapshotBuilderTest.command("unused").features(),
                FeatureSnapshotBuilderTest.command("unused").sourceRefs()));

    assertFalse(failedCapture.valid());
    assertEquals("POLICY_NOT_SATISFIED", failedCapture.errorCode().orElseThrow());
    assertEquals("deterministic-pricing-response-v1", pricingResponseBeforeCapture);
    assertTrue(service.outboxEvents().isEmpty());
  }
}
