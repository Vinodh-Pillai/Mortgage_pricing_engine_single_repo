package com.wcpe.lock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LockRenegotiationApi {
  public static final String POST_PREVIEW_METHOD = "POST";
  public static final String POST_PREVIEW_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/renegotiations/preview";
  public static final String POST_RENEGOTIATION_METHOD = "POST";
  public static final String POST_RENEGOTIATION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/renegotiations";
  public static final String POST_DECISION_METHOD = "POST";
  public static final String POST_DECISION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/renegotiations/{renegotiationId}/decisions";
  public static final String POST_CONFIRMATION_METHOD = "POST";
  public static final String POST_CONFIRMATION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/renegotiations/{renegotiationId}/confirmations";
  public static final String POST_WITHDRAW_METHOD = "POST";
  public static final String POST_WITHDRAW_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/renegotiations/{renegotiationId}/withdraw";
  public static final String REQUEST_PERMISSION = "LOCK_RENEGOTIATION_REQUEST";
  public static final String APPROVE_PERMISSION = "LOCK_RENEGOTIATION_APPROVE";
  public static final String CONFIRM_PERMISSION = "LOCK_RENEGOTIATION_CONFIRM";
  public static final String WITHDRAW_PERMISSION = "LOCK_RENEGOTIATION_WITHDRAW";

  private final LockService service;

  public LockRenegotiationApi(LockService service) {
    if (service == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock service is required");
    }
    this.service = service;
  }

  public RenegotiationPreviewResponse postPreview(
    UUID tenantId,
    String lockId,
    String idempotencyKey,
    String correlationId,
    RenegotiationPreviewRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "renegotiation preview request is required");
    }
    return RenegotiationPreviewResponse.from(service.previewRelock(new LockModels.RelockPreviewCommand(
      tenantId, lockId, request.actorId(), request.expectedVersion(), request.currentQuoteId(), request.originalTerms(),
      request.currentTerms(), request.selectedTerms(), request.policySnapshot(), request.reasonCode(), request.permissionGranted(),
      idempotencyKey, correlationId, request.sourceRefs()
    )));
  }

  public RenegotiationResponse postRenegotiation(
    UUID tenantId,
    String lockId,
    String idempotencyKey,
    String correlationId,
    RenegotiationRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "renegotiation request is required");
    }
    return RenegotiationResponse.from(service.requestRelockReplayAware(new LockModels.RelockRequestCommand(
      tenantId, lockId, request.requestId(), request.actorId(), request.expectedVersion(), request.currentQuoteId(),
      request.requestedAt(), request.originalTerms(), request.currentTerms(), request.selectedTerms(), request.policySnapshot(),
      request.reasonCode(), request.complianceEvidenceRef(), request.permissionGranted(), idempotencyKey, correlationId,
      request.sourceRefs()
    )));
  }

  public RenegotiationResponse postDecision(
    UUID tenantId,
    String lockId,
    String renegotiationId,
    String idempotencyKey,
    String correlationId,
    RenegotiationDecisionRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "renegotiation decision request is required");
    }
    return RenegotiationResponse.from(service.decideRelock(new LockModels.RelockDecisionCommand(
      tenantId, lockId, renegotiationId, request.decision(), request.actorId(), request.requesterActorId(), request.expectedVersion(),
      request.permissionGranted(), request.separationOfDutiesConfigured(), request.decisionPolicyCurrent(), request.reasonCodes(),
      request.complianceEvidenceRef(), idempotencyKey, correlationId, request.decidedAt()
    )));
  }

  public RenegotiationResponse postConfirmation(
    UUID tenantId,
    String lockId,
    String renegotiationId,
    String idempotencyKey,
    String correlationId,
    RenegotiationConfirmationRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "renegotiation confirmation request is required");
    }
    return RenegotiationResponse.from(service.confirmRelock(new LockModels.RelockConfirmationCommand(
      tenantId, lockId, renegotiationId, request.actorId(), request.expectedVersion(), request.permissionGranted(),
      request.investorResponseMatches(), request.investorConfirmationRef(), request.complianceEvidenceRef(), idempotencyKey,
      correlationId, request.confirmedAt(), request.sourceRefs()
    )));
  }

  public RenegotiationResponse postWithdraw(
    UUID tenantId,
    String lockId,
    String renegotiationId,
    String idempotencyKey,
    String correlationId,
    RenegotiationWithdrawRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "renegotiation withdraw request is required");
    }
    return RenegotiationResponse.from(service.cancelRelock(new LockModels.RelockCancelCommand(
      tenantId, lockId, renegotiationId, request.actorId(), request.expectedVersion(), request.permissionGranted(), request.reasonCodes(),
      request.complianceEvidenceRef(), idempotencyKey, correlationId, request.withdrawnAt()
    )));
  }

  public record RenegotiationPreviewRequest(
    String actorId,
    int expectedVersion,
    String currentQuoteId,
    LockModels.RelockTermsSnapshot originalTerms,
    LockModels.RelockTermsSnapshot currentTerms,
    LockModels.RelockTermsSnapshot selectedTerms,
    LockModels.RelockPolicySnapshot policySnapshot,
    String reasonCode,
    boolean permissionGranted,
    Map<String, String> sourceRefs
  ) {}

  public record RenegotiationRequest(
    String requestId,
    String actorId,
    int expectedVersion,
    String currentQuoteId,
    Instant requestedAt,
    LockModels.RelockTermsSnapshot originalTerms,
    LockModels.RelockTermsSnapshot currentTerms,
    LockModels.RelockTermsSnapshot selectedTerms,
    LockModels.RelockPolicySnapshot policySnapshot,
    String reasonCode,
    String complianceEvidenceRef,
    boolean permissionGranted,
    Map<String, String> sourceRefs
  ) {}

  public record RenegotiationDecisionRequest(
    LockModels.RelockDecisionType decision,
    String actorId,
    String requesterActorId,
    int expectedVersion,
    boolean permissionGranted,
    boolean separationOfDutiesConfigured,
    boolean decisionPolicyCurrent,
    List<String> reasonCodes,
    String complianceEvidenceRef,
    Instant decidedAt
  ) {}

  public record RenegotiationConfirmationRequest(
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    boolean investorResponseMatches,
    String investorConfirmationRef,
    String complianceEvidenceRef,
    Instant confirmedAt,
    Map<String, String> sourceRefs
  ) {}

  public record RenegotiationWithdrawRequest(
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    List<String> reasonCodes,
    String complianceEvidenceRef,
    Instant withdrawnAt
  ) {}

  public record RenegotiationPreviewResponse(
    String renegotiationId,
    String lockId,
    String sourceStatus,
    String resultSummary,
    List<String> validationMessages,
    String replayRef,
    String correlationId,
    String benefitLedgerHash
  ) {
    static RenegotiationPreviewResponse from(LockModels.RelockResponse response) {
      return new RenegotiationPreviewResponse(
        response.relockId(), response.sourceLockId(), response.sourceStatus().name(), response.resultSummary(),
        response.validationMessages(), response.replayRef(), response.correlationId(), response.comparisonHash()
      );
    }
  }

  public record RenegotiationResponse(
    String id,
    String lockId,
    String replacementLockId,
    String renegotiationStatus,
    String lockStatus,
    String replacementStatus,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType,
    String benefitLedgerHash
  ) {
    static RenegotiationResponse from(LockModels.RelockResponse response) {
      return new RenegotiationResponse(
        response.relockId(), response.sourceLockId(), response.replacementLockId(), response.relockStatus().name(),
        response.sourceStatus().name(), response.replacementStatus().name(), response.version(), response.resultSummary(),
        response.validationMessages(), response.auditRef(), response.replayRef(), response.correlationId(),
        renegotiationEvent(response.outboxEventType()), response.comparisonHash()
      );
    }
  }

  private static String renegotiationEvent(String eventType) {
    if ("lock.relock_requested.v1".equals(eventType)) return "lock.renegotiation_requested.v1";
    if ("lock.relock_approved.v1".equals(eventType)) return "lock.float_down_approved.v1";
    if ("lock.relocked.v1".equals(eventType)) return "lock.terms_amended.v1";
    if ("lock.relock_rejected.v1".equals(eventType)) return "lock.renegotiation_rejected.v1";
    if ("lock.relock_cancelled.v1".equals(eventType)) return "lock.renegotiation_withdrawn.v1";
    return eventType;
  }
}
