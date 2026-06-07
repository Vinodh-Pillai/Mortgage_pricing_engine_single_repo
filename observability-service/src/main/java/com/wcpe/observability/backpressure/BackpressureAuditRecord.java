package com.wcpe.observability.backpressure;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BackpressureAuditRecord(
    UUID tenantId,
    String action,
    String actorId,
    String beforeSummary,
    String afterSummary,
    String policyConfigRef,
    String correlationId,
    String replayHash,
    Instant createdAt) {
  public BackpressureAuditRecord {
    Objects.requireNonNull(tenantId, "tenantId is required");
    action = SafeBackpressureText.requireSafeToken(action, "action", 120);
    actorId = SafeBackpressureText.requireSafeToken(actorId, "actorId", 128);
    beforeSummary = SafeBackpressureText.requireSafeToken(beforeSummary, "beforeSummary", 160);
    afterSummary = SafeBackpressureText.requireSafeToken(afterSummary, "afterSummary", 160);
    policyConfigRef = SafeBackpressureText.requireSafeToken(policyConfigRef, "policyConfigRef", 160);
    correlationId = SafeBackpressureText.requireSafeToken(correlationId, "correlationId", 128);
    replayHash = SafeBackpressureText.requireSafeToken(replayHash, "replayHash", 160);
    Objects.requireNonNull(createdAt, "createdAt is required");
  }
}
