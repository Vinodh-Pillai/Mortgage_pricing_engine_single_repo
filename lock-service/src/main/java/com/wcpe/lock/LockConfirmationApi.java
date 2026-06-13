package com.wcpe.lock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LockConfirmationApi {
  public static final String POST_CONFIRMATION_METHOD = "POST";
  public static final String POST_CONFIRMATION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/confirmations";
  public static final String GET_CONFIRMATION_METHOD = "GET";
  public static final String GET_CONFIRMATION_PATH = "/api/v1/tenants/{tenantId}/lock-confirmation/{id}";
  public static final String WRITE_PERMISSION = "lock:lock-confirmation:write";
  public static final String READ_PERMISSION = "lock:lock-confirmation:read";

  private final LockService service;

  public LockConfirmationApi(LockService service) {
    if (service == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock service is required");
    }
    this.service = service;
  }

  public ConfirmationResponse postConfirmation(
    UUID tenantId,
    String lockId,
    String idempotencyKey,
    String correlationId,
    ConfirmationRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "confirmation request is required");
    }
    LockModels.LockConfirmationResponse response = service.confirmLockReplayAware(new LockModels.LockConfirmationCommand(
      tenantId,
      lockId,
      request.actorId(),
      request.expectedVersion(),
      request.confirmationType(),
      request.lockNumber(),
      request.lockNumberOverrideAllowed(),
      request.investorId(),
      request.investorConfirmationRef(),
      request.lockPeriodDays(),
      request.confirmedAt(),
      request.expiresAt(),
      request.requestedProductId(),
      request.responseProductId(),
      request.requestedLoanId(),
      request.responseLoanId(),
      request.requestedPolicyVersionId(),
      request.responsePolicyVersionId(),
      request.permissionGranted(),
      request.freshnessLockable(),
      request.confirmationPolicyResolved(),
      request.investorResponseMatches(),
      request.complianceEvidenceRef(),
      idempotencyKey,
      correlationId,
      request.sourceRefs()
    ));
    return ConfirmationResponse.from(response);
  }

  public ConfirmationReadResponse getConfirmation(UUID tenantId, String confirmationId) {
    LockModels.LockConfirmationRecord record = service.getConfirmation(tenantId, confirmationId);
    return new ConfirmationReadResponse(
      record.confirmationId(),
      record.lockId(),
      record.status().name(),
      record.lockVersion(),
      record.lockNumber(),
      record.investorConfirmationRef(),
      record.confirmedAt(),
      record.expiresAt(),
      record.expirationBusinessDays(),
      record.calendarConfigHash(),
      record.expirationBreakdown(),
      record.replayRef(),
      record.correlationId()
    );
  }

  public record ConfirmationRequest(
    String actorId,
    int expectedVersion,
    LockModels.LockConfirmationType confirmationType,
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
    Map<String, String> sourceRefs
  ) {}

  public record ConfirmationResponse(
    String id,
    String status,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String lockNumber,
    String investorConfirmationRef,
    int lockPeriodBusinessDays,
    Instant lockCreatedAt,
    Instant lockExpiresAt,
    LockModels.CalendarConfigSummary calendarConfig,
    BusinessDayCalculator.ExpirationBreakdown expirationBreakdown,
    String calendarConfigHash,
    String eventType
  ) {
    static ConfirmationResponse from(LockModels.LockConfirmationResponse response) {
      return new ConfirmationResponse(
        response.confirmationId(),
        response.status().name(),
        response.version(),
        response.resultSummary(),
        response.validationMessages(),
        response.auditRef(),
        response.replayRef(),
        response.correlationId(),
        response.lockNumber(),
        response.investorConfirmationRef(),
        response.lockPeriodBusinessDays(),
        response.lockCreatedAt(),
        response.lockExpiresAt(),
        response.calendarConfig(),
        response.expirationBreakdown(),
        response.calendarConfigHash(),
        response.outboxEventType()
      );
    }
  }

  public record ConfirmationReadResponse(
    String id,
    String lockId,
    String status,
    int version,
    String lockNumber,
    String investorConfirmationRef,
    Instant confirmedAt,
    Instant expiresAt,
    int expirationBusinessDays,
    String calendarConfigHash,
    BusinessDayCalculator.ExpirationBreakdown expirationBreakdown,
    String replayRef,
    String correlationId
  ) {}
}
