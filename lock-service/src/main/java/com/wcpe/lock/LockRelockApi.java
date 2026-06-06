package com.wcpe.lock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LockRelockApi {
  public static final String POST_PREVIEW_METHOD = "POST";
  public static final String POST_PREVIEW_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/relocks/preview";
  public static final String POST_RELOCK_METHOD = "POST";
  public static final String POST_RELOCK_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/relocks";
  public static final String POST_DECISION_METHOD = "POST";
  public static final String POST_DECISION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/relocks/{relockId}/decisions";
  public static final String POST_CONFIRMATION_METHOD = "POST";
  public static final String POST_CONFIRMATION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/relocks/{relockId}/confirmations";
  public static final String POST_CANCEL_METHOD = "POST";
  public static final String POST_CANCEL_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/relocks/{relockId}/cancel";
  public static final String REQUEST_PERMISSION = "LOCK_RELOCK_REQUEST";
  public static final String APPROVE_PERMISSION = "LOCK_RELOCK_APPROVE";
  public static final String CONFIRM_PERMISSION = "LOCK_RELOCK_CONFIRM";
  public static final String CANCEL_PERMISSION = "LOCK_RELOCK_CANCEL";

  private final LockService service;

  public LockRelockApi(LockService service) {
    if (service == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock service is required");
    }
    this.service = service;
  }

  public RelockPreviewResponse postPreview(
    UUID tenantId,
    String lockId,
    String idempotencyKey,
    String correlationId,
    RelockPreviewRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock preview request is required");
    }
    return RelockPreviewResponse.from(service.previewRelock(new LockModels.RelockPreviewCommand(
      tenantId, lockId, request.actorId(), request.expectedVersion(), request.currentQuoteId(), request.originalTerms(),
      request.currentTerms(), request.selectedTerms(), request.policySnapshot(), request.reasonCode(), request.permissionGranted(),
      idempotencyKey, correlationId, request.sourceRefs()
    )));
  }

  public RelockResponse postRelock(
    UUID tenantId,
    String lockId,
    String idempotencyKey,
    String correlationId,
    RelockRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock request is required");
    }
    return RelockResponse.from(service.requestRelockReplayAware(new LockModels.RelockRequestCommand(
      tenantId, lockId, request.requestId(), request.actorId(), request.expectedVersion(), request.currentQuoteId(),
      request.requestedAt(), request.originalTerms(), request.currentTerms(), request.selectedTerms(), request.policySnapshot(),
      request.reasonCode(), request.complianceEvidenceRef(), request.permissionGranted(), idempotencyKey, correlationId,
      request.sourceRefs()
    )));
  }

  public RelockResponse postDecision(
    UUID tenantId,
    String lockId,
    String relockId,
    String idempotencyKey,
    String correlationId,
    RelockDecisionRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock decision request is required");
    }
    return RelockResponse.from(service.decideRelock(new LockModels.RelockDecisionCommand(
      tenantId, lockId, relockId, request.decision(), request.actorId(), request.requesterActorId(), request.expectedVersion(),
      request.permissionGranted(), request.separationOfDutiesConfigured(), request.decisionPolicyCurrent(), request.reasonCodes(),
      request.complianceEvidenceRef(), idempotencyKey, correlationId, request.decidedAt()
    )));
  }

  public RelockResponse postConfirmation(
    UUID tenantId,
    String lockId,
    String relockId,
    String idempotencyKey,
    String correlationId,
    RelockConfirmationRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock confirmation request is required");
    }
    return RelockResponse.from(service.confirmRelock(new LockModels.RelockConfirmationCommand(
      tenantId, lockId, relockId, request.actorId(), request.expectedVersion(), request.permissionGranted(),
      request.investorResponseMatches(), request.investorConfirmationRef(), request.complianceEvidenceRef(), idempotencyKey,
      correlationId, request.confirmedAt(), request.sourceRefs()
    )));
  }

  public RelockResponse postCancel(
    UUID tenantId,
    String lockId,
    String relockId,
    String idempotencyKey,
    String correlationId,
    RelockCancelRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock cancellation request is required");
    }
    return RelockResponse.from(service.cancelRelock(new LockModels.RelockCancelCommand(
      tenantId, lockId, relockId, request.actorId(), request.expectedVersion(), request.permissionGranted(), request.reasonCodes(),
      request.complianceEvidenceRef(), idempotencyKey, correlationId, request.cancelledAt()
    )));
  }

  public record RelockPreviewRequest(
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

  public record RelockRequest(
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

  public record RelockDecisionRequest(
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

  public record RelockConfirmationRequest(
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    boolean investorResponseMatches,
    String investorConfirmationRef,
    String complianceEvidenceRef,
    Instant confirmedAt,
    Map<String, String> sourceRefs
  ) {}

  public record RelockCancelRequest(
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    List<String> reasonCodes,
    String complianceEvidenceRef,
    Instant cancelledAt
  ) {}

  public record RelockPreviewResponse(
    String relockId,
    String sourceLockId,
    String sourceStatus,
    String resultSummary,
    List<String> validationMessages,
    String replayRef,
    String correlationId,
    String comparisonHash
  ) {
    static RelockPreviewResponse from(LockModels.RelockResponse response) {
      return new RelockPreviewResponse(
        response.relockId(), response.sourceLockId(), response.sourceStatus().name(), response.resultSummary(),
        response.validationMessages(), response.replayRef(), response.correlationId(), response.comparisonHash()
      );
    }
  }

  public record RelockResponse(
    String id,
    String sourceLockId,
    String replacementLockId,
    String relockStatus,
    String sourceStatus,
    String replacementStatus,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType
  ) {
    static RelockResponse from(LockModels.RelockResponse response) {
      return new RelockResponse(
        response.relockId(), response.sourceLockId(), response.replacementLockId(), response.relockStatus().name(),
        response.sourceStatus().name(), response.replacementStatus().name(), response.version(), response.resultSummary(),
        response.validationMessages(), response.auditRef(), response.replayRef(), response.correlationId(), response.outboxEventType()
      );
    }
  }
}
