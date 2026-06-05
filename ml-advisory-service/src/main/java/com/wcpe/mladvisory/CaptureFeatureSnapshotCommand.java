package com.wcpe.mladvisory;

import java.util.List;
import java.util.Map;

public record CaptureFeatureSnapshotCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String scenarioId,
    String pricingResultId,
    String eligibilityResultId,
    String featureSchemaVersion,
    CaptureMode captureMode,
    String legalBasis,
    String retentionClass,
    String correlationId,
    List<FeatureInput> features,
    Map<String, String> sourceRefs) {}
