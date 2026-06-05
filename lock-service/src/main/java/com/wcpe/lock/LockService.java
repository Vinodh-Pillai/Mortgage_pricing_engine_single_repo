package com.wcpe.lock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
  private long lockApprovalTotal;
  private long lockRejectionTotal;
  private long lockDecisionPolicyBlockedTotal;
  private long freshnessCheckTotal;
  private long freshnessPolicyResolutionFailureTotal;
  private long freshnessExpiresSoonTotal;

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

  public LockModels.LockDecisionResponse decideLock(LockModels.LockDecisionCommand command) {
    validateDecisionRequired(command);
    String decisionHash = hash(command);
    repository.findDecisionIdempotency(command.tenantId(), command.idempotencyKey(), decisionHash)
      .ifPresent(response -> { throw new DecisionIdempotencyReplay(response); });

    LockModels.RateLockRecord current = getLock(command.tenantId(), command.lockId());
    LockModels.RateLockStatus nextStatus = decisionStatus(command.decision());
    if (current.version() != command.expectedVersion()) {
      throw new LockServiceException("VERSION_CONFLICT", "Decision expected aggregate version " + command.expectedVersion() + " but current version is " + current.version());
    }
    if (!current.status().allowedNextStates().contains(nextStatus)) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Cannot transition from " + current.status() + " to " + nextStatus);
    }
    if (!command.permissionGranted()) {
      String permission = command.decision() == LockModels.LockDecisionType.APPROVE ? "LOCK_DECIDE_APPROVE" : "LOCK_DECIDE_REJECT";
      throw new LockServiceException("TENANT_ACCESS_DENIED", permission + " permission is required");
    }
    if (command.decision() == LockModels.LockDecisionType.REJECT && normalizedReasonCodes(command).isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Configured rejection reason code is required");
    }
    if (command.decision() == LockModels.LockDecisionType.APPROVE
      && command.separationOfDutiesConfigured()
      && LockModels.normalized(command.actorId()).equals(LockModels.normalized(command.requesterActorId()))) {
      throw new LockServiceException("SEPARATION_OF_DUTIES_VIOLATION", "Requester cannot approve their own lock request");
    }
    if (!command.decisionPolicyCurrent()) {
      lockDecisionPolicyBlockedTotal++;
      throw new LockServiceException("LOCK_DECISION_BLOCKED_BY_POLICY", "Current policy/configuration invalidates the lock decision");
    }

    String decisionId = stableId(command.tenantId(), command.lockId(), command.idempotencyKey());
    String auditRef = "AUDIT-LOCK-DECISION-" + decisionId;
    String replayRef = "REPLAY-LOCK-DECISION-" + decisionHash.substring(0, 16);
    LockModels.RateLockRecord updated = new LockModels.RateLockRecord(
      current.tenantId(), current.lockId(), current.requestId(), current.quoteId(),
      current.loanId(), current.scenarioHash(), nextStatus, current.version() + 1,
      current.createdAt(), command.decidedAt(), current.idempotencyKey(), command.correlationId(),
      command.policyVersionId(), current.requestHash(), auditRef, replayRef,
      decisionEventType(command.decision())
    );
    LockModels.LockDecisionResponse response = new LockModels.LockDecisionResponse(
      command.tenantId(), command.lockId(), decisionId, command.decision(), current.status(),
      nextStatus, updated.version(), decisionSummary(command.decision()), List.of(), auditRef,
      replayRef, command.correlationId(), decisionEventType(command.decision()), decisionHash
    );
    LockModels.LockEvent event = new LockModels.LockEvent(
      decisionEventType(command.decision()), "1", command.tenantId() + ":" + command.lockId(),
      command.tenantId(), command.lockId(), command.actorId(), command.correlationId(),
      current.requestId(), command.idempotencyKey(), command.decidedAt(), Map.of(
        "decisionId", decisionId,
        "decision", command.decision().name(),
        "previousStatus", current.status().name(),
        "status", nextStatus.name(),
        "version", String.valueOf(updated.version()),
        "policyVersion", command.policyVersionId(),
        "reasonCodes", String.join(",", normalizedReasonCodes(command))
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      auditRef, command.tenantId(), command.lockId(), "LOCK_" + nextStatus.name(),
      command.actorId(), current.status().name(), nextStatus.name(), command.policyVersionId(),
      command.complianceEvidenceRef(), command.correlationId(), decisionHash
    );
    repository.saveDecision(updated, response, command.idempotencyKey(), event, audit);
    if (command.decision() == LockModels.LockDecisionType.APPROVE) {
      lockApprovalTotal++;
    } else {
      lockRejectionTotal++;
    }
    return response;
  }

  public LockModels.LockDecisionResponse decideLockReplayAware(LockModels.LockDecisionCommand command) {
    try {
      return decideLock(command);
    } catch (DecisionIdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.FreshnessCheckResponse checkFreshness(LockModels.FreshnessCheckCommand command) {
    validateFreshnessRequired(command);
    String resultHash = hash(command);
    repository.findFreshnessIdempotency(command.tenantId(), command.idempotencyKey(), resultHash)
      .ifPresent(response -> { throw new FreshnessIdempotencyReplay(response); });

    FreshnessEvaluation evaluation = evaluateFreshness(command);
    String checkId = "FRESHNESS-" + hash(command.tenantId() + "|" + command.requestId() + "|" + command.idempotencyKey()).substring(0, 16).toUpperCase();
    String auditRef = "AUDIT-FRESHNESS-" + checkId;
    String replayRef = "REPLAY-FRESHNESS-" + resultHash.substring(0, 16);
    LockModels.FreshnessCheckResponse response = new LockModels.FreshnessCheckResponse(
      command.tenantId(), checkId, command.quoteId(), evaluation.decision(), evaluation.reasonCodes(),
      command.policyVersionId(), evaluation.quoteAgeSeconds(), evaluation.expiresAt(), evaluation.remediations(),
      auditRef, replayRef, command.correlationId(), resultHash
    );
    LockModels.FreshnessCheckRecord record = new LockModels.FreshnessCheckRecord(
      command.tenantId(), checkId, command.quoteId(), command.scenarioHash(), command.policyVersionId(),
      evaluation.decision(), evaluation.reasonCodes(), command.evaluatedAt(), evaluation.expiresAt(),
      resultHash, command.actorId(), command.correlationId()
    );
    LockModels.LockEvent event = null;
    LockModels.AuditSnapshot audit = null;
    if (command.emitAuditEvent()) {
      event = new LockModels.LockEvent(
        "lock.freshness_checked.v1", "1", command.tenantId() + ":" + checkId, command.tenantId(),
        checkId, command.actorId(), command.correlationId(), command.requestId(), command.idempotencyKey(),
        command.evaluatedAt(), Map.of(
          "quoteId", command.quoteId(),
          "decision", evaluation.decision().name(),
          "reasonCodes", String.join(",", evaluation.reasonCodes()),
          "policyVersion", command.policyVersionId(),
          "quoteAgeSeconds", String.valueOf(evaluation.quoteAgeSeconds()),
          "resultHash", resultHash
        )
      );
      audit = new LockModels.AuditSnapshot(
        auditRef, command.tenantId(), checkId, "LOCK_FRESHNESS_CHECKED", command.actorId(), null,
        evaluation.decision().name(), command.policyVersionId(), command.complianceEvidenceRef(),
        command.correlationId(), resultHash
      );
    }
    repository.saveFreshnessCheck(record, response, command.idempotencyKey(), event, audit);
    freshnessCheckTotal++;
    if (evaluation.decision() == LockModels.FreshnessDecisionType.CONFIG_ERROR) {
      freshnessPolicyResolutionFailureTotal++;
    }
    if (evaluation.decision() == LockModels.FreshnessDecisionType.EXPIRES_SOON) {
      freshnessExpiresSoonTotal++;
    }
    return response;
  }

  public LockModels.FreshnessCheckResponse checkFreshnessReplayAware(LockModels.FreshnessCheckCommand command) {
    try {
      return checkFreshness(command);
    } catch (FreshnessIdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.FreshnessCheckRecord getFreshnessCheck(UUID tenantId, String checkId) {
    return repository.findFreshnessCheck(tenantId, checkId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Freshness check was not found for tenant"));
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

  public int committedFreshnessCheckCount() {
    return repository.freshnessCheckCount();
  }

  public LockModels.MetricsSnapshot metrics() {
    return new LockModels.MetricsSnapshot(
      lockRequestTotal, lockRequestRejectedTotal, lockApprovalTotal, lockRejectionTotal,
      lockDecisionPolicyBlockedTotal, freshnessCheckTotal, freshnessPolicyResolutionFailureTotal,
      freshnessExpiresSoonTotal, 0
    );
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

  private static void validateDecisionRequired(LockModels.LockDecisionCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.decision(), "decision", missing);
    require(command.actorId(), "actorId", missing);
    require(command.policyVersionId(), "policyVersionId", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.decidedAt(), "decidedAt", missing);
    if (command.expectedVersion() <= 0) {
      missing.add("expectedVersion");
    }
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateFreshnessRequired(LockModels.FreshnessCheckCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.requestId(), "requestId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.quoteId(), "quoteId", missing);
    require(command.scenarioHash(), "scenarioHash", missing);
    require(command.pricingResultHash(), "pricingResultHash", missing);
    require(command.currentScenarioHash(), "currentScenarioHash", missing);
    require(command.currentPricingResultHash(), "currentPricingResultHash", missing);
    require(command.rateSheetVersion(), "rateSheetVersion", missing);
    require(command.marketDataVersion(), "marketDataVersion", missing);
    require(command.productId(), "productId", missing);
    require(command.investorId(), "investorId", missing);
    require(command.channel(), "channel", missing);
    require(command.quotePricedAt(), "quotePricedAt", missing);
    require(command.evaluatedAt(), "evaluatedAt", missing);
    require(command.policyVersionId(), "policyVersionId", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_FRESHNESS_READ permission is required");
    }
  }

  private static FreshnessEvaluation evaluateFreshness(LockModels.FreshnessCheckCommand command) {
    long quoteAgeSeconds = Math.max(0, Duration.between(command.quotePricedAt(), command.evaluatedAt()).getSeconds());
    Instant expiresAt = command.expiresAt() != null ? command.expiresAt() : command.quotePricedAt().plusSeconds(Math.max(0, command.maxQuoteAgeSeconds()));
    List<String> reasons = new ArrayList<>();
    List<String> remediations = new ArrayList<>();
    LockModels.FreshnessDecisionType decision = LockModels.FreshnessDecisionType.FRESH;

    if (!command.policyResolved() || command.policyAmbiguous() || command.maxQuoteAgeSeconds() <= 0 || command.expirySoonWindowSeconds() < 0) {
      decision = LockModels.FreshnessDecisionType.CONFIG_ERROR;
      reasons.add("CONFIG_AMBIGUOUS");
      remediations.add("Resolve tenant/channel/product/investor freshness policy configuration");
    } else if (command.marketSuspended()) {
      decision = LockModels.FreshnessDecisionType.POLICY_SUSPENDED;
      reasons.add("MARKET_SUSPENDED");
      remediations.add("Wait for configured market suspension to clear or refresh quote later");
    } else if (!command.investorEnabled()) {
      decision = LockModels.FreshnessDecisionType.POLICY_SUSPENDED;
      reasons.add("INVESTOR_DISABLED");
      remediations.add("Use an enabled investor/channel option or wait for policy restoration");
    } else if (command.complianceBlocking()) {
      decision = LockModels.FreshnessDecisionType.STALE;
      reasons.add("COMPLIANCE_BLOCKING");
      remediations.add("Resolve compliance evidence before lock submission");
    } else if (!command.scenarioHash().equals(command.currentScenarioHash())) {
      decision = LockModels.FreshnessDecisionType.STALE;
      reasons.add("SCENARIO_HASH_CHANGED");
      remediations.add("Refresh pricing from the current scenario snapshot");
    } else if (!command.pricingResultHash().equals(command.currentPricingResultHash())) {
      decision = LockModels.FreshnessDecisionType.STALE;
      reasons.add("PRICING_HASH_CHANGED");
      remediations.add("Refresh pricing before requesting a lock");
    } else if (!command.rateSheetLockable()) {
      decision = LockModels.FreshnessDecisionType.STALE;
      reasons.add("RATE_SHEET_NOT_LOCKABLE");
      remediations.add("Refresh quote against a lockable rate sheet");
    } else if (!expiresAt.isAfter(command.evaluatedAt()) || quoteAgeSeconds > command.maxQuoteAgeSeconds()) {
      decision = LockModels.FreshnessDecisionType.STALE;
      reasons.add("QUOTE_EXPIRED");
      remediations.add("Refresh quote before requesting a lock");
    } else if (Duration.between(command.evaluatedAt(), expiresAt).getSeconds() <= command.expirySoonWindowSeconds()) {
      decision = LockModels.FreshnessDecisionType.EXPIRES_SOON;
      reasons.add("QUOTE_EXPIRES_SOON");
      remediations.add("Submit lock request before configured expiry or refresh quote");
    }

    if (reasons.isEmpty()) {
      reasons.add("QUOTE_FRESH");
    }
    return new FreshnessEvaluation(decision, List.copyOf(reasons), quoteAgeSeconds, expiresAt, List.copyOf(remediations));
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

  private static LockModels.RateLockStatus decisionStatus(LockModels.LockDecisionType decision) {
    return switch (decision) {
      case APPROVE -> LockModels.RateLockStatus.APPROVED;
      case REJECT -> LockModels.RateLockStatus.REJECTED;
    };
  }

  private static String decisionEventType(LockModels.LockDecisionType decision) {
    return switch (decision) {
      case APPROVE -> "lock.approved.v1";
      case REJECT -> "lock.rejected.v1";
    };
  }

  private static String decisionSummary(LockModels.LockDecisionType decision) {
    return switch (decision) {
      case APPROVE -> "Lock decision approved using current tenant policy configuration";
      case REJECT -> "Lock decision rejected using configured reason codes";
    };
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

  private static String hash(LockModels.LockDecisionCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), command.decision().name(),
      command.actorId(), LockModels.normalized(command.requesterActorId()),
      String.valueOf(command.expectedVersion()), String.join(",", normalizedReasonCodes(command)),
      String.valueOf(Objects.hashCode(LockModels.normalized(command.note()))),
      command.policyVersionId(), command.complianceEvidenceRef()
    ));
  }

  private static String hash(LockModels.FreshnessCheckCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.requestId(), command.actorId(), command.quoteId(),
      command.scenarioHash(), command.pricingResultHash(), command.currentScenarioHash(),
      command.currentPricingResultHash(), command.rateSheetVersion(), command.marketDataVersion(),
      command.productId(), command.investorId(), command.channel(), command.quotePricedAt().toString(),
      command.evaluatedAt().toString(), String.valueOf(command.maxQuoteAgeSeconds()),
      String.valueOf(command.expirySoonWindowSeconds()), String.valueOf(command.expiresAt()),
      command.policyVersionId(), command.complianceEvidenceRef(), String.valueOf(command.policyResolved()),
      String.valueOf(command.policyAmbiguous()), String.valueOf(command.rateSheetLockable()),
      String.valueOf(command.marketSuspended()), String.valueOf(command.investorEnabled()),
      String.valueOf(command.complianceBlocking()), String.valueOf(command.emitAuditEvent()),
      String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static List<String> normalizedReasonCodes(LockModels.LockDecisionCommand command) {
    if (command.reasonCodes() == null) {
      return List.of();
    }
    return command.reasonCodes().stream()
      .map(LockModels::upper)
      .filter(reason -> !reason.isEmpty())
      .toList();
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

  private static final class DecisionIdempotencyReplay extends RuntimeException {
    private final LockModels.LockDecisionResponse response;

    private DecisionIdempotencyReplay(LockModels.LockDecisionResponse response) {
      this.response = response;
    }
  }

  private record FreshnessEvaluation(
    LockModels.FreshnessDecisionType decision,
    List<String> reasonCodes,
    long quoteAgeSeconds,
    Instant expiresAt,
    List<String> remediations
  ) {}

  private static final class FreshnessIdempotencyReplay extends RuntimeException {
    private final LockModels.FreshnessCheckResponse response;

    private FreshnessIdempotencyReplay(LockModels.FreshnessCheckResponse response) {
      this.response = response;
    }
  }
}
