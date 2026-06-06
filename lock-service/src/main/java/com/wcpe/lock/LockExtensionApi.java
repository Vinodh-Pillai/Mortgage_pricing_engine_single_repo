package com.wcpe.lock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LockExtensionApi {
  public static final String POST_PREVIEW_METHOD = "POST";
  public static final String POST_PREVIEW_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/extensions/preview";
  public static final String POST_EXTENSION_METHOD = "POST";
  public static final String POST_EXTENSION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/extensions";
  public static final String POST_DECISION_METHOD = "POST";
  public static final String POST_DECISION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/extensions/{extensionId}/decisions";
  public static final String POST_CONFIRMATION_METHOD = "POST";
  public static final String POST_CONFIRMATION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/extensions/{extensionId}/confirmations";
  public static final String POST_CANCEL_METHOD = "POST";
  public static final String POST_CANCEL_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/extensions/{extensionId}/cancel";
  public static final String REQUEST_PERMISSION = "LOCK_EXTENSION_REQUEST";
  public static final String APPROVE_PERMISSION = "LOCK_EXTENSION_APPROVE";
  public static final String CONFIRM_PERMISSION = "LOCK_EXTENSION_CONFIRM";
  public static final String CANCEL_PERMISSION = "LOCK_EXTENSION_CANCEL";

  private final LockService service;

  public LockExtensionApi(LockService service) {
    if (service == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock service is required");
    }
    this.service = service;
  }

  public ExtensionPreviewResponse postPreview(
    UUID tenantId,
    String lockId,
    String idempotencyKey,
    String correlationId,
    ExtensionPreviewRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension preview request is required");
    }
    return ExtensionPreviewResponse.from(service.previewExtension(new LockModels.LockExtensionPreviewCommand(
      tenantId, lockId, request.actorId(), request.expectedVersion(), request.requestedDays(), request.requestedExpiresAt(),
      request.reasonCode(), request.costSnapshot(), request.permissionGranted(), request.extensionPolicyResolved(),
      request.compliancePermitsAmendedTerms(), request.investorSupportsExtension(), idempotencyKey, correlationId, request.sourceRefs()
    )));
  }

  public ExtensionResponse postExtension(
    UUID tenantId,
    String lockId,
    String idempotencyKey,
    String correlationId,
    ExtensionRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension request is required");
    }
    return ExtensionResponse.from(service.requestExtensionReplayAware(new LockModels.LockExtensionRequestCommand(
      tenantId, lockId, request.requestId(), request.actorId(), request.expectedVersion(), request.requestedDays(),
      request.requestedAt(), request.requestedExpiresAt(), request.reasonCode(), request.costSnapshot(), request.permissionGranted(),
      request.extensionPolicyResolved(), request.compliancePermitsAmendedTerms(), request.investorSupportsExtension(),
      request.complianceEvidenceRef(), idempotencyKey, correlationId, request.sourceRefs()
    )));
  }

  public ExtensionResponse postDecision(
    UUID tenantId,
    String lockId,
    String extensionId,
    String idempotencyKey,
    String correlationId,
    ExtensionDecisionRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension decision request is required");
    }
    return ExtensionResponse.from(service.decideExtension(new LockModels.LockExtensionDecisionCommand(
      tenantId, lockId, extensionId, request.decision(), request.actorId(), request.requesterActorId(), request.expectedVersion(),
      request.investorConfirmationRequired(), request.permissionGranted(), request.separationOfDutiesConfigured(),
      request.decisionPolicyCurrent(), request.reasonCodes(), request.complianceEvidenceRef(), idempotencyKey, correlationId,
      request.decidedAt()
    )));
  }

  public ExtensionResponse postConfirmation(
    UUID tenantId,
    String lockId,
    String extensionId,
    String idempotencyKey,
    String correlationId,
    ExtensionConfirmationRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension confirmation request is required");
    }
    return ExtensionResponse.from(service.confirmExtension(new LockModels.LockExtensionConfirmationCommand(
      tenantId, lockId, extensionId, request.actorId(), request.expectedVersion(), request.permissionGranted(),
      request.investorResponseMatches(), request.investorConfirmationRef(), request.complianceEvidenceRef(), idempotencyKey,
      correlationId, request.confirmedAt(), request.sourceRefs()
    )));
  }

  public ExtensionResponse postCancel(
    UUID tenantId,
    String lockId,
    String extensionId,
    String idempotencyKey,
    String correlationId,
    ExtensionCancelRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension cancellation request is required");
    }
    return ExtensionResponse.from(service.cancelExtension(new LockModels.LockExtensionCancelCommand(
      tenantId, lockId, extensionId, request.actorId(), request.expectedVersion(), request.permissionGranted(), request.reasonCodes(),
      request.complianceEvidenceRef(), idempotencyKey, correlationId, request.cancelledAt()
    )));
  }

  public record ExtensionPreviewRequest(
    String actorId,
    int expectedVersion,
    int requestedDays,
    Instant requestedExpiresAt,
    String reasonCode,
    LockModels.ExtensionCostSnapshot costSnapshot,
    boolean permissionGranted,
    boolean extensionPolicyResolved,
    boolean compliancePermitsAmendedTerms,
    boolean investorSupportsExtension,
    Map<String, String> sourceRefs
  ) {}

  public record ExtensionRequest(
    String requestId,
    String actorId,
    int expectedVersion,
    int requestedDays,
    Instant requestedAt,
    Instant requestedExpiresAt,
    String reasonCode,
    LockModels.ExtensionCostSnapshot costSnapshot,
    boolean permissionGranted,
    boolean extensionPolicyResolved,
    boolean compliancePermitsAmendedTerms,
    boolean investorSupportsExtension,
    String complianceEvidenceRef,
    Map<String, String> sourceRefs
  ) {}

  public record ExtensionDecisionRequest(
    LockModels.LockExtensionDecisionType decision,
    String actorId,
    String requesterActorId,
    int expectedVersion,
    boolean investorConfirmationRequired,
    boolean permissionGranted,
    boolean separationOfDutiesConfigured,
    boolean decisionPolicyCurrent,
    List<String> reasonCodes,
    String complianceEvidenceRef,
    Instant decidedAt
  ) {}

  public record ExtensionConfirmationRequest(
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    boolean investorResponseMatches,
    String investorConfirmationRef,
    String complianceEvidenceRef,
    Instant confirmedAt,
    Map<String, String> sourceRefs
  ) {}

  public record ExtensionCancelRequest(
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    List<String> reasonCodes,
    String complianceEvidenceRef,
    Instant cancelledAt
  ) {}

  public record ExtensionPreviewResponse(
    String lockId,
    int requestedDays,
    Instant requestedExpiresAt,
    LockModels.ExtensionCostSnapshot costSnapshot,
    List<String> validationMessages,
    String correlationId,
    String resultHash
  ) {
    static ExtensionPreviewResponse from(LockModels.LockExtensionPreviewResponse response) {
      return new ExtensionPreviewResponse(
        response.lockId(), response.requestedDays(), response.requestedExpiresAt(), response.costSnapshot(),
        response.validationMessages(), response.correlationId(), response.resultHash()
      );
    }
  }

  public record ExtensionResponse(
    String id,
    String lockId,
    String extensionStatus,
    String status,
    int version,
    Instant expiresAt,
    int requestedDays,
    Instant requestedExpiresAt,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType
  ) {
    static ExtensionResponse from(LockModels.LockExtensionResponse response) {
      return new ExtensionResponse(
        response.extensionId(), response.lockId(), response.extensionStatus().name(), response.status().name(), response.version(),
        response.expiresAt(), response.requestedDays(), response.requestedExpiresAt(), response.resultSummary(),
        response.validationMessages(), response.auditRef(), response.replayRef(), response.correlationId(), response.outboxEventType()
      );
    }
  }
}
