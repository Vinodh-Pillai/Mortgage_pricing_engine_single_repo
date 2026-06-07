package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GenerateAdvisoryCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String scenarioId,
    String pricingResultId,
    String snapshotId,
    String modelVersionId,
    AdvisoryType advisoryType,
    String recommendation,
    double confidence,
    String confidenceBand,
    Instant generatedAt,
    Instant expiresAt,
    List<AdvisoryReason> reasons,
    Map<String, String> sourceRefs,
    AdvisoryDisplayPolicy displayPolicy,
    String correlationId) {
  public GenerateAdvisoryCommand {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
    sourceRefs = sourceRefs == null ? Map.of() : Map.copyOf(sourceRefs);
  }
}
