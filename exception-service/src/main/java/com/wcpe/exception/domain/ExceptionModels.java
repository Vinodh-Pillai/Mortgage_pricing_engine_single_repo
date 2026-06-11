package com.wcpe.exception.domain;

import java.time.Instant;
import java.util.*;

/**
 * Exception domain models, states, request/response types, and exception class.
 */
public final class ExceptionModels {

  private ExceptionModels() {}

  // ── State enum ──────────────────────────────────────────────────────────────

  public enum ExceptionState {
    DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED;

    static Set<ExceptionState> allowedTransitions(ExceptionState current) {
      return switch (current) {
        case DRAFT -> Set.of(SUBMITTED, CANCELLED);
        case SUBMITTED -> Set.of(APPROVED, REJECTED, CANCELLED);
        case APPROVED -> Set.of();
        case REJECTED -> Set.of();
        case CANCELLED -> Set.of();
      };
    }

    boolean isTerminal() {
      return this == APPROVED || this == REJECTED || this == CANCELLED;
    }

    static ExceptionState from(String value) {
      if (value == null) return null;
      try { return valueOf(value.toUpperCase(Locale.ROOT)); }
      catch (IllegalArgumentException e) { return null; }
    }
  }

  public enum ExceptionType {
    CONCESSION, EXCEPTION
  }

  // ── Request/Response records ────────────────────────────────────────────────

  public record ExceptionRequestCreate(
    String placeholderQuoteReference,
    ExceptionType requestType
  ) {}

  public record ExceptionRequestStatus(
    String exceptionRequestId,
    String placeholderQuoteReference,
    ExceptionType requestType,
    ExceptionState state,
    boolean mockBacked,
    boolean authoritativeIntegration,
    Instant createdAt,
    Instant updatedAt
  ) {}

  public record ExceptionTransitionRequest(
    ExceptionState requestedTransition
  ) {}

  public record ExceptionTransitionResponse(
    String exceptionRequestId,
    ExceptionState previousState,
    ExceptionState newState,
    ExceptionState requestedTransition,
    boolean mockBacked,
    boolean authoritativeIntegration,
    Instant transitionedAt
  ) {}

  public record ExceptionError(
    String code,
    String message,
    String requestId
  ) {}

  public enum ConcessionRequestStatus {
    SUBMITTED, NEEDS_ELIGIBILITY_EXCEPTION, APPROVED_PENDING_APPLICATION, APPLIED
  }

  public enum EligibilityExceptionRequestStatus {
    DRAFT, SUBMITTED, WITHDRAWN
  }

  public enum MonitoringSignalType {
    FREQUENCY, AMOUNT, APPROVAL_RATE, OVERRIDE_USAGE, SLA_BREACH, ROUTE_BYPASS, FAIRNESS_DISPARITY
  }

  public enum MonitoringPolicyStatus {
    PUBLISHED, SUSPENDED, RETIRED
  }

  public enum RiskEventSourceType {
    CONCESSION_REQUESTED,
    CONCESSION_APPROVED,
    CONCESSION_REJECTED,
    CONCESSION_APPLIED,
    CONCESSION_EXPIRED,
    CONCESSION_WITHDRAWN,
    CONCESSION_APPLICATION_FAILED,
    ELIGIBILITY_EXCEPTION_REQUESTED,
    ELIGIBILITY_EXCEPTION_APPROVED,
    ELIGIBILITY_EXCEPTION_REJECTED,
    ELIGIBILITY_EXCEPTION_EXPIRED,
    ELIGIBILITY_EXCEPTION_LINKED_CONCESSION_BLOCKED,
    ELIGIBILITY_EXCEPTION_LINKED_CONCESSION_UNBLOCKED,
    AUTHORITY_MATRIX_DRAFT_SUBMITTED,
    AUTHORITY_MATRIX_APPROVED,
    AUTHORITY_MATRIX_PUBLISHED,
    AUTHORITY_MATRIX_SUSPENDED,
    AUTHORITY_MATRIX_ROLLBACK,
    AUTHORITY_MATRIX_ROUTE_UNRESOLVED,
    MANUAL_PRICE_EDIT_BLOCKED,
    MANUAL_PRICE_EDIT_BYPASS_ATTEMPT,
    MANUAL_PRICE_EDIT_GOVERNED_COMMAND_ALLOWED,
    QUOTE_HASH_CHANGED_AFTER_APPROVAL,
    LOCK_BLOCKED_BY_PENDING_EXCEPTION,
    LOCK_BLOCKED_BY_REJECTED_EXCEPTION,
    CONCESSION_APPLIED_LOCK_UPDATE,
    FAIRNESS_ALERT_RAISED,
    FAIRNESS_ALERT_DISPOSITIONED,
    FAIRNESS_SMALL_CELL_SIGNAL_SUPPRESSED
  }

