package com.wcpe.governance;

import java.time.Instant;
import java.util.Map;

public record ConfigLifecycleVersion(
    String tenantId,
    String artifactId,
    String versionId,
    String artifactType,
    String contextKey,
    ConfigLifecycleState status,
    int revision,
    String etag,
    String payloadHash,
    String lastMaterialEditorId,
    String validationRunId,
    String validationResultHash,
    Instant validationCompletedAt,
    boolean validationBlocking,
    Instant effectiveStart,
    Instant effectiveEnd,
    Map<String, String> payload) {
  public ConfigLifecycleVersion {
    payload = Map.copyOf(payload == null ? Map.of() : payload);
  }

  ConfigLifecycleVersion withValidation(String runId, String resultHash, Instant completedAt, boolean blocking, String nextEtag) {
    return new ConfigLifecycleVersion(
        tenantId,
        artifactId,
        versionId,
        artifactType,
        contextKey,
        ConfigLifecycleState.VALIDATED,
        revision + 1,
        nextEtag,
        payloadHash,
        lastMaterialEditorId,
        runId,
        resultHash,
        completedAt,
        blocking,
        effectiveStart,
        effectiveEnd,
        payload);
  }

  ConfigLifecycleVersion withStatus(ConfigLifecycleState nextStatus, int nextRevision, String nextEtag, Instant nextEffectiveStart, Instant nextEffectiveEnd) {
    return new ConfigLifecycleVersion(
        tenantId,
        artifactId,
        versionId,
        artifactType,
        contextKey,
        nextStatus,
        nextRevision,
        nextEtag,
        payloadHash,
        lastMaterialEditorId,
        validationRunId,
        validationResultHash,
        validationCompletedAt,
        validationBlocking,
        nextEffectiveStart,
        nextEffectiveEnd,
        payload);
  }
}
