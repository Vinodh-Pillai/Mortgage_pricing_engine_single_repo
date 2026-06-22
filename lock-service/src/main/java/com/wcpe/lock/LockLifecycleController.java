package com.wcpe.lock;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class LockLifecycleController {
  private final LockService lockService;
  private final LockDetailApi lockDetailApi;
  private final LockConfirmationApi lockConfirmationApi;
  private final LockExtensionApi lockExtensionApi;
  private final LockPersistenceGate persistenceGate;

  public LockLifecycleController(
    LockService lockService,
    LockDetailApi lockDetailApi,
    LockConfirmationApi lockConfirmationApi,
    LockExtensionApi lockExtensionApi,
    LockPersistenceGate persistenceGate
  ) {
    this.lockService = lockService;
    this.lockDetailApi = lockDetailApi;
    this.lockConfirmationApi = lockConfirmationApi;
    this.lockExtensionApi = lockExtensionApi;
    this.persistenceGate = persistenceGate;
  }

  @PostMapping("/locks/requests")
  public ResponseEntity<LockModels.LockRequestResponse> requestLock(
    @PathVariable UUID tenantId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
    @RequestBody LockRequestBody request
  ) {
    persistenceGate.requireLifecycleRoutePersistence("POST /api/v1/tenants/{tenantId}/locks/requests");
    LockModels.LockRequestResponse response = lockService.requestLockReplayAware(request.toCommand(tenantId, idempotencyKey, correlationId));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/locks/{lockId}")
  public LockDetailApi.LockDetailResponse getLock(
    @PathVariable UUID tenantId,
    @PathVariable String lockId
  ) {
    persistenceGate.requireLifecycleRoutePersistence(LockDetailApi.GET_LOCK_METHOD + " " + LockDetailApi.GET_LOCK_PATH);
    return lockDetailApi.getLock(tenantId, lockId);
  }

  @PostMapping("/locks/{lockId}/confirmations")
  public ResponseEntity<LockConfirmationApi.ConfirmationResponse> postConfirmation(
    @PathVariable UUID tenantId,
    @PathVariable String lockId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
    @RequestBody LockConfirmationApi.ConfirmationRequest request
  ) {
    persistenceGate.requireLifecycleRoutePersistence(LockConfirmationApi.POST_CONFIRMATION_METHOD + " " + LockConfirmationApi.POST_CONFIRMATION_PATH);
    return ResponseEntity.status(HttpStatus.CREATED).body(lockConfirmationApi.postConfirmation(tenantId, lockId, idempotencyKey, correlationId, request));
  }

  @PostMapping("/locks/{lockId}/extensions/preview")
  public LockExtensionApi.ExtensionPreviewResponse postExtensionPreview(
    @PathVariable UUID tenantId,
    @PathVariable String lockId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
    @RequestBody LockExtensionApi.ExtensionPreviewRequest request
  ) {
    persistenceGate.requireLifecycleRoutePersistence(LockExtensionApi.POST_PREVIEW_METHOD + " " + LockExtensionApi.POST_PREVIEW_PATH);
    return lockExtensionApi.postPreview(tenantId, lockId, idempotencyKey, correlationId, request);
  }

  @PostMapping("/locks/{lockId}/extensions")
  public ResponseEntity<LockExtensionApi.ExtensionResponse> postExtension(
    @PathVariable UUID tenantId,
    @PathVariable String lockId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
    @RequestBody LockExtensionApi.ExtensionRequest request
  ) {
    persistenceGate.requireLifecycleRoutePersistence(LockExtensionApi.POST_EXTENSION_METHOD + " " + LockExtensionApi.POST_EXTENSION_PATH);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(lockExtensionApi.postExtension(tenantId, lockId, idempotencyKey, correlationId, request));
  }

  @PostMapping("/locks/{lockId}/extensions/{extensionId}/decisions")
  public LockExtensionApi.ExtensionResponse postExtensionDecision(
    @PathVariable UUID tenantId,
    @PathVariable String lockId,
    @PathVariable String extensionId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
    @RequestBody LockExtensionApi.ExtensionDecisionRequest request
  ) {
    persistenceGate.requireLifecycleRoutePersistence(LockExtensionApi.POST_DECISION_METHOD + " " + LockExtensionApi.POST_DECISION_PATH);
    return lockExtensionApi.postDecision(tenantId, lockId, extensionId, idempotencyKey, correlationId, request);
  }

  @PostMapping("/locks/{lockId}/extensions/{extensionId}/confirmations")
  public LockExtensionApi.ExtensionResponse postExtensionConfirmation(
    @PathVariable UUID tenantId,
    @PathVariable String lockId,
    @PathVariable String extensionId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
    @RequestBody LockExtensionApi.ExtensionConfirmationRequest request
  ) {
    persistenceGate.requireLifecycleRoutePersistence(LockExtensionApi.POST_CONFIRMATION_METHOD + " " + LockExtensionApi.POST_CONFIRMATION_PATH);
    return lockExtensionApi.postConfirmation(tenantId, lockId, extensionId, idempotencyKey, correlationId, request);
  }

  @PostMapping("/locks/{lockId}/extensions/{extensionId}/cancel")
  public LockExtensionApi.ExtensionResponse postExtensionCancel(
    @PathVariable UUID tenantId,
    @PathVariable String lockId,
    @PathVariable String extensionId,
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
    @RequestBody LockExtensionApi.ExtensionCancelRequest request
  ) {
    persistenceGate.requireLifecycleRoutePersistence(LockExtensionApi.POST_CANCEL_METHOD + " " + LockExtensionApi.POST_CANCEL_PATH);
    return lockExtensionApi.postCancel(tenantId, lockId, extensionId, idempotencyKey, correlationId, request);
  }

  public record LockRequestBody(
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
  ) {
    LockModels.LockRequestCommand toCommand(UUID tenantId, String idempotencyKey, String correlationId) {
      return new LockModels.LockRequestCommand(
        tenantId, requestId, actorId, quoteId, loanId, scenarioHash, pricingResultHash, rateSheetVersion,
        productId, investorId, channel, lockPeriodDays, quotePricedAt, requestedAt, idempotencyKey,
        correlationId, permissionGranted, autoApprovalPermitted, quoteFresh, scenarioHashUnchanged,
        pricingHashUnchanged, rateSheetLockable, marketSuspended, investorSuspended, complianceBlocking,
        tenantChannelConfigPresent, investorAmbiguous, lockPolicyVersionId, complianceEvidenceRef,
        sourceRefs == null ? Map.of() : sourceRefs
      );
    }
  }
}
