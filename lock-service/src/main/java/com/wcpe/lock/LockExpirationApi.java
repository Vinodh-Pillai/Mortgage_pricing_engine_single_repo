package com.wcpe.lock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LockExpirationApi {
  public static final String POST_EXPIRATION_RUN_METHOD = "POST";
  public static final String POST_EXPIRATION_RUN_PATH = "/internal/lock-expiration-runs";
  public static final String SERVICE_PERMISSION = "LOCK_EXPIRATION_RUN";

  private final LockService service;

  public LockExpirationApi(LockService service) {
    if (service == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock service is required");
    }
    this.service = service;
  }

  public ExpirationRunResponse postExpirationRun(ExpirationRunRequest request) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "expiration run request is required");
    }
    LockModels.LockExpirationRunResponse response = service.runExpiration(new LockModels.LockExpirationRunCommand(
      request.tenantId(),
      request.runId(),
      request.actorId(),
      request.evaluatedAt(),
      request.warningThresholdSeconds(),
      request.policyVersionId(),
      request.permissionGranted(),
      request.expirationPolicyResolved(),
      request.dryRun(),
      request.correlationId()
    ));
    return ExpirationRunResponse.from(response);
  }

  public record ExpirationRunRequest(
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

  public record ExpirationRunResponse(
    String runId,
    String status,
    int processedCount,
    int expiringSoonCount,
    int expiredCount,
    int noOpCount,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId
  ) {
    static ExpirationRunResponse from(LockModels.LockExpirationRunResponse response) {
      return new ExpirationRunResponse(
        response.runId(),
        response.status(),
        response.processedCount(),
        response.expiringSoonCount(),
        response.expiredCount(),
        response.noOpCount(),
        response.validationMessages(),
        response.auditRef(),
        response.replayRef(),
        response.correlationId()
      );
    }
  }
}
