package com.wcpe.lock;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LockModels {
  private LockModels() {}

  public enum RateLockStatus {
    REQUESTED, PENDING_APPROVAL, APPROVED, REJECTED, CANCELLED;

    public Set<RateLockStatus> allowedNextStates() {
      return switch (this) {
        case REQUESTED, PENDING_APPROVAL -> Set.of(APPROVED, REJECTED, CANCELLED);
        case APPROVED, REJECTED, CANCELLED -> Set.of();
      };
    }

    public boolean active() {
      return this == REQUESTED || this == PENDING_APPROVAL || this == APPROVED;
    }
  }

  public record LockRequestCommand(
    UUID tenantId,
    String requestId,
    String actorId,
    String quoteId,
    String loanId,
    String scenarioHash,
    String pricingResultHash,
    String rateSheetVersion,
    String productId,
    String investorId,
    String channel,
    int lockPeriodDays,
    Instant quotePricedAt,
    Instant requestedAt,
    String idempotencyKey,
    String correlationId,
    boolean permissionGranted,
    boolean autoApprovalPermitted,
    boolean quoteFresh,
    boolean scenarioHashUnchanged,
    boolean pricingHashUnchanged,
    boolean rateSheetLockable,
    boolean marketSuspended,
    boolean investorSuspended,
    boolean complianceBlocking,
    boolean tenantChannelConfigPresent,
    boolean investorAmbiguous,
    String lockPolicyVersionId,
    String complianceEvidenceRef,
    Map<String, String> sourceRefs
  ) {}

  public record RateLockRecord(
    UUID tenantId,
    String lockId,
    String requestId,
    String quoteId,
    String loanId,
    String scenarioHash,
    RateLockStatus status,
    int version,
    Instant createdAt,
    Instant updatedAt,
    String idempotencyKey,
    String correlationId,
    String lockPolicyVersionId,
    String requestHash,
    String auditRef,
    String replayRef,
    String outboxEventType
  ) {}

  public record LockRequestResponse(
    UUID tenantId,
    String lockId,
    String requestId,
    RateLockStatus status,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType,
    String requestHash
  ) {}

  public record LockEvent(
    String eventType,
    String eventVersion,
    String eventKey,
    UUID tenantId,
    String lockId,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    Instant occurredAt,
    Map<String, String> payload
  ) {}

  public record AuditSnapshot(
    String auditRef,
    UUID tenantId,
    String lockId,
    String action,
    String actorId,
    String beforeState,
    String afterState,
    String lockPolicyVersionId,
    String complianceEvidenceRef,
    String correlationId,
    String replayHash
  ) {}

  public record MetricsSnapshot(
    long lockRequestTotal,
    long lockRequestRejectedTotal,
    long lockOutboxLagSeconds
  ) {}

  static String normalized(String value) {
    return value == null ? "" : value.trim();
  }

  static String upper(String value) {
    return normalized(value).toUpperCase(Locale.ROOT);
  }
}