  public enum RiskMonitoringEventStatus {
    READY, SUPPRESSED
  }

  public enum AlertSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
  }

  public enum AlertStatus {
    OPEN, ACKNOWLEDGED, FALSE_POSITIVE, ESCALATED, REMEDIATED, CLOSED, SUPPRESSED
  }

  public enum AlertDispositionDecision {
    ACKNOWLEDGE, FALSE_POSITIVE, ESCALATE, REMEDIATE, CLOSE
  }

  public enum ExceptionHistorySubjectType {
    QUOTE, LOCK, CONCESSION_REQUEST, ELIGIBILITY_EXCEPTION, ACTOR, CORRELATION_ID
  }

  public enum ExceptionHistoryAction {
    REQUESTED, APPROVED, REJECTED, APPLIED, MONITORING_ALERT, ALERT_DISPOSITIONED, PRICE_MUTATION_BLOCKED
  }

  public enum ExceptionHistoryReplayStatus {
    MATCH, MISMATCH
  }

  public enum ApplicationTargetType {
    QUOTE, LOCK
  }

  public enum ApprovalDecisionType {
    APPROVE
  }

  public enum AuthorityMatrixVersionStatus {
    DRAFT, APPROVED, PUBLISHED, SUSPENDED
  }

  public enum PriceMutationGuardPolicyStatus {
    PUBLISHED, SUSPENDED, RETIRED
  }

  public enum PriceMutationTargetType {
    QUOTE, LOCK, PRICING_GRID, PARTNER_API, ADMIN_ADJUSTMENT
  }

  public enum PriceMutationGuardDecision {
    BLOCKED, ALLOWED
  }

  public enum ConcessionUnit {
    BASIS_POINTS, PRICE_POINTS, FEE_AMOUNT
  }

  public record AuthorityMatrixCondition(
    Map<String, String> dimensions
  ) {}

  public record AuthorityMatrixApprovalStep(
    String stepId,
    List<String> roleScopeRefs,
    String slaPolicyRef
  ) {}

  public record PriceFieldMutation(
    String fieldName,
    String previousValueHash,
    String proposedValueHash
  ) {}

  public record AuthorizedPricingCommandRef(
    String commandType,
    String commandId,
    String workflowCapabilityRef,
    String approvedAdjustmentRef
  ) {}

  public record LedgerHashExpectation(
    String expectedLedgerHash,
    String beforeQuoteHash,
    String beforeLockHash
  ) {}

  public record PriceMutationGuardPolicyVersion(
    String policyVersionId,
    PriceMutationGuardPolicyStatus status,
    List<String> guardedFields,
    List<String> allowedCommandTypes,
    List<String> workflowCapabilityRefs,
    String effectiveFrom
  ) {}

  public record GuardPriceMutationCommand(
    UUID tenantId,
    PriceMutationTargetType targetType,
    String quoteId,
    String lockId,
    String sourceSurface,
    List<PriceFieldMutation> fieldMutations,
    AuthorizedPricingCommandRef authorizedCommandRef,
    LedgerHashExpectation ledgerHashExpectation,
    String actorId,
    String idempotencyKey,
    String correlationId
  ) {}

  public record BlockedMutationEvidence(
    String attemptId,
    PriceMutationTargetType targetType,
    String sourceSurface,
    String quoteId,
    String lockId,
    List<String> guardedFields,
    String payloadHash,
    String denialReason,
    String policyVersionId,
    String auditRef,
    String outboxEventType,
    String eventHash,
    String correlationId,
    Instant createdAt
  ) {}

  public record PriceMutationGuardResponse(
    UUID tenantId,
    PriceMutationGuardDecision decision,
    String resultSummary,
    List<String> validationMessages,
    BlockedMutationEvidence blockedEvidence,
    String replayHash,
    String correlationId
  ) {}

  public record AuthorityMatrixRuleDraft(
    String ruleId,
    String requestType,
    AuthorityMatrixCondition condition,
    String amountUnit,
    String amountMin,
    String amountMax,
    List<AuthorityMatrixApprovalStep> approvalSteps,
    int priority,
    String failClosedReason
  ) {}

  public record CreateAuthorityMatrixDraftCommand(
    UUID tenantId,
    String versionLabel,
    String sourceVersionId,
    List<AuthorityMatrixRuleDraft> rules,
    String actorId,
    String idempotencyKey,
    String correlationId
  ) {}

  public record ApproveAuthorityMatrixCommand(
    UUID tenantId,
    String matrixVersionId,
    String actorId,
    List<String> actorRoleRefs,
    ConflictAttestation conflictAttestation,
    String approvalTicketRef,
    String idempotencyKey,
    String correlationId,
    int expectedVersion
  ) {}

  public record PublishAuthorityMatrixCommand(
    UUID tenantId,
    String matrixVersionId,
    String actorId,
    String effectiveFrom,
    String idempotencyKey,
    String correlationId,
    int expectedVersion
  ) {}

  public record ResolveAuthorityMatrixCommand(
    UUID tenantId,
    String requestType,
    Map<String, String> dimensions,
    String amountUnit,
    String amountValue,
    String effectiveAt,
    String correlationId
  ) {}

  public record AuthorityMatrixValidationMessage(
    String code,
    String message,
    String ruleId
  ) {}

  public record AuthorityMatrixVersionResponse(
    UUID tenantId,
    String matrixVersionId,
    AuthorityMatrixVersionStatus status,
    String versionLabel,
    String sourceVersionId,
    List<AuthorityMatrixRuleDraft> rules,
    List<AuthorityMatrixValidationMessage> validationMessages,
    String validationHash,
    String approvalTicketRef,
    String submittedBy,
    String approvedBy,
    String publishedBy,
    String effectiveFrom,
    String auditRef,
    String outboxEventType,
    String eventHash,
    String idempotencyKey,
    String correlationId,
    int version,
    Instant createdAt,
    Instant updatedAt
  ) {}

  public record AuthorityMatrixResolutionResponse(
    UUID tenantId,
    String matrixVersionId,
    String matchedRuleId,
    List<AuthorityMatrixApprovalStep> approvalSteps,
    String routeHash,
    String failClosedReason,
    String correlationId
  ) {}

  public record ConcessionAmount(
    ConcessionUnit unit,
    String value,
    String currency
  ) {}

  public record ConcessionEvidenceRef(
    String evidenceUri,
    String evidenceType,
    String checksum
  ) {}

  public record ApprovalRouteSnapshot(
    String authorityMatrixVersionId,
    List<String> approverGroups,
    String sla,
    boolean ambiguous
  ) {}

  public record EligibilityFindingRef(
    String eligibilityResultId,
    String findingId,
    String ruleCode,
    String ruleVersionId,
    String severity,
    boolean exceptionable,
    String originalResultHash
  ) {}

  public record EligibilityExceptionScope(
    String scopeType,
    Map<String, String> attributes
  ) {}

  public record CompensatingFactor(
    String factorType,
    String description
  ) {}

  public record EligibilityExceptionEvidenceRef(
    String evidenceUri,
    String evidenceType,
    String checksum,
    String uploadedBy
  ) {}

  public record EligibilityExceptionPolicy(
    String policyVersionId,
    String authorityMatrixVersionId,
    boolean findingExceptionable,
    boolean quoteStateAllowsRequest,
    boolean requiredEvidence,
    ApprovalRouteSnapshot approvalRouteSnapshot
  ) {}

  public record CreateEligibilityExceptionRequest(
    UUID tenantId,
    String quoteId,
    String scenarioId,
    String lockId,
    EligibilityFindingRef findingRef,
    EligibilityExceptionScope exceptionScope,
    String reasonCode,
    String narrative,
    List<CompensatingFactor> compensatingFactors,
    List<EligibilityExceptionEvidenceRef> evidenceRefs,
    String desiredExpiration,
    String relatedConcessionRequestId,
    String actorId,
    String idempotencyKey,
    String correlationId
  ) {}

  public record EligibilityExceptionRequestResponse(
    UUID tenantId,
    String exceptionRequestId,
    String quoteId,
    String scenarioId,
    String lockId,
    EligibilityFindingRef findingRef,
    EligibilityExceptionScope exceptionScope,
    String reasonCode,
    List<CompensatingFactor> compensatingFactors,
    List<EligibilityExceptionEvidenceRef> evidenceRefs,
    String desiredExpiration,
    String relatedConcessionRequestId,
    EligibilityExceptionRequestStatus status,
    String policyVersionId,
    String authorityMatrixVersionId,
    String approvalRouteHash,
    String originalResultHash,
    String auditRef,
    String outboxEventType,
    String eventHash,
    String idempotencyKey,
    String actorId,
    String correlationId,
    int version,
    Instant createdAt,
    Instant updatedAt
  ) {}

  public record ApprovalConditions(
    String expiresAt,
    Map<String, String> conditions
  ) {}

  public record ConflictAttestation(
    boolean noConflict,
    String attestationText
  ) {}

  public record ApproveConcessionRequest(
    UUID tenantId,
    String concessionRequestId,
    String routeStepId,
    ApprovalDecisionType decision,
    String reasonCode,
    String comment,
    ApprovalConditions conditions,
    ConflictAttestation conflictAttestation,
    String authorityMatrixVersionId,
    String actorId,
    List<String> actorRoleRefs,
    String idempotencyKey,
    String correlationId,
    int expectedRequestVersion
  ) {}

  public record ConcessionApprovalResponse(
    UUID tenantId,
    String concessionRequestId,
    String decisionId,
    ConcessionRequestStatus previousStatus,
    ConcessionRequestStatus newStatus,
    String completedStep,
    String nextStep,
    int aggregateVersion,
    String auditRef,
    String outboxEventType,
    String eventHash,
    String correlationId,
    Instant decidedAt
  ) {}

  public record ApplicationTarget(
    ApplicationTargetType targetType,
    String quoteId,
    String lockId,
    String currentQuoteSnapshotHash,
    String currentLockState
  ) {}

  public record ApplicationPrecedence(
    String precedenceConfigVersionId,
    int scale,
    String roundingMode
  ) {}

  public record ApplyApprovedConcessionRequest(
    UUID tenantId,
    String concessionRequestId,
    ApplicationTarget target,
    int expectedRequestVersion,
    String expectedQuoteSnapshotHash,
    String expectedLedgerHash,
    String pricingRuleVersionId,
    String policyVersionId,
    ApplicationPrecedence precedence,
    boolean eligibilityExceptionsResolved,
    String actorId,
    String idempotencyKey,
    String correlationId
  ) {}

  public record ConcessionApplicationResponse(
    UUID tenantId,
    String applicationId,
    String concessionRequestId,
    ApplicationTargetType targetType,
    String quoteId,
    String lockId,
    ConcessionRequestStatus status,
    String pricingLedgerEntryId,
    String beforePriceHash,
    String afterPriceHash,
    String pricingRuleVersionId,
    String policyVersionId,
    String precedenceConfigVersionId,
    String auditRef,
    String outboxEventType,
    String replayHash,
    String correlationId,
    int version,
    Instant appliedAt
  ) {}

  public record PricingConcessionRequestCreate(
    UUID tenantId,
    String quoteId,
    String scenarioId,
    String lockId,
    ConcessionAmount requestedAmount,
    String reasonCode,
    String narrative,
    List<ConcessionEvidenceRef> evidenceRefs,
    String expiration,
    String actorId,
    String idempotencyKey,
    String correlationId,
    String concessionPolicyVersionId,
    String reasonCodeVersionId,
    String quoteSnapshotHash,
    ApprovalRouteSnapshot approvalRouteSnapshot,
    boolean eligibilityExceptionRequired
  ) {}

  public record PricingConcessionRequestStatus(
    UUID tenantId,
    String concessionRequestId,
    String quoteId,
    String scenarioId,
    String lockId,
    ConcessionRequestStatus status,
    ConcessionAmount requestedAmount,
    String reasonCode,
    String commentsRedacted,
    List<ConcessionEvidenceRef> evidenceRefs,
    String expiration,
    String concessionPolicyVersionId,
    String authorityMatrixVersionId,
    String reasonCodeVersionId,
    String quoteSnapshotHash,
    String approvalRouteHash,
    List<String> nextApproverGroups,
    String sla,
    String idempotencyKey,
    String actorId,
    String correlationId,
    String auditRef,
    String outboxEventType,
    String requestHash,
    int version,
    Instant createdAt,
    Instant updatedAt
  ) {}

  public record MonitoringPolicyVersion(
    String detectorId,
    String detectorVersionId,
    MonitoringPolicyStatus status,
    String policyConfigRef,
    AlertSeverity severity,
    String monitoringWindow,
    List<String> groupingDimensions,
    Integer minimumCellSize,
    boolean fairnessCohortViewAuthorized
  ) {}

  public record ConcessionMonitoringSignalCommand(
    UUID tenantId,
    String sourceEventId,
    String concessionRequestId,
    String applicationId,
    String approvalDecisionId,
    MonitoringSignalType signalType,
    Map<String, String> dimensions,
    Map<String, String> measurements,
    String fairnessCohortRef,
    Integer fairnessCohortCellCount,
    String actorId,
    String idempotencyKey,
    String correlationId
  ) {}

  public record ConcessionMonitoringAlertResponse(
    UUID tenantId,
    String alertId,
    String signalId,
    String detectorId,
    String detectorVersionId,
    AlertSeverity severity,
    AlertStatus status,
    List<String> sourceEventIds,
    Map<String, String> evidenceSnapshot,
    String fairnessCohortRef,
    boolean fairnessCohortSuppressed,
    String auditRef,
    String outboxEventType,
    String evidenceHash,
    String replayHash,
    String correlationId,
    int version,
    Instant openedAt,
    Instant updatedAt
  ) {}

  public record RiskEventMappingRule(
    RiskEventSourceType sourceEventType,
    MonitoringSignalType signalType,
    AlertSeverity severity,
    String category,
    String schemaVersion,
    String routingHint,
    boolean suppressionEligible
  ) {}

  public record RiskEventMappingVersion(
    String mappingVersionId,
    MonitoringPolicyStatus status,
    String topic,
    String eventVersion,
    String partitionStrategy,
    List<RiskEventMappingRule> mappings,
    List<String> prohibitedPayloadFields,
    Set<MonitoringSignalType> suppressedSignalTypes,
    String approvedBy,
    String publishedAt
  ) {}

  public record MapRiskMonitoringEventCommand(
    UUID tenantId,
    RiskEventSourceType sourceEventType,
    String sourceEventId,
    String sourceService,
    String riskSubjectRef,
    Map<String, String> sourceRefs,
    Map<String, String> payload,
    String actorId,
    String idempotencyKey,
    String correlationId,
    String causationId,
    String traceId
  ) {}

  public record RiskMonitoringEventEnvelope(
    UUID tenantId,
    String riskEventId,
    String sourceEventId,
    MonitoringSignalType signalType,
    AlertSeverity severity,
    RiskMonitoringEventStatus status,
    String topic,
    String eventKey,
    Map<String, String> headers,
    Map<String, String> payload,
    String payloadHash,
    Map<String, String> redactionManifest,
    String mappingVersionId,
    String schemaVersion,
    String auditRef,
    String outboxEventType,
    boolean replayFlag,
    Instant createdAt
  ) {}

  public record ConcessionAlertDispositionCommand(
    UUID tenantId,
    String alertId,
    AlertDispositionDecision decision,
    String reasonCode,
    String comment,
    String actorId,
    String idempotencyKey,
    String correlationId
  ) {}

  public record ConcessionAlertDispositionResponse(
    UUID tenantId,
    String dispositionId,
    String alertId,
    AlertStatus previousStatus,
    AlertStatus newStatus,
    String reasonCode,
    String commentRedacted,
    String actorId,
    String auditRef,
    String outboxEventType,
    String dispositionHash,
    String correlationId,
    int alertVersion,
    Instant createdAt
  ) {}

  public record ExceptionHistorySearchRequest(
    UUID tenantId,
    ExceptionHistorySubjectType subjectType,
    String subjectId,
    String actorId,
    Set<String> permissions,
    boolean includeRawJson,
    String correlationId
  ) {}

  public record TimelineEvent(
    Instant occurredAt,
    String eventId,
    String aggregateRef,
    int aggregateVersion,
    String actorId,
    ExceptionHistoryAction action,
    String statusTransition,
    String reason,
    ConcessionAmount amount,
    String targetQuoteId,
    String targetLockId,
    Map<String, String> configVersions,
    String eventType,
    String eventHash,
    boolean hashMatch,
    boolean rawJsonAvailable,
    boolean fieldLevelRestricted,
    Map<String, String> evidenceRefs
  ) {}

  public record VersionGraph(
    List<String> eventIds,
    Map<String, String> configVersions,
    String graphHash
  ) {}

  public record ExceptionHistoryTimeline(
    UUID tenantId,
    ExceptionHistorySubjectType subjectType,
    String subjectId,
    List<TimelineEvent> events,
    VersionGraph versionGraph,
    String projectionHash,
    String auditRef,
    String correlationId,
    Instant rebuiltAt
  ) {}

  public record ExceptionHistoryReplayResult(
    UUID tenantId,
    String replayId,
    ExceptionHistorySubjectType subjectType,
    String subjectId,
    List<String> inputEventIds,
    List<String> configVersionIds,
    String expectedHash,
    String actualHash,
    ExceptionHistoryReplayStatus status,
    String mismatchClassification,
    String replayHash,
    String auditRef,
    String outboxEventType,
    String correlationId,
    Instant createdAt
  ) {}

  public record ExceptionHistoryExportManifest(
    String exportId,
    String manifestHash,
    String signature,
    String redactionMode,
    Set<String> includedPermissions,
    List<String> excludedFields,
    String storageRef,
    Instant expiresAt
  ) {}

  public record ExceptionHistoryExportPacket(
    UUID tenantId,
    ExceptionHistorySubjectType subjectType,
    String subjectId,
    ExceptionHistoryTimeline timeline,
    ExceptionHistoryReplayResult replay,
    ExceptionHistoryExportManifest manifest,
    String auditRef,
    String outboxEventType,
    String correlationId,
    Instant createdAt
  ) {}

  public record ExceptionWorkbenchSection(
    String sectionId,
    String label,
    String status,
    List<String> backendRefs,
    List<String> auditRefs,
    String summary
  ) {}

  public record ExceptionWorkbenchMutationGuard(
    boolean commitDisabled,
    PriceMutationGuardDecision decision,
    List<String> reasonCodes,
    String escalationPath,
    String auditRef,
    String replayHash
  ) {}

  public record ExceptionWorkbenchCase(
    UUID tenantId,
    String caseId,
    String quoteId,
    String status,
    List<ExceptionWorkbenchSection> sections,
    ExceptionWorkbenchMutationGuard manualPriceMutationGuard,
    List<String> crossServiceRefs,
    List<String> versionRefs,
    List<String> auditRefs,
    List<String> blockers,
    String replayHash,
    String exportManifestRef,
    String fallbackReason,
    Instant assembledAt
  ) {}

  public record ExceptionHistoryProjectionRecord(
    UUID tenantId,
    String projectionId,
    ExceptionHistorySubjectType subjectType,
    String subjectId,
    ExceptionHistoryTimeline timeline,
    VersionGraph versionGraph,
    Instant latestEventAt,
    String projectionHash,
    Instant rebuiltAt
  ) {}

  public record ExceptionHistoryAuditRecord(
    UUID tenantId,
    String auditId,
    String action,
    ExceptionHistorySubjectType subjectType,
    String subjectId,
    String actorId,
    String purpose,
    String resultHash,
    String correlationId,
    Instant createdAt
  ) {}

  // ── Internal request record (used by repository/service) ────────────────────

  public record ExceptionRequestRecord(
    String exceptionRequestId,
    String placeholderQuoteReference,
    ExceptionType requestType,
    ExceptionState state,
    Instant createdAt,
    Instant updatedAt
  ) {}

  public record PricingConcessionRequestRecord(
    UUID tenantId,
    String concessionRequestId,
    String quoteId,
    String scenarioId,
    String lockId,
    ConcessionRequestStatus status,
    ConcessionAmount requestedAmount,
    String reasonCode,
    String commentsRedacted,
    List<ConcessionEvidenceRef> evidenceRefs,
    String expiration,
    String concessionPolicyVersionId,
    String authorityMatrixVersionId,
    String reasonCodeVersionId,
    String quoteSnapshotHash,
    String approvalRouteHash,
    List<String> nextApproverGroups,
    String sla,
    String idempotencyKey,
    String actorId,
    String correlationId,
    String auditRef,
    String outboxEventType,
    String requestHash,
    int version,
    Instant createdAt,
    Instant updatedAt
  ) {}

  public record EligibilityExceptionRequestRecord(
    UUID tenantId,
    String exceptionRequestId,
    String quoteId,
    String scenarioId,
    String lockId,
    EligibilityFindingRef findingRef,
    EligibilityExceptionScope exceptionScope,
    String reasonCode,
    String narrativeRedacted,
    List<CompensatingFactor> compensatingFactors,
    List<EligibilityExceptionEvidenceRef> evidenceRefs,
    String desiredExpiration,
    String relatedConcessionRequestId,
    EligibilityExceptionRequestStatus status,
    String policyVersionId,
    String authorityMatrixVersionId,
    String approvalRouteHash,
    String originalResultHash,
    String auditRef,
    String outboxEventType,
    String eventHash,
    String requestHash,
    String idempotencyKey,
    String actorId,
    String correlationId,
    int version,
    Instant createdAt,
    Instant updatedAt
  ) {}

  public record ApprovalDecisionRecord(
    UUID tenantId,
    String decisionId,
    String concessionRequestId,
    String routeStepId,
    ApprovalDecisionType decision,
    String decisionReasonCode,
    String decisionCommentRedacted,
    ApprovalConditions conditions,
    String authorityMatrixVersionId,
    String actorId,
    List<String> actorRoleRefs,
    ConflictAttestation conflictAttestation,
    String idempotencyKey,
    String correlationId,
    String auditRef,
    String outboxEventType,
    String eventHash,
    int aggregateVersion,
    Instant createdAt
  ) {}

  public record ConcessionApplicationRecord(
    UUID tenantId,
    String applicationId,
    String concessionRequestId,
    ApplicationTargetType targetType,
    String quoteId,
    String lockId,
    ConcessionAmount appliedAmount,
    String pricingLedgerEntryId,
    String beforePriceHash,
    String afterPriceHash,
    String pricingRuleVersionId,
    String policyVersionId,
    String precedenceConfigVersionId,
    int scale,
    String roundingMode,
    ConcessionRequestStatus status,
    String idempotencyKey,
    String appliedBy,
    String correlationId,
    String auditRef,
    String outboxEventType,
    String replayHash,
    int version,
    Instant appliedAt
  ) {}

  public record MonitoringSignalRecord(
    UUID tenantId,
    String signalId,
    String sourceEventId,
    String concessionRequestId,
    String applicationId,
    String approvalDecisionId,
    MonitoringSignalType signalType,
    String detectorId,
    String detectorVersionId,
    Map<String, String> dimensions,
    Map<String, String> measurements,
    String actorId,
    String idempotencyKey,
    String correlationId,
    String signalHash,
    Instant observedAt
  ) {}

  public record MonitoringAlertRecord(
    UUID tenantId,
    String alertId,
    String signalId,
    String detectorId,
    String detectorVersionId,
    AlertSeverity severity,
    AlertStatus status,
    List<String> sourceEventIds,
    Map<String, String> evidenceSnapshot,
    String fairnessCohortRef,
    boolean fairnessCohortSuppressed,
    String auditRef,
    String outboxEventType,
    String evidenceHash,
    String replayHash,
    String correlationId,
    int version,
    Instant openedAt,
    Instant updatedAt
  ) {}

  public record RiskMonitoringEventRecord(
    UUID tenantId,
    String riskEventId,
    RiskEventSourceType sourceEventType,
    String sourceEventId,
    MonitoringSignalType signalType,
    AlertSeverity severity,
    RiskMonitoringEventStatus status,
    String topic,
    String eventKey,
    Map<String, String> headers,
    Map<String, String> payload,
    String payloadHash,
    Map<String, String> redactionManifest,
    String mappingVersionId,
    String schemaVersion,
    String auditRef,
    String outboxEventType,
    boolean replayFlag,
    String idempotencyKey,
    String requestHash,
    String correlationId,
    Instant createdAt
  ) {}

  public record AlertDispositionRecord(
    UUID tenantId,
    String dispositionId,
    String alertId,
    AlertStatus previousStatus,
    AlertStatus newStatus,
    String reasonCode,
    String commentRedacted,
    String actorId,
    String idempotencyKey,
    String correlationId,
    String auditRef,
    String outboxEventType,
    String dispositionHash,
    int alertVersion,
    Instant createdAt
  ) {}

  public record AuthorityMatrixVersionRecord(
    UUID tenantId,
    String matrixVersionId,
    AuthorityMatrixVersionStatus status,
    String versionLabel,
    String sourceVersionId,
    List<AuthorityMatrixRuleDraft> rules,
    List<AuthorityMatrixValidationMessage> validationMessages,
    String validationHash,
    String approvalTicketRef,
    String submittedBy,
    String approvedBy,
    String publishedBy,
    String effectiveFrom,
    String auditRef,
    String outboxEventType,
    String eventHash,
    String requestHash,
    String idempotencyKey,
    String correlationId,
    int version,
    Instant createdAt,
    Instant updatedAt
  ) {}

  public record ManualPriceEditAttemptRecord(
    UUID tenantId,
    String attemptId,
    String actorId,
    String sourceSurface,
    PriceMutationTargetType targetType,
    String quoteId,
    String lockId,
    List<String> fieldNames,
    String payloadHash,
    String denialReason,
    String policyVersionId,
    String auditRef,
    String outboxEventType,
    String eventHash,
    String idempotencyKey,
    String correlationId,
    Instant createdAt
  ) {}
}
