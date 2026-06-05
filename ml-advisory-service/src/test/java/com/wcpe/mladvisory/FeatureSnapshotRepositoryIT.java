package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureSnapshotRepositoryIT {
  @Test
  void shouldEnforceTenantIsolation() {
    MlAdvisoryControlService service = FeatureSnapshotBuilderTest.service();
    FeatureSnapshotResponse tenantOne = service.captureFeatureSnapshot(FeatureSnapshotBuilderTest.command("idem-tenant-one")).value().orElseThrow();

    assertTrue(service.getFeatureSnapshot(tenantOne.tenantId(), tenantOne.snapshotId(), "corr-read").valid());
    MlAdvisoryResult<FeatureSnapshotResponse> otherTenantRead =
        service.getFeatureSnapshot("22222222-2222-2222-2222-222222222222", tenantOne.snapshotId(), "corr-read");
    assertFalse(otherTenantRead.valid());
    assertEquals("ML_SNAPSHOT_NOT_FOUND", otherTenantRead.errorCode().orElseThrow());

    List<FeatureSnapshotResponse> otherTenantSearch =
        service.searchFeatureSnapshots(
            new FeatureSnapshotSearchQuery("22222222-2222-2222-2222-222222222222", "scenario-1001", null, null, "corr-read"));
    assertTrue(otherTenantSearch.isEmpty());
  }
}
