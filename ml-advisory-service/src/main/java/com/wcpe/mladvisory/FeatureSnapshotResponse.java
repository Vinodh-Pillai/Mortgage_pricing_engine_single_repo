package com.wcpe.mladvisory;

import java.util.List;

public record FeatureSnapshotResponse(
    String snapshotId,
    String tenantId,
    String scenarioId,
    String featureSchemaVersion,
    CaptureMode captureMode,
    String featureHash,
    String governanceStatus,
    String eventRef,
    String auditRef,
    String correlationId,
    List<FeatureSnapshotValue> features) {}
