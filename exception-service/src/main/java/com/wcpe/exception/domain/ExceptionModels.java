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
    SUBMITTED, NEEDS_ELIGIBILITY_EXCEPTION, APPROVED_PENDING_APPLICATION
  }

  public enum ApprovalDecisionType {
    APPROVE
  }

  public enum ConcessionUnit {
    BASIS_POINTS, PRICE_POINTS, FEE_AMOUNT
  }

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
}
