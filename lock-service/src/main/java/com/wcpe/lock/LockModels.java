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
    REQUESTED, PENDING_APPROVAL, APPROVED, PENDING_INVESTOR_CONFIRMATION, ACTIVE, INVESTOR_REJECTED, REJECTED, CANCELLED;

    public Set<RateLockStatus> allowedNextStates() {
      return switch (this) {
        case REQUESTED, PENDING_APPROVAL -> Set.of(APPROVED, REJECTED, CANCELLED);
        case APPROVED -> Set.of(PENDING_INVESTOR_CONFIRMATION, ACTIVE, CANCELLED);
        case PENDING_INVESTOR_CONFIRMATION -> Set.of(ACTIVE, INVESTOR_REJECTED, CANCELLED);
        case ACTIVE, INVESTOR_REJECTED, REJECTED, CANCELLED -> Set.of();
      };
    }

    public boolean active() {
      return this == REQUESTED || this == PENDING_APPROVAL || this == APPROVED
        || this == PENDING_INVESTOR_CONFIRMATION || this == ACTIVE;
    }
  }

  public enum LockDecisionType {
    APPROVE, REJECT
  }

  public enum FreshnessDecisionType {
    FRESH, EXPIRES_SOON, STALE, POLICY_SUSPENDED, CONFIG_ERROR, UNKNOWN;

    public boolean lockable() {
      return this == FRESH || this == EXPIRES_SOON;
    }
  }

  public enum LockConfirmationType {
    INTERNAL, INVESTOR_REQUEST, INVESTOR_CALLBACK
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

  public record LockDecisionCommand(
    UUID tenantId,
    String lockId,
    LockDecisionType decision,
    String actorId,
    String requesterActorId,
    int expectedVersion,
    List<String> reasonCodes,
    String note,
    String policyVersionId,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Instant decidedAt,
    boolean permissionGranted,
    boolean separationOfDutiesConfigured,
    boolean decisionPolicyCurrent
  ) {}

  public record LockDecisionResponse(
    UUID tenantId,
    String lockId,
    String decisionId,
    LockDecisionType decision,
    RateLockStatus previousStatus,
    RateLockStatus status,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType,
    String decisionHash
  ) {}

  public record FreshnessCheckCommand(
    UUID tenantId,
    String requestId,
    String actorId,
    String quoteId,
    String scenarioHash,
    String pricingResultHash,
    String currentScenarioHash,
    String currentPricingResultHash,
    String rateSheetVersion,
    String marketDataVersion,
    String productId,
    String investorId,
    String channel,
    Instant quotePricedAt,
    Instant evaluatedAt,
    long maxQuoteAgeSeconds,
    long expirySoonWindowSeconds,
    Instant expiresAt,
    String policyVersionId,
    String complianceEvidenceRef,
    boolean permissionGranted,
    boolean policyResolved,
    boolean policyAmbiguous,
    boolean rateSheetLockable,
    boolean marketSuspended,
    boolean investorEnabled,
    boolean complianceBlocking,
    boolean emitAuditEvent,
    String idempotencyKey,
    String correlationId,
    Map<String, String> sourceRefs
  ) {}

  public record FreshnessCheckResponse(
    UUID tenantId,
    String checkId,
    String quoteId,
    FreshnessDecisionType decision,
    List<String> reasonCodes,
    String policyVersionId,
    long quoteAgeSeconds,
    Instant expiresAt,
    List<String> remediations,
    String auditRef,
    String replayRef,
    String correlationId,
    String resultHash
  ) {}

  public record FreshnessCheckRecord(
    UUID tenantId,
    String checkId,
    String quoteId,
    String scenarioHash,
    String policyVersionId,
    FreshnessDecisionType decision,
    List<String> reasonCodes,
    Instant evaluatedAt,
    Instant expiresAt,
    String resultHash,
    String createdBy,
    String correlationId
  ) {}

  public record LockConfirmationCommand(
    UUID tenantId,
    String lockId,
    String actorId,
    int expectedVersion,
    LockConfirmationType confirmationType,
    String lockNumber,
    boolean lockNumberOverrideAllowed,
    String investorId,
    String investorConfirmationRef,
    int lockPeriodDays,
    Instant confirmedAt,
    Instant expiresAt,
    String requestedProductId,
    String responseProductId,
    String requestedLoanId,
    String responseLoanId,
    String requestedPolicyVersionId,
    String responsePolicyVersionId,
    boolean permissionGranted,
    boolean freshnessLockable,
    boolean confirmationPolicyResolved,
    boolean investorResponseMatches,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Map<String, String> sourceRefs
  ) {}

  public record LockConfirmationRecord(
    UUID tenantId,
    String confirmationId,
    String lockId,
    LockConfirmationType confirmationType,
    String lockNumber,
    String investorId,
    String investorConfirmationRef,
    RateLockStatus status,
    int lockVersion,
    Instant confirmedAt,
    Instant expiresAt,
    String confirmedTermsHash,
    String idempotencyKey,
    String correlationId,
    String replayRef
  ) {}

  public record LockConfirmationResponse(
    UUID tenantId,
    String lockId,
    String confirmationId,
    RateLockStatus previousStatus,
    RateLockStatus status,
    int version,
    String lockNumber,
    String investorConfirmationRef,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType,
    String confirmedTermsHash
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
    long lockApprovalTotal,
    long lockRejectionTotal,
    long lockDecisionPolicyBlockedTotal,
    long freshnessCheckTotal,
    long freshnessPolicyResolutionFailureTotal,
    long freshnessExpiresSoonTotal,
    long lockConfirmationTotal,
    long pendingInvestorConfirmationTotal,
    long investorMismatchTotal,
    long lockOutboxLagSeconds
  ) {}

  static String normalized(String value) {
    return value == null ? "" : value.trim();
  }

  static String upper(String value) {
    return normalized(value).toUpperCase(Locale.ROOT);
  }
}
