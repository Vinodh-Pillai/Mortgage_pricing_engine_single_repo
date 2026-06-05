package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeatureSnapshotControllerContractTest {
  @Test
  void shouldRedactSensitiveFeatureValues() {
    FeatureSnapshotResponse response =
        FeatureSnapshotBuilderTest.service().captureFeatureSnapshot(FeatureSnapshotBuilderTest.command("idem-contract")).value().orElseThrow();

    assertEquals(
        "POST /api/v1/tenants/{tenantId}/ml-advisory/feature-snapshots",
        MlAdvisoryControlService.CAPTURE_FEATURE_SNAPSHOT_ENDPOINT);
    assertEquals(
        "GET /api/v1/tenants/{tenantId}/ml-advisory/feature-snapshots/{snapshotId}",
        MlAdvisoryControlService.GET_FEATURE_SNAPSHOT_ENDPOINT);
    assertTrue(response.features().stream().anyMatch(value -> value.redactedValue().startsWith("[REDACTED:")));
    assertFalse(response.features().toString().contains("720-739"));
  }
}
