package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FeatureSnapshot(
    String snapshotId,
    String tenantId,
    String scenarioId,
    String pricingResultId,
    String eligibilityResultId,
    String featureSchemaVersion,
    CaptureMode captureMode,
    String featureHash,
    Instant createdAt,
    String retentionClass,
    String governanceStatus,
    String correlationId,
    List<FeatureSnapshotValue> features,
    Map<String, String> sourceRefs) {}
