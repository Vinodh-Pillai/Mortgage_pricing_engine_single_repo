package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;

public record AdvisoryExplanation(
    String explanationId,
    String tenantId,
    String advisoryId,
    String modelVersion,
    String featureSnapshotId,
    String policyVersion,
    String summary,
    String confidenceNarrative,
    String disclaimer,
    boolean authoritative,
    boolean notAdverseAction,
    String status,
    Instant generatedAt,
    Instant expiresAt,
    List<ExplanationDriver> drivers,
    List<ExplanationLimitation> limitations,
    String eventRef,
    String auditRef,
    String correlationId) {
  public AdvisoryExplanation {
    drivers = drivers == null ? List.of() : List.copyOf(drivers);
    limitations = limitations == null ? List.of() : List.copyOf(limitations);
  }
}
