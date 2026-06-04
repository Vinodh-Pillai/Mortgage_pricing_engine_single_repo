package com.wcpe.lock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class LockService {
  private final LockRepository repository;
  private long lockRequestTotal;
  private long lockRequestRejectedTotal;

  public LockService() {
    this(new LockRepository());
  }

  LockService(LockRepository repository) {
    this.repository = repository;
  }

  public LockModels.LockRequestResponse requestLock(LockModels.LockRequestCommand command) {
    validateRequired(command);
    String requestHash = hash(command);
    repository.findIdempotency(command.tenantId(), command.idempotencyKey(), requestHash)
      .ifPresent(response -> { throw new IdempotencyReplay(response); });

    List<String> policyFailures = policyFailures(command);
    if (!policyFailures.isEmpty()) {
      lockRequestRejectedTotal++;
      throw new LockServiceException("POLICY_NOT_SATISFIED", String.join("; ", policyFailures));
    }
    if (repository.hasActiveQuote(command.tenantId(), command.quoteId())) {
      lockRequestRejectedTotal++;
      throw new LockServiceException("DUPLICATE_ACTIVE_QUOTE_LOCK", "Active lock already exists for tenant quote");
    }

    Instant now = command.requestedAt();
    String lockId = stableId(command.tenantId(), command.requestId(), command.idempotencyKey());
    LockModels.RateLockStatus status = command.autoApprovalPermitted()
      ? LockModels.RateLockStatus.REQUESTED
      : LockModels.RateLockStatus.PENDING_APPROVAL;
    String auditRef = "AUDIT-LOCK-" + lockId;
    String replayRef = "REPLAY-LOCK-" + requestHash.substring(0, 16);
    LockModels.LockRequestResponse response = new LockModels.LockRequestResponse(
      command.tenantId(), lockId, command.requestId(), status, 1,
      "Lock request accepted using tenant policy configuration", List.of(), auditRef,
      replayRef, command.correlationId(), "lock.requested.v1", requestHash
    );
    LockModels.RateLockRecord record = new LockModels.RateLockRecord(
      command.tenantId(), lockId, command.requestId(), command.quoteId(), command.loanId(),
      command.scenarioHash(), status, 1, now, now, command.idempotencyKey(),
      command.correlationId(), command.lockPolicyVersionId(), requestHash, auditRef,
      replayRef, "lock.requested.v1"
    );
    LockModels.LockEvent event = new LockModels.LockEvent(
      "lock.requested.v1", "1", command.tenantId() + ":" + lockId, command.tenantId(),
      lockId, command.actorId(), command.correlationId(), command.requestId(),
      command.idempotencyKey(), now, Map.of(
        "status", status.name(),
        "version", "1",
        "quoteId", command.quoteId(),
        "policyVersion", command.lockPolicyVersionId(),
        "snapshotHash", requestHash
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      auditRef, command.tenantId(), lockId, "LOCK_REQUESTED", command.actorId(), null,
      status.name(), command.lockPolicyVersionId(), command.complianceEvidenceRef(),
      command.correlationId(), requestHash
    );
    repository.saveCommitted(record, response, event, audit);
    lockRequestTotal++;
    return response;
  }

  public LockModels.LockRequestResponse requestLockReplayAware(LockModels.LockRequestCommand command) {
    try {
      return requestLock(command);
    } catch (IdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.RateLockRecord getLock(UUID tenantId, String lockId) {
    return repository.find(tenantId, lockId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock was not found for tenant"));
  }

  public LockModels.RateLockRecord transition(UUID tenantId, String lockId, LockModels.RateLockStatus nextStatus) {
    LockModels.RateLockRecord current = getLock(tenantId, lockId);
    if (nextStatus == null || !current.status().allowedNextStates().contains(nextStatus)) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Cannot transition from " + current.status() + " to " + nextStatus);
    }
    LockModels.RateLockRecord updated = new LockModels.RateLockRecord(
      current.tenantId(), current.lockId(), current.requestId(), current.quoteId(),
      current.loanId(), current.scenarioHash(), nextStatus, current.version() + 1,
      current.createdAt(), Instant.now(), current.idempotencyKey(), current.correlationId(),
      current.lockPolicyVersionId(), current.requestHash(), current.auditRef(),
      current.replayRef(), current.outboxEventType()
    );
    repository.replace(updated);
    return updated;
  }

  public List<LockModels.LockEvent> outboxEvents() {
    return repository.outboxEvents();
  }

  public List<LockModels.AuditSnapshot> auditSnapshots() {
    return repository.auditSnapshots();
  }

  public int committedLockCount() {
    return repository.lockCount();
  }

  public LockModels.MetricsSnapshot metrics() {
    return new LockModels.MetricsSnapshot(lockRequestTotal, lockRequestRejectedTotal, 0);
  }

  private static void validateRequired(LockModels.LockRequestCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.requestId(), "requestId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.quoteId(), "quoteId", missing);
    require(command.loanId(), "loanId", missing);
    require(command.scenarioHash(), "scenarioHash", missing);
    require(command.pricingResultHash(), "pricingResultHash", missing);
    require(command.rateSheetVersion(), "rateSheetVersion", missing);
    require(command.productId(), "productId", missing);
    require(command.investorId(), "investorId", missing);
    require(command.channel(), "channel", missing);
    require(command.quotePricedAt(), "quotePricedAt", missing);
    require(command.requestedAt(), "requestedAt", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.lockPolicyVersionId(), "lockPolicyVersionId", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    if (command.lockPeriodDays() <= 0) {
      missing.add("lockPeriodDays");
    }
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_REQUEST_CREATE permission is required");
    }
  }

  private static List<String> policyFailures(LockModels.LockRequestCommand command) {
    List<String> failures = new ArrayList<>();
    if (!command.tenantChannelConfigPresent()) failures.add("tenant/channel policy configuration is missing or ambiguous");
    if (!command.quoteFresh()) failures.add("quote snapshot is stale");
    if (!command.scenarioHashUnchanged()) failures.add("scenario hash changed");
    if (!command.pricingHashUnchanged()) failures.add("pricing result hash changed");
    if (!command.rateSheetLockable()) failures.add("rate sheet is not active or lockable");
    if (command.marketSuspended()) failures.add("market is suspended");
    if (command.investorSuspended()) failures.add("investor is suspended");
    if (command.complianceBlocking()) failures.add("compliance advisory is blocking or missing");
    if (command.investorAmbiguous()) failures.add("investor resolution is ambiguous");
    return failures;
  }

  private static void require(Object value, String field, List<String> missing) {
    if (value == null || value instanceof String text && text.trim().isEmpty()) {
      missing.add(field);
    }
  }

  private static String stableId(UUID tenantId, String requestId, String idempotencyKey) {
    return "LOCK-" + hash(tenantId + "|" + requestId + "|" + idempotencyKey).substring(0, 16).toUpperCase();
  }

  private static String hash(LockModels.LockRequestCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.requestId(), command.actorId(), command.quoteId(),
      command.loanId(), command.scenarioHash(), command.pricingResultHash(),
      command.rateSheetVersion(), command.productId(), command.investorId(), command.channel(),
      String.valueOf(command.lockPeriodDays()), command.quotePricedAt().toString(),
      command.lockPolicyVersionId(), command.complianceEvidenceRef(),
      String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static final class IdempotencyReplay extends RuntimeException {
    private final LockModels.LockRequestResponse response;

    private IdempotencyReplay(LockModels.LockRequestResponse response) {
      this.response = response;
    }
  }
}
