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
    REQUESTED, PENDING_APPROVAL, APPROVED, PENDING_INVESTOR_CONFIRMATION, ACTIVE, EXPIRING_SOON,
    EXTENSION_REQUESTED, EXTENSION_APPROVED, PENDING_INVESTOR_EXTENSION_CONFIRMATION,
    RELOCK_REQUESTED, RELOCK_APPROVED, RELOCK_REJECTED, PENDING_INVESTOR_RELOCK_CONFIRMATION, RELOCKED,
    INVESTOR_REJECTED, REJECTED, CANCELLED, EXPIRED;

    public Set<RateLockStatus> allowedNextStates() {
      return switch (this) {
        case REQUESTED, PENDING_APPROVAL -> Set.of(APPROVED, REJECTED, CANCELLED);
        case APPROVED -> Set.of(PENDING_INVESTOR_CONFIRMATION, ACTIVE, CANCELLED);
        case PENDING_INVESTOR_CONFIRMATION -> Set.of(ACTIVE, INVESTOR_REJECTED, CANCELLED);
        case ACTIVE -> Set.of(EXPIRING_SOON, EXTENSION_REQUESTED, RELOCK_REQUESTED, EXPIRED, CANCELLED);
        case EXPIRING_SOON -> Set.of(ACTIVE, EXTENSION_REQUESTED, RELOCK_REQUESTED, EXPIRED, CANCELLED);
        case EXTENSION_REQUESTED -> Set.of(EXTENSION_APPROVED, ACTIVE, CANCELLED);
        case EXTENSION_APPROVED -> Set.of(PENDING_INVESTOR_EXTENSION_CONFIRMATION, ACTIVE, CANCELLED);
        case PENDING_INVESTOR_EXTENSION_CONFIRMATION -> Set.of(ACTIVE, INVESTOR_REJECTED, CANCELLED);
        case EXPIRED, CANCELLED -> Set.of(RELOCK_REQUESTED);
        case RELOCK_REQUESTED -> Set.of(RELOCK_APPROVED, RELOCK_REJECTED, CANCELLED);
        case RELOCK_APPROVED -> Set.of(PENDING_INVESTOR_RELOCK_CONFIRMATION, RELOCKED, ACTIVE);
        case PENDING_INVESTOR_RELOCK_CONFIRMATION -> Set.of(RELOCKED, INVESTOR_REJECTED, CANCELLED);
        case INVESTOR_REJECTED, REJECTED, RELOCK_REJECTED, RELOCKED -> Set.of();
      };
    }

    public boolean active() {
      return this == REQUESTED || this == PENDING_APPROVAL || this == APPROVED
        || this == PENDING_INVESTOR_CONFIRMATION || this == ACTIVE || this == EXPIRING_SOON
        || this == EXTENSION_REQUESTED || this == EXTENSION_APPROVED || this == PENDING_INVESTOR_EXTENSION_CONFIRMATION
        || this == RELOCK_REQUESTED || this == RELOCK_APPROVED || this == PENDING_INVESTOR_RELOCK_CONFIRMATION;
    }
  }

  public enum LockExtensionStatus {
    PREVIEWED, REQUESTED, APPROVED, REJECTED, PENDING_INVESTOR_CONFIRMATION, CONFIRMED, CANCELLED
  }

  public enum LockExtensionDecisionType {
    APPROVE, REJECT
  }

  public enum RelockStatus {
    PREVIEWED, REQUESTED, APPROVED, REJECTED, PENDING_INVESTOR_CONFIRMATION, CONFIRMED, CANCELLED
  }

  public enum RelockDecisionType {
    APPROVE, REJECT
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

  public enum LockSyncStatus {
    PENDING, SENT, ACKED, FAILED, DLQ, RECONCILED
  }

  public enum LockAuditReportStatus {
    REQUESTED, READY, FAILED
  }

  public enum LockReplayMismatchClass {
    MATCH, RESULT_MISMATCH, CONFIG_REF_MISSING
  }

  public record LockAuditReportCommand(
    UUID tenantId,
    String requestId,
    String actorId,
    Map<String, String> criteria,
    Instant requestedAt,
    boolean permissionGranted,
    String idempotencyKey,
    String correlationId,
    Map<String, String> sourceRefs
  ) {}

  public record LockAuditReportRecord(
    UUID tenantId,
    String reportId,
    LockAuditReportStatus status,
    String requestedBy,
    Instant generatedAt,
    String criteriaHash,
    String manifestHash,
    String idempotencyKey,
    String correlationId
  ) {}

  public record LockAuditReportResponse(
    UUID tenantId,
    String reportId,
    LockAuditReportStatus status,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType,
    String manifestHash
  ) {}

  public record LockReplayCommand(
    UUID tenantId,
    String lockId,
    String replayId,
    String actorId,
    String capturedInputHash,
    String configGraphHash,
    String eventSequenceHash,
    String expectedResultHash,
    String actualResultHash,
    boolean permissionGranted,
    String idempotencyKey,
    String correlationId,
    Instant replayedAt,
    Map<String, String> historicalRefs
  ) {}

  public record LockReplayResult(
    UUID tenantId,
    String replayId,
    String lockId,
    String inputHash,
    String configGraphHash,
    String eventSequenceHash,
    String expectedResultHash,
    String actualResultHash,
    LockReplayMismatchClass mismatchClass,
    String evidenceHash,
    String idempotencyKey,
    String correlationId,
    Instant replayedAt
  ) {}

  public record LockReplayResponse(
    UUID tenantId,
    String replayId,
    String lockId,
    LockReplayMismatchClass mismatchClass,
    String evidenceHash,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType
  ) {}

  public record LockCancellationCommand(
    UUID tenantId,
    String lockId,
    String cancellationId,
    String reasonCode,
    String note,
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    boolean cancellationPolicyResolved,
    boolean externalNotifyRequired,
    String policyVersionId,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Instant cancelledAt,
    Map<String, String> sourceRefs
  ) {}

  public record LockCancellationRecord(
    UUID tenantId,
    String cancellationId,
    String lockId,
    String reasonCode,
    String cancelledBy,
    Instant cancelledAt,
    String policyVersionId,
    boolean externalNotifyRequired,
    String evidenceHash,
    String idempotencyKey,
    String correlationId
  ) {}

  public record LockCancellationResponse(
    UUID tenantId,
    String cancellationId,
    String lockId,
    RateLockStatus previousStatus,
    RateLockStatus status,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType,
    String evidenceHash
  ) {}

  public record LockEvidenceExportCommand(
    UUID tenantId,
    String reportId,
    String exportId,
    String actorId,
    List<String> eventIds,
    Map<String, String> schemaVersions,
    Map<String, String> configVersions,
    Map<String, String> snapshotHashes,
    Map<String, String> generatedFileHashes,
    String purposeCode,
    boolean permissionGranted,
    boolean redactedByDefault,
    String idempotencyKey,
    String correlationId,
    Instant generatedAt
  ) {}

  public record LockEvidenceExportRecord(
    UUID tenantId,
    String exportId,
    String reportId,
    String actorId,
    Map<String, String> actorRefs,
    String purposeCode,
    boolean redactedByDefault,
    String manifestHash,
    List<String> eventIds,
    Map<String, String> schemaVersions,
    Map<String, String> configVersions,
    Map<String, String> snapshotHashes,
    Map<String, String> generatedFileHashes,
    String idempotencyKey,
    String correlationId,
    Instant generatedAt
  ) {}

  public record LockEvidenceExportResponse(
    UUID tenantId,
    String exportId,
    String reportId,
    String status,
    String manifestHash,
    List<String> eventIds,
    Map<String, String> schemaVersions,
    Map<String, String> configVersions,
    Map<String, String> snapshotHashes,
    Map<String, String> actorRefs,
    Map<String, String> generatedFileHashes,
    boolean redactedByDefault,
    String actorId,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType
  ) {}

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
    Instant expiresAt,
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

  public record ExtensionCostSnapshot(
    String priceAdjustment,
    String feeAmount,
    String payerType,
    String roundingMode,
    String reasonCode,
    String policyVersionId
  ) {}

  public record LockExtensionPreviewCommand(
    UUID tenantId,
    String lockId,
    String actorId,
    int expectedVersion,
    int requestedDays,
    Instant requestedExpiresAt,
    String reasonCode,
    ExtensionCostSnapshot costSnapshot,
    boolean permissionGranted,
    boolean extensionPolicyResolved,
    boolean compliancePermitsAmendedTerms,
    boolean investorSupportsExtension,
    String idempotencyKey,
    String correlationId,
    Map<String, String> sourceRefs
  ) {}

  public record LockExtensionPreviewResponse(
    UUID tenantId,
    String lockId,
    int requestedDays,
    Instant requestedExpiresAt,
    ExtensionCostSnapshot costSnapshot,
    List<String> validationMessages,
    String correlationId,
    String resultHash
  ) {}

  public record LockExtensionRequestCommand(
    UUID tenantId,
    String lockId,
    String requestId,
    String actorId,
    int expectedVersion,
    int requestedDays,
    Instant requestedAt,
    Instant requestedExpiresAt,
    String reasonCode,
    ExtensionCostSnapshot costSnapshot,
    boolean permissionGranted,
    boolean extensionPolicyResolved,
    boolean compliancePermitsAmendedTerms,
    boolean investorSupportsExtension,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Map<String, String> sourceRefs
  ) {}

  public record LockExtensionDecisionCommand(
    UUID tenantId,
    String lockId,
    String extensionId,
    LockExtensionDecisionType decision,
    String actorId,
    String requesterActorId,
    int expectedVersion,
    boolean investorConfirmationRequired,
    boolean permissionGranted,
    boolean separationOfDutiesConfigured,
    boolean decisionPolicyCurrent,
    List<String> reasonCodes,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Instant decidedAt
  ) {}

  public record LockExtensionConfirmationCommand(
    UUID tenantId,
    String lockId,
    String extensionId,
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    boolean investorResponseMatches,
    String investorConfirmationRef,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Instant confirmedAt,
    Map<String, String> sourceRefs
  ) {}

  public record LockExtensionCancelCommand(
    UUID tenantId,
    String lockId,
    String extensionId,
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    List<String> reasonCodes,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Instant cancelledAt
  ) {}

  public record LockExtensionRecord(
    UUID tenantId,
    String extensionId,
    String lockId,
    LockExtensionStatus status,
    int lockVersion,
    int requestedDays,
    Instant previousExpiresAt,
    Instant requestedExpiresAt,
    String reasonCode,
    String requestedBy,
    String approvedBy,
    Instant requestedAt,
    Instant decidedAt,
    Instant confirmedAt,
    String policyVersionId,
    String costSnapshotHash,
    String idempotencyKey,
    String correlationId,
    String replayRef
  ) {}

  public record LockExtensionResponse(
    UUID tenantId,
    String lockId,
    String extensionId,
    LockExtensionStatus extensionStatus,
    RateLockStatus status,
    int version,
    Instant expiresAt,
    int requestedDays,
    Instant requestedExpiresAt,
    ExtensionCostSnapshot costSnapshot,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType,
    String resultHash
  ) {}

  public record RelockTermsSnapshot(
    String productId,
    String investorId,
    String rateSheetVersion,
    String priceRef,
    String rateRef,
    String feeRef,
    String lockPeriodRef,
    String termsHash
  ) {}

  public record RelockPolicySnapshot(
    String policyVersionId,
    String selectionModeRef,
    String waitingPeriodRef,
    String feeTreatmentRef,
    String eligibilityThresholdRef,
    String benefitLedgerRef,
    boolean sourceStateEligible,
    boolean currentQuoteFresh,
    boolean waitingPeriodSatisfied,
    boolean relockPolicyResolved,
    boolean compliancePermitsSelectedTerms,
    boolean investorConfirmationRequired
  ) {}

  public record RelockPreviewCommand(
    UUID tenantId,
    String sourceLockId,
    String actorId,
    int expectedVersion,
    String currentQuoteId,
    RelockTermsSnapshot originalTerms,
    RelockTermsSnapshot currentTerms,
    RelockTermsSnapshot selectedTerms,
    RelockPolicySnapshot policySnapshot,
    String reasonCode,
    boolean permissionGranted,
    String idempotencyKey,
    String correlationId,
    Map<String, String> sourceRefs
  ) {}

  public record RelockRequestCommand(
    UUID tenantId,
    String sourceLockId,
    String requestId,
    String actorId,
    int expectedVersion,
    String currentQuoteId,
    Instant requestedAt,
    RelockTermsSnapshot originalTerms,
    RelockTermsSnapshot currentTerms,
    RelockTermsSnapshot selectedTerms,
    RelockPolicySnapshot policySnapshot,
    String reasonCode,
    String complianceEvidenceRef,
    boolean permissionGranted,
    String idempotencyKey,
    String correlationId,
    Map<String, String> sourceRefs
  ) {}

  public record RelockDecisionCommand(
    UUID tenantId,
    String sourceLockId,
    String relockId,
    RelockDecisionType decision,
    String actorId,
    String requesterActorId,
    int expectedVersion,
    boolean permissionGranted,
    boolean separationOfDutiesConfigured,
    boolean decisionPolicyCurrent,
    List<String> reasonCodes,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Instant decidedAt
  ) {}

  public record RelockConfirmationCommand(
    UUID tenantId,
    String sourceLockId,
    String relockId,
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    boolean investorResponseMatches,
    String investorConfirmationRef,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Instant confirmedAt,
    Map<String, String> sourceRefs
  ) {}

  public record RelockCancelCommand(
    UUID tenantId,
    String sourceLockId,
    String relockId,
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    List<String> reasonCodes,
    String complianceEvidenceRef,
    String idempotencyKey,
    String correlationId,
    Instant cancelledAt
  ) {}

  public record RelockRecord(
    UUID tenantId,
    String relockId,
    String sourceLockId,
    String replacementLockId,
    String currentQuoteId,
    RelockStatus status,
    int sourceLockVersion,
    String requestedBy,
    String approvedBy,
    Instant requestedAt,
    Instant decidedAt,
    Instant confirmedAt,
    String reasonCode,
    String policyVersionId,
    boolean investorConfirmationRequired,
    String comparisonHash,
    String idempotencyKey,
    String correlationId,
    String replayRef
  ) {}

  public record RelockResponse(
    UUID tenantId,
    String relockId,
    String sourceLockId,
    String replacementLockId,
    RelockStatus relockStatus,
    RateLockStatus sourceStatus,
    RateLockStatus replacementStatus,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType,
    String comparisonHash
  ) {}

  public record LockExpirationRunCommand(
    UUID tenantId,
    String runId,
    String actorId,
    Instant evaluatedAt,
    long warningThresholdSeconds,
    String policyVersionId,
    boolean permissionGranted,
    boolean expirationPolicyResolved,
    boolean dryRun,
    String correlationId
  ) {}

  public record LockExpirationRunResponse(
    UUID tenantId,
    String runId,
    Instant startedAt,
    Instant completedAt,
    String status,
    int processedCount,
    int expiringSoonCount,
    int expiredCount,
    int noOpCount,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId
  ) {}

  public record LockExpirationSchedule(
    UUID tenantId,
    String lockId,
    Instant expiresAt,
    Instant nextWarningAt,
    String policyVersionId,
    Instant lastEvaluatedAt
  ) {}

  public record LockExpirationRunRecord(
    UUID tenantId,
    String runId,
    Instant startedAt,
    Instant completedAt,
    String status,
    int processedCount,
    int expiringSoonCount,
    int expiredCount,
    int noOpCount,
    String replayRef,
    String correlationId
  ) {}

  public record LockSyncTarget(
    UUID tenantId,
    String targetId,
    String system,
    boolean enabled,
    String contractVersion,
    String policyVersion
  ) {}

  public record LockStatusSyncCommand(
    UUID tenantId,
    String lockId,
    String eventId,
    String actorId,
    LockSyncTarget target,
    boolean permissionGranted,
    Instant requestedAt,
    String idempotencyKey,
    String correlationId,
    Map<String, String> sourceRefs
  ) {}

  public record LockSyncAttempt(
    UUID tenantId,
    String attemptId,
    String lockId,
    String eventId,
    String targetId,
    LockSyncStatus status,
    String payloadHash,
    int retryCount,
    Instant nextRetryAt,
    String ackRef,
    String correlationId,
    String policyVersion,
    String contractVersion,
    Instant updatedAt
  ) {}

  public record LockSyncAttemptResponse(
    UUID tenantId,
    String attemptId,
    String lockId,
    String eventId,
    String targetId,
    LockSyncStatus status,
    String payloadHash,
    int retryCount,
    Instant nextRetryAt,
    String ackRef,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String outboxEventType
  ) {}

  public record LockStatusAckCommand(
    UUID tenantId,
    String ackId,
    String lockId,
    String eventId,
    String targetId,
    String actorId,
    boolean permissionGranted,
    boolean sourceTrusted,
    RateLockStatus expectedCurrentStatus,
    RateLockStatus requestedLockStatus,
    LockSyncStatus ackStatus,
    String ackRef,
    String policyVersion,
    String contractVersion,
    Instant receivedAt,
    String idempotencyKey,
    String correlationId,
    Map<String, String> sourceRefs
  ) {}

  public record LockSyncAcknowledgement(
    UUID tenantId,
    String ackId,
    String lockId,
    String eventId,
    String targetId,
    LockSyncStatus ackStatus,
    String ackRef,
    String payloadHash,
    Instant receivedAt,
    String correlationId
  ) {}

  public record LockReconciliationRecord(
    UUID tenantId,
    String recordId,
    String lockId,
    String targetSystem,
    String driftType,
    String resolution,
    String actorId,
    Instant reconciledAt,
    String replayRef,
    String correlationId
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
    long lockExpirationRunTotal,
    long locksExpiringSoonTotal,
    long locksExpiredTotal,
    long lockOutboxLagSeconds,
    long extensionRequestTotal,
    long extensionApprovalTotal,
    long extensionRejectionTotal,
    long extensionCancellationTotal,
    long extensionConfirmationFailureTotal,
    double extensionAverageRequestedDays,
    Map<String, String> extensionFeeConfigRefsByReason,
    long lockSyncSentTotal,
    long lockSyncAckedTotal,
    long lockSyncFailedTotal,
    long lockSyncDlqTotal,
    long lockSyncReconciledTotal,
    long lockAuditReportTotal,
    long lockReplayTotal,
    long lockReplayMismatchTotal,
    long lockCancellationTotal,
    long lockEvidenceExportTotal
  ) {}

  static String normalized(String value) {
    return value == null ? "" : value.trim();
  }

  static String upper(String value) {
    return normalized(value).toUpperCase(Locale.ROOT);
  }
}
