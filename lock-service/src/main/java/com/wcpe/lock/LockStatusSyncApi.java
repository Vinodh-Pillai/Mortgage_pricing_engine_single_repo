package com.wcpe.lock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LockStatusSyncApi {
  public static final String GET_SYNC_STATUS_METHOD = "GET";
  public static final String GET_SYNC_STATUS_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/sync-status";
  public static final String POST_ACK_METHOD = "POST";
  public static final String POST_ACK_PATH = "/api/v1/tenants/{tenantId}/integrations/lock-status-acks";
  public static final String READ_PERMISSION = "LOCK_SYNC_READ";
  public static final String RETRY_PERMISSION = "LOCK_SYNC_RETRY";
  public static final String ACK_WRITE_PERMISSION = "LOCK_SYNC_ACK_WRITE";

  private final LockService service;

  public LockStatusSyncApi(LockService service) {
    if (service == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock service is required");
    }
    this.service = service;
  }

  public List<SyncStatusResponse> getSyncStatus(UUID tenantId, String lockId, boolean permissionGranted) {
    if (!permissionGranted) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", READ_PERMISSION + " permission is required");
    }
    return service.syncStatus(tenantId, lockId).stream().map(SyncStatusResponse::from).toList();
  }

  public SyncStatusResponse postAck(UUID tenantId, String idempotencyKey, String correlationId, AckRequest request) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock status ack request is required");
    }
    LockModels.LockSyncAttemptResponse response = service.acknowledgeLockStatus(new LockModels.LockStatusAckCommand(
      tenantId,
      request.ackId(),
      request.lockId(),
      request.eventId(),
      request.targetId(),
      request.actorId(),
      request.permissionGranted(),
      request.sourceTrusted(),
      request.expectedCurrentStatus(),
      request.requestedLockStatus(),
      request.ackStatus(),
      request.ackRef(),
      request.policyVersion(),
      request.contractVersion(),
      request.receivedAt(),
      idempotencyKey,
      correlationId,
      request.sourceRefs()
    ));
    return SyncStatusResponse.from(response);
  }

  public record AckRequest(
    String ackId,
    String lockId,
    String eventId,
    String targetId,
    String actorId,
    boolean permissionGranted,
    boolean sourceTrusted,
    LockModels.RateLockStatus expectedCurrentStatus,
    LockModels.RateLockStatus requestedLockStatus,
    LockModels.LockSyncStatus ackStatus,
    String ackRef,
    String policyVersion,
    String contractVersion,
    Instant receivedAt,
    Map<String, String> sourceRefs
  ) {}

  public record SyncStatusResponse(
    String id,
    String lockId,
    String eventId,
    String targetId,
    String status,
    String payloadHash,
    int retryCount,
    Instant nextRetryAt,
    String ackRef,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType
  ) {
    static SyncStatusResponse from(LockModels.LockSyncAttempt attempt) {
      return new SyncStatusResponse(
        attempt.attemptId(), attempt.lockId(), attempt.eventId(), attempt.targetId(), attempt.status().name(),
        attempt.payloadHash(), attempt.retryCount(), attempt.nextRetryAt(), attempt.ackRef(), "", List.of(),
        "AUDIT-LOCK-SYNC-" + attempt.attemptId(), "REPLAY-LOCK-SYNC-" + attempt.payloadHash().substring(0, 16),
        attempt.correlationId(), ""
      );
    }

    static SyncStatusResponse from(LockModels.LockSyncAttemptResponse response) {
      return new SyncStatusResponse(
        response.attemptId(), response.lockId(), response.eventId(), response.targetId(), response.status().name(),
        response.payloadHash(), response.retryCount(), response.nextRetryAt(), response.ackRef(), response.resultSummary(),
        response.validationMessages(), response.auditRef(), response.replayRef(), response.correlationId(), response.outboxEventType()
      );
    }
  }
}
