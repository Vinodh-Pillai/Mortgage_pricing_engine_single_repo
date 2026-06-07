package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;

public record EvaluatePricingAdvisoryCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String scenarioId,
    String pricingResultId,
    String snapshotId,
    ModelArtifactRef modelArtifactRef,
    double confidence,
    AdvisoryDisplayPolicy displayPolicy,
    List<AdvisoryReason> reasons,
    Instant generatedAt,
    Instant expiresAt,
    String correlationId,
    long timeoutMillis,
    boolean simulateModelTimeout,
    boolean simulateModelFailure,
    boolean simulateAuthoritativeOutput) {
  public EvaluatePricingAdvisoryCommand {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }
}
