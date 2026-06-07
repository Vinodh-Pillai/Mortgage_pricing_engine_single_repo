package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;

public record EvaluateEligibilityRiskAdvisoryCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String scenarioId,
    String eligibilityResultId,
    String eligibilityResultVersion,
    String snapshotId,
    ModelArtifactRef modelArtifactRef,
    String riskBand,
    double confidence,
    AdvisoryDisplayPolicy displayPolicy,
    List<AdvisoryReason> reviewPrompts,
    Instant generatedAt,
    Instant expiresAt,
    String correlationId,
    long timeoutMillis,
    boolean simulateModelTimeout,
    boolean simulateModelFailure,
    boolean simulateAuthoritativeOutput) {
  public EvaluateEligibilityRiskAdvisoryCommand {
    reviewPrompts = reviewPrompts == null ? List.of() : List.copyOf(reviewPrompts);
  }
}
