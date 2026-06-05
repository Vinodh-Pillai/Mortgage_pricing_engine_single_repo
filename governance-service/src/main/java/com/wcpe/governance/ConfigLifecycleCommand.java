package com.wcpe.governance;

import java.time.Instant;
import java.util.Map;

public record ConfigLifecycleCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String actorGroup,
    String artifactId,
    String versionId,
    ConfigLifecycleAction action,
    ConfigLifecycleState expectedStatus,
    String expectedEtag,
    ConfigLifecyclePolicy policy,
    ConfigLifecycleVersion version,
    String validationRunId,
    String validationResultHash,
    Instant validationCompletedAt,
    boolean validationBlocking,
    Instant requestedEffectiveStart,
    Instant requestedEffectiveEnd,
    String reasonCode,
    String comments,
    String correlationId,
    Map<String, String> evidenceRefs) {
  public ConfigLifecycleCommand {
    evidenceRefs = Map.copyOf(evidenceRefs == null ? Map.of() : evidenceRefs);
  }
}
