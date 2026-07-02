package com.wcpe.lock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class LockService {
  private final LockRepository repository;
  private final BusinessDayCalculator businessDayCalculator;
  private long lockRequestTotal;
  private long lockRequestRejectedTotal;
  private long lockApprovalTotal;
  private long lockRejectionTotal;
  private long lockDecisionPolicyBlockedTotal;
  private long freshnessCheckTotal;
  private long freshnessPolicyResolutionFailureTotal;
  private long freshnessExpiresSoonTotal;
  private long lockConfirmationTotal;
  private long pendingInvestorConfirmationTotal;
  private long investorMismatchTotal;
  private long lockExpirationRunTotal;
  private long locksExpiringSoonTotal;
  private long locksExpiredTotal;
  private long extensionRequestTotal;
  private long extensionApprovalTotal;
  private long extensionRejectionTotal;
  private long extensionCancellationTotal;
  private long extensionConfirmationFailureTotal;
  private long extensionRequestedDaysTotal;
  private long lockSyncSentTotal;
  private long lockSyncAckedTotal;
  private long lockSyncFailedTotal;
  private long lockSyncDlqTotal;
  private long lockSyncReconciledTotal;
  private long lockAuditReportTotal;
  private long lockReplayTotal;
  private long lockReplayMismatchTotal;
  private long lockCancellationTotal;
  private long lockEvidenceExportTotal;
  private final Map<String, String> extensionFeeConfigRefsByReason = new LinkedHashMap<>();

  public LockService() {
    this(new LockRepository(), new BusinessDayCalculator(TenantCalendarClient.configuredLocalDefault()));
  }

  LockService(LockRepository repository) {
    this(repository, new BusinessDayCalculator(TenantCalendarClient.configuredLocalDefault()));
  }

  LockService(LockRepository repository, BusinessDayCalculator businessDayCalculator) {
    this.repository = repository;
    this.businessDayCalculator = businessDayCalculator;
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
      command.scenarioHash(), status, 1, now, now, null, command.idempotencyKey(),
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

  public List<LockModels.RateLockRecord> listLocks(UUID tenantId) {
    return repository.list(tenantId);
  }

  public LockModels.LockDetailResponse getLockDetail(UUID tenantId, String lockId) {
    LockModels.RateLockRecord lock = getLock(tenantId, lockId);
    LockModels.LockExpirationSchedule schedule = repository.findExpirationSchedule(tenantId, lockId).orElse(null);
    return new LockModels.LockDetailResponse(
      lock.tenantId(), lock.lockId(), lock.status(), lock.version(), lock.createdAt(), lock.expiresAt(),
      schedule == null ? 0 : schedule.expirationBusinessDays(),
      schedule == null ? "" : LockModels.normalized(schedule.calendarConfigHash()),
      schedule == null ? null : schedule.expirationBreakdown()
    );
  }

  public LockModels.RateLockRecord transition(UUID tenantId, String lockId, LockModels.RateLockStatus nextStatus) {
    LockModels.RateLockRecord current = getLock(tenantId, lockId);
    if (nextStatus == null || !current.status().allowedNextStates().contains(nextStatus)) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Cannot transition from " + current.status() + " to " + nextStatus);
    }
    LockModels.RateLockRecord updated = new LockModels.RateLockRecord(
      current.tenantId(), current.lockId(), current.requestId(), current.quoteId(),
      current.loanId(), current.scenarioHash(), nextStatus, current.version() + 1,
      current.createdAt(), Instant.now(), current.expiresAt(), current.idempotencyKey(), current.correlationId(),
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
      current.createdAt(), command.decidedAt(), current.expiresAt(), current.idempotencyKey(), command.correlationId(),
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

  public LockModels.LockConfirmationResponse confirmLock(LockModels.LockConfirmationCommand command) {
    validateConfirmationRequired(command);
    String confirmationHash = hash(command);
    repository.findConfirmationIdempotency(command.tenantId(), command.idempotencyKey(), confirmationHash)
      .ifPresent(response -> { throw new ConfirmationIdempotencyReplay(response); });

    LockModels.RateLockRecord current = getLock(command.tenantId(), command.lockId());
    LockModels.RateLockStatus nextStatus = confirmationStatus(command);
    if (current.version() != command.expectedVersion()) {
      throw new LockServiceException("VERSION_CONFLICT", "Confirmation expected aggregate version " + command.expectedVersion() + " but current version is " + current.version());
    }
    if (repository.hasActiveConfirmation(command.tenantId(), command.lockId())) {
      throw new LockServiceException("DUPLICATE_ACTIVE_CONFIRMATION", "Lock already has an active confirmation");
    }
    if (!current.status().allowedNextStates().contains(nextStatus)) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Cannot transition from " + current.status() + " to " + nextStatus);
    }
    if (nextStatus == LockModels.RateLockStatus.ACTIVE && repository.hasLockNumber(command.tenantId(), command.lockNumber())) {
      throw new LockServiceException("DUPLICATE_LOCK_NUMBER", "Lock number is already active for tenant");
    }
    if (nextStatus == LockModels.RateLockStatus.ACTIVE
      && repository.hasInvestorExternalRef(command.tenantId(), command.investorId(), command.investorConfirmationRef())) {
      throw new LockServiceException("DUPLICATE_INVESTOR_CONFIRMATION_REF", "Investor confirmation reference is already active for tenant/investor");
    }

    String confirmationId = stableId(command.tenantId(), command.lockId(), command.idempotencyKey());
    String auditRef = "AUDIT-LOCK-CONFIRMATION-" + confirmationId;
    String replayRef = "REPLAY-LOCK-CONFIRMATION-" + confirmationHash.substring(0, 16);
    BusinessDayCalculator.ExpirationCalculation expiration = expirationForConfirmation(command);
    Instant calculatedExpiresAt = expiration == null ? command.expiresAt() : expiration.expiresAt();
    int expirationBusinessDays = expiration == null ? command.lockPeriodDays() : expiration.breakdown().businessDaysAdded();
    String calendarConfigHash = expiration == null ? "" : expiration.calendarConfigHash();
    BusinessDayCalculator.ExpirationBreakdown expirationBreakdown = expiration == null ? null : expiration.breakdown();
    LockModels.CalendarConfigSummary calendarConfig = expiration == null
      ? null
      : new LockModels.CalendarConfigSummary(expiration.timezone(), expiration.workingHoursSummary(), expiration.holidaysCount());
    LockModels.RateLockRecord updated = new LockModels.RateLockRecord(
      current.tenantId(), current.lockId(), current.requestId(), current.quoteId(), current.loanId(),
      current.scenarioHash(), nextStatus, current.version() + 1, current.createdAt(), command.confirmedAt(), calculatedExpiresAt,
      current.idempotencyKey(), command.correlationId(), command.requestedPolicyVersionId(), current.requestHash(),
      auditRef, replayRef, confirmationEventType(nextStatus)
    );
    LockModels.LockConfirmationRecord confirmation = new LockModels.LockConfirmationRecord(
      command.tenantId(), confirmationId, command.lockId(), command.confirmationType(), command.lockNumber(),
      command.investorId(), command.investorConfirmationRef(), nextStatus, updated.version(), command.confirmedAt(),
      calculatedExpiresAt, expirationBusinessDays, command.confirmedAt(), calendarConfigHash, expirationBreakdown,
      confirmationHash, command.idempotencyKey(), command.correlationId(), replayRef
    );
    LockModels.LockConfirmationResponse response = new LockModels.LockConfirmationResponse(
      command.tenantId(), command.lockId(), confirmationId, current.status(), nextStatus, updated.version(),
      command.lockNumber(), command.investorConfirmationRef(), confirmationSummary(nextStatus), List.of(),
      auditRef, replayRef, command.correlationId(), confirmationEventType(nextStatus), expirationBusinessDays,
      command.confirmedAt(), calculatedExpiresAt, calendarConfig, expirationBreakdown, calendarConfigHash, confirmationHash
    );
    LockModels.LockEvent event = new LockModels.LockEvent(
      confirmationEventType(nextStatus), "1", command.tenantId() + ":" + command.lockId(), command.tenantId(),
      command.lockId(), command.actorId(), command.correlationId(), current.requestId(), command.idempotencyKey(),
      command.confirmedAt(), Map.of(
        "confirmationId", confirmationId,
        "status", nextStatus.name(),
        "version", String.valueOf(updated.version()),
        "lockNumber", command.lockNumber(),
        "investorId", LockModels.normalized(command.investorId()),
        "investorConfirmationRef", LockModels.normalized(command.investorConfirmationRef()),
        "expirationBusinessDays", String.valueOf(expirationBusinessDays),
        "calendarConfigHash", calendarConfigHash,
        "confirmedTermsHash", confirmationHash,
        "sourceRefs", String.valueOf(Objects.hashCode(command.sourceRefs()))
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      auditRef, command.tenantId(), command.lockId(), confirmationAuditAction(nextStatus), command.actorId(),
      current.status().name(), nextStatus.name(), command.requestedPolicyVersionId(), command.complianceEvidenceRef(),
      command.correlationId(), confirmationHash
    );
    repository.saveConfirmation(updated, confirmation, response, confirmationHash, event, audit);
    if (nextStatus == LockModels.RateLockStatus.PENDING_INVESTOR_CONFIRMATION) {
      pendingInvestorConfirmationTotal++;
    } else if (nextStatus == LockModels.RateLockStatus.INVESTOR_REJECTED) {
      investorMismatchTotal++;
    } else if (nextStatus == LockModels.RateLockStatus.ACTIVE) {
      lockConfirmationTotal++;
    }
    return response;
  }

  public LockModels.LockConfirmationResponse confirmLockReplayAware(LockModels.LockConfirmationCommand command) {
    try {
      return confirmLock(command);
    } catch (ConfirmationIdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.LockConfirmationRecord getConfirmation(UUID tenantId, String confirmationId) {
    return repository.findConfirmation(tenantId, confirmationId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock confirmation was not found for tenant"));
  }

  public LockModels.LockExtensionPreviewResponse previewExtension(LockModels.LockExtensionPreviewCommand command) {
    validateExtensionPreviewRequired(command);
    LockModels.RateLockRecord current = getLock(command.tenantId(), command.lockId());
    Instant requestedExpiresAt = calendarEnabled(command.sourceRefs())
      ? businessDayCalculator.calculateExpiration(command.tenantId(), current.expiresAt(), command.requestedDays()).expiresAt()
      : command.requestedExpiresAt();
    validateExtensionEligibility(current, command.expectedVersion(), command.requestedDays(), requestedExpiresAt, command.costSnapshot());
    validateExtensionPolicy(
      command.permissionGranted(), command.extensionPolicyResolved(), command.compliancePermitsAmendedTerms(), command.investorSupportsExtension(),
      "LOCK_EXTENSION_REQUEST"
    );
    String resultHash = hash(command);
    return new LockModels.LockExtensionPreviewResponse(
      command.tenantId(), command.lockId(), command.requestedDays(), requestedExpiresAt, command.costSnapshot(),
      List.of("Extension preview calculated from provided tenant/investor policy snapshot"), command.correlationId(), resultHash
    );
  }

  public LockModels.LockExtensionResponse requestExtension(LockModels.LockExtensionRequestCommand command) {
    validateExtensionRequestRequired(command);
    String resultHash = hash(command);
    repository.findExtensionIdempotency(command.tenantId(), command.idempotencyKey(), resultHash)
      .ifPresent(response -> { throw new ExtensionIdempotencyReplay(response); });

    LockModels.RateLockRecord current = getLock(command.tenantId(), command.lockId());
    BusinessDayCalculator.ExpirationCalculation extensionExpiration = calendarEnabled(command.sourceRefs())
      ? businessDayCalculator.calculateExpiration(command.tenantId(), current.expiresAt(), command.requestedDays())
      : null;
    Instant requestedExpiresAt = extensionExpiration == null ? command.requestedExpiresAt() : extensionExpiration.expiresAt();
    validateExtensionEligibility(current, command.expectedVersion(), command.requestedDays(), requestedExpiresAt, command.costSnapshot());
    validateExtensionPolicy(
      command.permissionGranted(), command.extensionPolicyResolved(), command.compliancePermitsAmendedTerms(), command.investorSupportsExtension(),
      "LOCK_EXTENSION_REQUEST"
    );
    if (repository.hasOpenExtension(command.tenantId(), command.lockId())) {
      throw new LockServiceException("OPEN_EXTENSION_EXISTS", "Only one open extension request is allowed per lock");
    }

    String extensionId = stableId(command.tenantId(), command.requestId(), command.idempotencyKey());
    String replayRef = "REPLAY-LOCK-EXTENSION-" + resultHash.substring(0, 16);
    String auditRef = "AUDIT-LOCK-EXTENSION-" + extensionId;
    LockModels.RateLockRecord updated = extensionLockRecord(
      current, LockModels.RateLockStatus.EXTENSION_REQUESTED, current.expiresAt(), command.correlationId(), command.costSnapshot().policyVersionId(),
      resultHash, auditRef, replayRef, "lock.extension_requested.v1", command.requestedAt()
    );
    LockModels.LockExtensionRecord extension = new LockModels.LockExtensionRecord(
      command.tenantId(), extensionId, command.lockId(), LockModels.LockExtensionStatus.REQUESTED, updated.version(),
      command.requestedDays(), current.expiresAt(), requestedExpiresAt, LockModels.upper(command.reasonCode()), command.actorId(), null,
      command.requestedAt(), null, null, command.costSnapshot().policyVersionId(), costSnapshotHash(command.costSnapshot()),
      command.idempotencyKey(), command.correlationId(), replayRef
    );
    LockModels.LockExtensionResponse response = extensionResponse(
      updated, extension, command.costSnapshot(), extensionExpiration, "Lock extension requested using tenant/investor policy configuration", List.of(), auditRef,
      replayRef, command.correlationId(), "lock.extension_requested.v1", resultHash
    );
    repository.saveExtensionRequest(
      updated, extension, response, resultHash,
      extensionEvent("lock.extension_requested.v1", command.tenantId(), command.lockId(), extensionId, command.actorId(), command.correlationId(), command.requestId(), command.idempotencyKey(), command.requestedAt(), updated.status(), updated.version(), command.costSnapshot(), resultHash),
      extensionAudit(auditRef, command.tenantId(), command.lockId(), "LOCK_EXTENSION_REQUESTED", command.actorId(), current.status().name(), updated.status().name(), command.costSnapshot().policyVersionId(), command.complianceEvidenceRef(), command.correlationId(), resultHash)
    );
    recordExtensionRequestMetrics(command.reasonCode(), command.costSnapshot(), command.requestedDays());
    return response;
  }

  public LockModels.LockExtensionResponse requestExtensionReplayAware(LockModels.LockExtensionRequestCommand command) {
    try {
      return requestExtension(command);
    } catch (ExtensionIdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.LockExtensionResponse decideExtension(LockModels.LockExtensionDecisionCommand command) {
    validateExtensionDecisionRequired(command);
    String resultHash = hash(command);
    repository.findExtensionIdempotency(command.tenantId(), command.idempotencyKey(), resultHash)
      .ifPresent(response -> { throw new ExtensionIdempotencyReplay(response); });
    LockModels.RateLockRecord current = getLock(command.tenantId(), command.lockId());
    LockModels.LockExtensionRecord extension = getExtension(command.tenantId(), command.extensionId());
    if (current.version() != command.expectedVersion()) {
      throw new LockServiceException("VERSION_CONFLICT", "Extension decision expected aggregate version " + command.expectedVersion() + " but current version is " + current.version());
    }
    if (current.status() != LockModels.RateLockStatus.EXTENSION_REQUESTED || extension.status() != LockModels.LockExtensionStatus.REQUESTED) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Extension decision requires an open requested extension");
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_EXTENSION_APPROVE permission is required");
    }
    if (command.decision() == LockModels.LockExtensionDecisionType.REJECT && normalizedReasons(command.reasonCodes()).isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Configured extension rejection reason code is required");
    }
    if (command.decision() == LockModels.LockExtensionDecisionType.APPROVE && command.separationOfDutiesConfigured()
      && LockModels.normalized(command.actorId()).equals(LockModels.normalized(command.requesterActorId()))) {
      throw new LockServiceException("SEPARATION_OF_DUTIES_VIOLATION", "Requester cannot approve their own lock extension");
    }
    if (!command.decisionPolicyCurrent()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Extension decision policy configuration is missing or stale");
    }

    boolean approve = command.decision() == LockModels.LockExtensionDecisionType.APPROVE;
    LockModels.RateLockStatus nextStatus = approve
      ? (command.investorConfirmationRequired() ? LockModels.RateLockStatus.PENDING_INVESTOR_EXTENSION_CONFIRMATION : LockModels.RateLockStatus.ACTIVE)
      : LockModels.RateLockStatus.ACTIVE;
    LockModels.LockExtensionStatus extensionStatus = approve
      ? (command.investorConfirmationRequired() ? LockModels.LockExtensionStatus.PENDING_INVESTOR_CONFIRMATION : LockModels.LockExtensionStatus.CONFIRMED)
      : LockModels.LockExtensionStatus.REJECTED;
    Instant expiresAt = approve && !command.investorConfirmationRequired() ? extension.requestedExpiresAt() : extension.previousExpiresAt();
    String eventType = approve ? "lock.extension_approved.v1" : "lock.extension_rejected.v1";
    String auditAction = approve ? "LOCK_EXTENSION_APPROVED" : "LOCK_EXTENSION_REJECTED";
    String auditRef = "AUDIT-LOCK-EXTENSION-DECISION-" + command.extensionId();
    String replayRef = "REPLAY-LOCK-EXTENSION-DECISION-" + resultHash.substring(0, 16);
    LockModels.RateLockRecord updated = extensionLockRecord(current, nextStatus, expiresAt, command.correlationId(), extension.policyVersionId(), resultHash, auditRef, replayRef, eventType, command.decidedAt());
    LockModels.LockExtensionRecord updatedExtension = new LockModels.LockExtensionRecord(
      extension.tenantId(), extension.extensionId(), extension.lockId(), extensionStatus, updated.version(), extension.requestedDays(),
      extension.previousExpiresAt(), extension.requestedExpiresAt(), extension.reasonCode(), extension.requestedBy(), approve ? command.actorId() : null,
      extension.requestedAt(), command.decidedAt(), approve && !command.investorConfirmationRequired() ? command.decidedAt() : null,
      extension.policyVersionId(), extension.costSnapshotHash(), extension.idempotencyKey(), command.correlationId(), replayRef
    );
    LockModels.LockExtensionResponse response = extensionResponse(
      updated, updatedExtension, extensionCostFromRecord(updatedExtension), null,
      approve ? "Lock extension approved using configured policy" : "Lock extension rejected using configured reason codes",
      List.of(), auditRef, replayRef, command.correlationId(), eventType, resultHash
    );
    repository.saveExtensionUpdate(
      updated, updatedExtension, response, command.idempotencyKey(), resultHash,
      extensionEvent(eventType, command.tenantId(), command.lockId(), command.extensionId(), command.actorId(), command.correlationId(), command.extensionId(), command.idempotencyKey(), command.decidedAt(), updated.status(), updated.version(), extensionCostFromRecord(updatedExtension), resultHash),
      extensionAudit(auditRef, command.tenantId(), command.lockId(), auditAction, command.actorId(), current.status().name(), updated.status().name(), extension.policyVersionId(), command.complianceEvidenceRef(), command.correlationId(), resultHash)
    );
    if (approve) {
      extensionApprovalTotal++;
    } else {
      extensionRejectionTotal++;
    }
    return response;
  }

  public LockModels.LockExtensionResponse confirmExtension(LockModels.LockExtensionConfirmationCommand command) {
    validateExtensionConfirmationRequired(command);
    String resultHash = hash(command);
    repository.findExtensionIdempotency(command.tenantId(), command.idempotencyKey(), resultHash)
      .ifPresent(response -> { throw new ExtensionIdempotencyReplay(response); });
    LockModels.RateLockRecord current = getLock(command.tenantId(), command.lockId());
    LockModels.LockExtensionRecord extension = getExtension(command.tenantId(), command.extensionId());
    if (current.version() != command.expectedVersion()) {
      throw new LockServiceException("VERSION_CONFLICT", "Extension confirmation expected aggregate version " + command.expectedVersion() + " but current version is " + current.version());
    }
    if (current.status() != LockModels.RateLockStatus.PENDING_INVESTOR_EXTENSION_CONFIRMATION
      || extension.status() != LockModels.LockExtensionStatus.PENDING_INVESTOR_CONFIRMATION) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Investor extension confirmation requires a pending investor extension");
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_EXTENSION_CONFIRM permission is required");
    }
    if (!command.investorResponseMatches()) {
      extensionConfirmationFailureTotal++;
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Investor extension response does not match configured days/date/cost tolerance");
    }
    String auditRef = "AUDIT-LOCK-EXTENSION-CONFIRMATION-" + command.extensionId();
    String replayRef = "REPLAY-LOCK-EXTENSION-CONFIRMATION-" + resultHash.substring(0, 16);
    LockModels.RateLockRecord updated = extensionLockRecord(current, LockModels.RateLockStatus.ACTIVE, extension.requestedExpiresAt(), command.correlationId(), extension.policyVersionId(), resultHash, auditRef, replayRef, "lock.extension_confirmed.v1", command.confirmedAt());
    LockModels.LockExtensionRecord updatedExtension = new LockModels.LockExtensionRecord(
      extension.tenantId(), extension.extensionId(), extension.lockId(), LockModels.LockExtensionStatus.CONFIRMED, updated.version(),
      extension.requestedDays(), extension.previousExpiresAt(), extension.requestedExpiresAt(), extension.reasonCode(), extension.requestedBy(),
      extension.approvedBy(), extension.requestedAt(), extension.decidedAt(), command.confirmedAt(), extension.policyVersionId(),
      extension.costSnapshotHash(), extension.idempotencyKey(), command.correlationId(), replayRef
    );
    LockModels.LockExtensionResponse response = extensionResponse(
      updated, updatedExtension, extensionCostFromRecord(updatedExtension), null,
      "Investor extension confirmation accepted and expiration amended", List.of(),
      auditRef, replayRef, command.correlationId(), "lock.extension_confirmed.v1", resultHash
    );
    repository.saveExtensionUpdate(
      updated, updatedExtension, response, command.idempotencyKey(), resultHash,
      extensionEvent("lock.extension_confirmed.v1", command.tenantId(), command.lockId(), command.extensionId(), command.actorId(), command.correlationId(), command.investorConfirmationRef(), command.idempotencyKey(), command.confirmedAt(), updated.status(), updated.version(), extensionCostFromRecord(updatedExtension), resultHash),
      extensionAudit(auditRef, command.tenantId(), command.lockId(), "LOCK_EXTENSION_CONFIRMED", command.actorId(), current.status().name(), updated.status().name(), extension.policyVersionId(), command.complianceEvidenceRef(), command.correlationId(), resultHash)
    );
    return response;
  }

  public LockModels.LockExtensionResponse cancelExtension(LockModels.LockExtensionCancelCommand command) {
    validateExtensionCancelRequired(command);
    String resultHash = hash(command);
    repository.findExtensionIdempotency(command.tenantId(), command.idempotencyKey(), resultHash)
      .ifPresent(response -> { throw new ExtensionIdempotencyReplay(response); });
    LockModels.RateLockRecord current = getLock(command.tenantId(), command.lockId());
    LockModels.LockExtensionRecord extension = getExtension(command.tenantId(), command.extensionId());
    if (current.version() != command.expectedVersion()) {
      throw new LockServiceException("VERSION_CONFLICT", "Extension cancellation expected aggregate version " + command.expectedVersion() + " but current version is " + current.version());
    }
    boolean cancellableState = current.status() == LockModels.RateLockStatus.EXTENSION_REQUESTED
      && extension.status() == LockModels.LockExtensionStatus.REQUESTED;
    boolean cancellableInvestorPending = current.status() == LockModels.RateLockStatus.PENDING_INVESTOR_EXTENSION_CONFIRMATION
      && extension.status() == LockModels.LockExtensionStatus.PENDING_INVESTOR_CONFIRMATION;
    if (!cancellableState && !cancellableInvestorPending) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Extension cancellation requires an open requested or pending investor extension");
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_EXTENSION_CANCEL permission is required");
    }
    if (normalizedReasons(command.reasonCodes()).isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Configured extension cancellation reason code is required");
    }

    String auditRef = "AUDIT-LOCK-EXTENSION-CANCEL-" + command.extensionId();
    String replayRef = "REPLAY-LOCK-EXTENSION-CANCEL-" + resultHash.substring(0, 16);
    LockModels.RateLockRecord updated = extensionLockRecord(
      current, LockModels.RateLockStatus.ACTIVE, extension.previousExpiresAt(), command.correlationId(), extension.policyVersionId(),
      resultHash, auditRef, replayRef, "lock.extension_cancelled.v1", command.cancelledAt()
    );
    LockModels.LockExtensionRecord updatedExtension = new LockModels.LockExtensionRecord(
      extension.tenantId(), extension.extensionId(), extension.lockId(), LockModels.LockExtensionStatus.CANCELLED, updated.version(),
      extension.requestedDays(), extension.previousExpiresAt(), extension.requestedExpiresAt(), extension.reasonCode(), extension.requestedBy(),
      extension.approvedBy(), extension.requestedAt(), command.cancelledAt(), null, extension.policyVersionId(), extension.costSnapshotHash(),
      extension.idempotencyKey(), command.correlationId(), replayRef
    );
    LockModels.LockExtensionResponse response = extensionResponse(
      updated, updatedExtension, extensionCostFromRecord(updatedExtension), null,
      "Lock extension request cancelled using configured reason codes", List.of(),
      auditRef, replayRef, command.correlationId(), "lock.extension_cancelled.v1", resultHash
    );
    repository.saveExtensionUpdate(
      updated, updatedExtension, response, command.idempotencyKey(), resultHash,
      extensionEvent("lock.extension_cancelled.v1", command.tenantId(), command.lockId(), command.extensionId(), command.actorId(), command.correlationId(), command.extensionId(), command.idempotencyKey(), command.cancelledAt(), updated.status(), updated.version(), extensionCostFromRecord(updatedExtension), resultHash),
      extensionAudit(auditRef, command.tenantId(), command.lockId(), "LOCK_EXTENSION_CANCELLED", command.actorId(), current.status().name(), updated.status().name(), extension.policyVersionId(), command.complianceEvidenceRef(), command.correlationId(), resultHash)
    );
    extensionCancellationTotal++;
    return response;
  }

  public LockModels.LockExtensionRecord getExtension(UUID tenantId, String extensionId) {
    return repository.findExtension(tenantId, extensionId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock extension was not found for tenant"));
  }

  public LockModels.RelockResponse previewRelock(LockModels.RelockPreviewCommand command) {
    validateRelockPreviewRequired(command);
    LockModels.RateLockRecord source = getLock(command.tenantId(), command.sourceLockId());
    validateRelockEligibility(source, command.expectedVersion(), command.policySnapshot(), command.originalTerms(), command.currentTerms(), command.selectedTerms());
    String resultHash = hash(command);
    return new LockModels.RelockResponse(
      command.tenantId(), previewRelockId(command.tenantId(), command.sourceLockId(), command.idempotencyKey()), command.sourceLockId(), null,
      LockModels.RelockStatus.PREVIEWED, source.status(), null, source.version(),
      "Relock preview calculated from tenant/investor policy snapshot", List.of(), null,
      "REPLAY-LOCK-RELOCK-PREVIEW-" + resultHash.substring(0, 16), command.correlationId(), null, resultHash
    );
  }

  public LockModels.RelockResponse requestRelock(LockModels.RelockRequestCommand command) {
    validateRelockRequestRequired(command);
    String resultHash = hash(command);
    repository.findRelockIdempotency(command.tenantId(), command.idempotencyKey(), resultHash)
      .ifPresent(response -> { throw new RelockIdempotencyReplay(response); });
    LockModels.RateLockRecord source = getLock(command.tenantId(), command.sourceLockId());
    validateRelockEligibility(source, command.expectedVersion(), command.policySnapshot(), command.originalTerms(), command.currentTerms(), command.selectedTerms());
    if (repository.hasOpenRelock(command.tenantId(), command.sourceLockId())) {
      throw new LockServiceException("OPEN_RELOCK_EXISTS", "Only one open relock request is allowed per source lock");
    }
    if (repository.hasActiveQuote(command.tenantId(), command.currentQuoteId())) {
      throw new LockServiceException("REPLACEMENT_LOCK_CONFLICT", "Current quote already has an active tenant lock");
    }

    String relockId = stableId(command.tenantId(), command.requestId(), command.idempotencyKey());
    String replacementLockId = "RELOCK-" + hash(command.tenantId() + "|" + relockId + "|" + command.currentQuoteId()).substring(0, 16).toUpperCase();
    String auditRef = "AUDIT-LOCK-RELOCK-" + relockId;
    String replayRef = "REPLAY-LOCK-RELOCK-" + resultHash.substring(0, 16);
    LockModels.RateLockRecord updatedSource = relockLockRecord(
      source, LockModels.RateLockStatus.RELOCK_REQUESTED, command.correlationId(), command.policySnapshot().policyVersionId(),
      resultHash, auditRef, replayRef, "lock.relock_requested.v1", command.requestedAt()
    );
    LockModels.RateLockRecord replacement = new LockModels.RateLockRecord(
      command.tenantId(), replacementLockId, command.requestId(), command.currentQuoteId(), source.loanId(), source.scenarioHash(),
      LockModels.RateLockStatus.PENDING_APPROVAL, 1, command.requestedAt(), command.requestedAt(), null, command.idempotencyKey(),
      command.correlationId(), command.policySnapshot().policyVersionId(), resultHash, auditRef, replayRef, "lock.relock_requested.v1"
    );
    LockModels.RelockRecord relock = new LockModels.RelockRecord(
      command.tenantId(), relockId, command.sourceLockId(), replacementLockId, command.currentQuoteId(), LockModels.RelockStatus.REQUESTED,
      updatedSource.version(), command.actorId(), null, command.requestedAt(), null, null, LockModels.upper(command.reasonCode()),
      command.policySnapshot().policyVersionId(), command.policySnapshot().investorConfirmationRequired(), resultHash,
      command.idempotencyKey(), command.correlationId(), replayRef
    );
    LockModels.RelockResponse response = relockResponse(
      updatedSource, replacement, relock, "Relock requested using configured worse-case/better-case policy snapshot", List.of(),
      auditRef, replayRef, command.correlationId(), "lock.relock_requested.v1", resultHash
    );
    repository.saveRelockRequest(
      updatedSource, replacement, relock, response, resultHash,
      relockEvent("lock.relock_requested.v1", command.tenantId(), command.sourceLockId(), relockId, replacementLockId, command.actorId(), command.correlationId(), command.requestId(), command.idempotencyKey(), command.requestedAt(), updatedSource.status(), replacement.status(), updatedSource.version(), command.policySnapshot(), resultHash),
      relockAudit(auditRef, command.tenantId(), command.sourceLockId(), "LOCK_RELOCK_REQUESTED", command.actorId(), source.status().name(), updatedSource.status().name(), command.policySnapshot().policyVersionId(), command.complianceEvidenceRef(), command.correlationId(), resultHash)
    );
    return response;
  }

  public LockModels.RelockResponse requestRelockReplayAware(LockModels.RelockRequestCommand command) {
    try {
      return requestRelock(command);
    } catch (RelockIdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.RelockResponse decideRelock(LockModels.RelockDecisionCommand command) {
    validateRelockDecisionRequired(command);
    String resultHash = hash(command);
    repository.findRelockIdempotency(command.tenantId(), command.idempotencyKey(), resultHash)
      .ifPresent(response -> { throw new RelockIdempotencyReplay(response); });
    LockModels.RateLockRecord source = getLock(command.tenantId(), command.sourceLockId());
    LockModels.RelockRecord relock = getRelock(command.tenantId(), command.relockId());
    LockModels.RateLockRecord replacement = getLock(command.tenantId(), relock.replacementLockId());
    if (source.version() != command.expectedVersion()) {
      throw new LockServiceException("VERSION_CONFLICT", "Relock decision expected aggregate version " + command.expectedVersion() + " but current version is " + source.version());
    }
    if (source.status() != LockModels.RateLockStatus.RELOCK_REQUESTED || relock.status() != LockModels.RelockStatus.REQUESTED) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Relock decision requires an open requested relock");
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_RELOCK_APPROVE permission is required");
    }
    if (command.decision() == LockModels.RelockDecisionType.REJECT && normalizedReasons(command.reasonCodes()).isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Configured relock rejection reason code is required");
    }
    if (command.decision() == LockModels.RelockDecisionType.APPROVE && command.separationOfDutiesConfigured()
      && LockModels.normalized(command.actorId()).equals(LockModels.normalized(command.requesterActorId()))) {
      throw new LockServiceException("SEPARATION_OF_DUTIES_VIOLATION", "Requester cannot approve their own relock request");
    }
    if (!command.decisionPolicyCurrent()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Relock decision policy configuration is missing or stale");
    }

    boolean approve = command.decision() == LockModels.RelockDecisionType.APPROVE;
    LockModels.RateLockStatus sourceStatus = approve
      ? (relock.investorConfirmationRequired() ? LockModels.RateLockStatus.PENDING_INVESTOR_RELOCK_CONFIRMATION : LockModels.RateLockStatus.RELOCK_APPROVED)
      : LockModels.RateLockStatus.RELOCK_REJECTED;
    LockModels.RateLockStatus replacementStatus = approve
      ? (relock.investorConfirmationRequired() ? LockModels.RateLockStatus.PENDING_INVESTOR_CONFIRMATION : LockModels.RateLockStatus.APPROVED)
      : LockModels.RateLockStatus.REJECTED;
    LockModels.RelockStatus relockStatus = approve
      ? (relock.investorConfirmationRequired() ? LockModels.RelockStatus.PENDING_INVESTOR_CONFIRMATION : LockModels.RelockStatus.APPROVED)
      : LockModels.RelockStatus.REJECTED;
    String eventType = approve ? "lock.relock_approved.v1" : "lock.relock_rejected.v1";
    String auditAction = approve ? "LOCK_RELOCK_APPROVED" : "LOCK_RELOCK_REJECTED";
    String auditRef = "AUDIT-LOCK-RELOCK-DECISION-" + command.relockId();
    String replayRef = "REPLAY-LOCK-RELOCK-DECISION-" + resultHash.substring(0, 16);
    LockModels.RateLockRecord updatedSource = relockLockRecord(source, sourceStatus, command.correlationId(), relock.policyVersionId(), resultHash, auditRef, replayRef, eventType, command.decidedAt());
    LockModels.RateLockRecord updatedReplacement = relockLockRecord(replacement, replacementStatus, command.correlationId(), relock.policyVersionId(), resultHash, auditRef, replayRef, eventType, command.decidedAt());
    LockModels.RelockRecord updatedRelock = new LockModels.RelockRecord(
      relock.tenantId(), relock.relockId(), relock.sourceLockId(), relock.replacementLockId(), relock.currentQuoteId(), relockStatus,
      updatedSource.version(), relock.requestedBy(), approve ? command.actorId() : null, relock.requestedAt(), command.decidedAt(),
      approve && !relock.investorConfirmationRequired() ? command.decidedAt() : null, relock.reasonCode(), relock.policyVersionId(),
      relock.investorConfirmationRequired(), relock.comparisonHash(), relock.idempotencyKey(), command.correlationId(), replayRef
    );
    LockModels.RelockResponse response = relockResponse(
      updatedSource, updatedReplacement, updatedRelock, approve ? "Relock approved using configured policy" : "Relock rejected using configured reason codes",
      List.of(), auditRef, replayRef, command.correlationId(), eventType, resultHash
    );
    repository.saveRelockUpdate(
      updatedSource, updatedReplacement, updatedRelock, response, command.idempotencyKey(), resultHash,
      relockEvent(eventType, command.tenantId(), command.sourceLockId(), command.relockId(), relock.replacementLockId(), command.actorId(), command.correlationId(), command.relockId(), command.idempotencyKey(), command.decidedAt(), updatedSource.status(), updatedReplacement.status(), updatedSource.version(), relockPolicyFromRecord(updatedRelock), resultHash),
      relockAudit(auditRef, command.tenantId(), command.sourceLockId(), auditAction, command.actorId(), source.status().name(), updatedSource.status().name(), relock.policyVersionId(), command.complianceEvidenceRef(), command.correlationId(), resultHash)
    );
    return response;
  }

  public LockModels.RelockResponse confirmRelock(LockModels.RelockConfirmationCommand command) {
    validateRelockConfirmationRequired(command);
    String resultHash = hash(command);
    repository.findRelockIdempotency(command.tenantId(), command.idempotencyKey(), resultHash)
      .ifPresent(response -> { throw new RelockIdempotencyReplay(response); });
    LockModels.RateLockRecord source = getLock(command.tenantId(), command.sourceLockId());
    LockModels.RelockRecord relock = getRelock(command.tenantId(), command.relockId());
    LockModels.RateLockRecord replacement = getLock(command.tenantId(), relock.replacementLockId());
    if (source.version() != command.expectedVersion()) {
      throw new LockServiceException("VERSION_CONFLICT", "Relock confirmation expected aggregate version " + command.expectedVersion() + " but current version is " + source.version());
    }
    boolean immediateApproved = source.status() == LockModels.RateLockStatus.RELOCK_APPROVED && relock.status() == LockModels.RelockStatus.APPROVED;
    boolean investorPending = source.status() == LockModels.RateLockStatus.PENDING_INVESTOR_RELOCK_CONFIRMATION
      && relock.status() == LockModels.RelockStatus.PENDING_INVESTOR_CONFIRMATION;
    if (!immediateApproved && !investorPending) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Relock confirmation requires an approved relock");
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_RELOCK_CONFIRM permission is required");
    }
    if (!command.investorResponseMatches()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Investor relock confirmation does not match configured selected terms tolerance");
    }
    String auditRef = "AUDIT-LOCK-RELOCK-CONFIRMATION-" + command.relockId();
    String replayRef = "REPLAY-LOCK-RELOCK-CONFIRMATION-" + resultHash.substring(0, 16);
    LockModels.RateLockRecord updatedSource = relockLockRecord(source, LockModels.RateLockStatus.RELOCKED, command.correlationId(), relock.policyVersionId(), resultHash, auditRef, replayRef, "lock.relocked.v1", command.confirmedAt());
    LockModels.RateLockRecord updatedReplacement = relockLockRecord(replacement, LockModels.RateLockStatus.ACTIVE, command.correlationId(), relock.policyVersionId(), resultHash, auditRef, replayRef, "lock.relocked.v1", command.confirmedAt());
    LockModels.RelockRecord updatedRelock = new LockModels.RelockRecord(
      relock.tenantId(), relock.relockId(), relock.sourceLockId(), relock.replacementLockId(), relock.currentQuoteId(), LockModels.RelockStatus.CONFIRMED,
      updatedSource.version(), relock.requestedBy(), relock.approvedBy(), relock.requestedAt(), relock.decidedAt(), command.confirmedAt(),
      relock.reasonCode(), relock.policyVersionId(), relock.investorConfirmationRequired(), relock.comparisonHash(), relock.idempotencyKey(),
      command.correlationId(), replayRef
    );
    LockModels.RelockResponse response = relockResponse(
      updatedSource, updatedReplacement, updatedRelock, "Relock confirmed and source/replacement lock linkage completed", List.of(),
      auditRef, replayRef, command.correlationId(), "lock.relocked.v1", resultHash
    );
    repository.saveRelockUpdate(
      updatedSource, updatedReplacement, updatedRelock, response, command.idempotencyKey(), resultHash,
      relockEvent("lock.relocked.v1", command.tenantId(), command.sourceLockId(), command.relockId(), relock.replacementLockId(), command.actorId(), command.correlationId(), command.investorConfirmationRef(), command.idempotencyKey(), command.confirmedAt(), updatedSource.status(), updatedReplacement.status(), updatedSource.version(), relockPolicyFromRecord(updatedRelock), resultHash),
      relockAudit(auditRef, command.tenantId(), command.sourceLockId(), "LOCK_RELOCKED", command.actorId(), source.status().name(), updatedSource.status().name(), relock.policyVersionId(), command.complianceEvidenceRef(), command.correlationId(), resultHash)
    );
    return response;
  }

  public LockModels.RelockResponse cancelRelock(LockModels.RelockCancelCommand command) {
    validateRelockCancelRequired(command);
    String resultHash = hash(command);
    repository.findRelockIdempotency(command.tenantId(), command.idempotencyKey(), resultHash)
      .ifPresent(response -> { throw new RelockIdempotencyReplay(response); });
    LockModels.RateLockRecord source = getLock(command.tenantId(), command.sourceLockId());
    LockModels.RelockRecord relock = getRelock(command.tenantId(), command.relockId());
    LockModels.RateLockRecord replacement = getLock(command.tenantId(), relock.replacementLockId());
    if (source.version() != command.expectedVersion()) {
      throw new LockServiceException("VERSION_CONFLICT", "Relock cancellation expected aggregate version " + command.expectedVersion() + " but current version is " + source.version());
    }
    if (relock.status() != LockModels.RelockStatus.REQUESTED && relock.status() != LockModels.RelockStatus.APPROVED) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Relock cancellation requires an open requested or approved relock");
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_RELOCK_CANCEL permission is required");
    }
    if (normalizedReasons(command.reasonCodes()).isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Configured relock cancellation reason code is required");
    }
    String auditRef = "AUDIT-LOCK-RELOCK-CANCEL-" + command.relockId();
    String replayRef = "REPLAY-LOCK-RELOCK-CANCEL-" + resultHash.substring(0, 16);
    LockModels.RateLockStatus restoredStatus = source.expiresAt() != null && source.expiresAt().isAfter(command.cancelledAt())
      ? LockModels.RateLockStatus.ACTIVE
      : LockModels.RateLockStatus.EXPIRED;
    LockModels.RateLockRecord updatedSource = relockLockRecord(source, restoredStatus, command.correlationId(), relock.policyVersionId(), resultHash, auditRef, replayRef, "lock.relock_cancelled.v1", command.cancelledAt());
    LockModels.RateLockRecord updatedReplacement = relockLockRecord(replacement, LockModels.RateLockStatus.CANCELLED, command.correlationId(), relock.policyVersionId(), resultHash, auditRef, replayRef, "lock.relock_cancelled.v1", command.cancelledAt());
    LockModels.RelockRecord updatedRelock = new LockModels.RelockRecord(
      relock.tenantId(), relock.relockId(), relock.sourceLockId(), relock.replacementLockId(), relock.currentQuoteId(), LockModels.RelockStatus.CANCELLED,
      updatedSource.version(), relock.requestedBy(), relock.approvedBy(), relock.requestedAt(), command.cancelledAt(), null,
      relock.reasonCode(), relock.policyVersionId(), relock.investorConfirmationRequired(), relock.comparisonHash(), relock.idempotencyKey(),
      command.correlationId(), replayRef
    );
    LockModels.RelockResponse response = relockResponse(
      updatedSource, updatedReplacement, updatedRelock, "Relock request cancelled using configured reason codes", List.of(),
      auditRef, replayRef, command.correlationId(), "lock.relock_cancelled.v1", resultHash
    );
    repository.saveRelockUpdate(
      updatedSource, updatedReplacement, updatedRelock, response, command.idempotencyKey(), resultHash,
      relockEvent("lock.relock_cancelled.v1", command.tenantId(), command.sourceLockId(), command.relockId(), relock.replacementLockId(), command.actorId(), command.correlationId(), command.relockId(), command.idempotencyKey(), command.cancelledAt(), updatedSource.status(), updatedReplacement.status(), updatedSource.version(), relockPolicyFromRecord(updatedRelock), resultHash),
      relockAudit(auditRef, command.tenantId(), command.sourceLockId(), "LOCK_RELOCK_CANCELLED", command.actorId(), source.status().name(), updatedSource.status().name(), relock.policyVersionId(), command.complianceEvidenceRef(), command.correlationId(), resultHash)
    );
    return response;
  }

  public LockModels.RelockRecord getRelock(UUID tenantId, String relockId) {
    return repository.findRelock(tenantId, relockId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Relock request was not found for tenant"));
  }

  public LockModels.LockExpirationRunResponse runExpiration(LockModels.LockExpirationRunCommand command) {
    validateExpirationRunRequired(command);
    String runHash = hash(command);
    String auditRef = "AUDIT-LOCK-EXPIRATION-RUN-" + command.runId();
    String replayRef = "REPLAY-LOCK-EXPIRATION-" + runHash.substring(0, 16);
    var priorRun = repository.findExpirationRun(command.tenantId(), command.runId());
    if (priorRun.isPresent()) {
      LockModels.LockExpirationRunRecord prior = priorRun.get();
      if (!prior.replayRef().equals(replayRef)) {
        throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Expiration runId was reused with a different payload");
      }
      return new LockModels.LockExpirationRunResponse(
        prior.tenantId(), prior.runId(), prior.startedAt(), prior.completedAt(), prior.status(), prior.processedCount(),
        prior.expiringSoonCount(), prior.expiredCount(), prior.noOpCount(), List.of(), auditRef, prior.replayRef(), prior.correlationId()
      );
    }
    int processed = 0;
    int expiringSoon = 0;
    int expired = 0;
    int noOp = 0;

    for (LockModels.LockConfirmationRecord confirmation : repository.activeConfirmations(command.tenantId())) {
      LockModels.RateLockRecord current = getLock(command.tenantId(), confirmation.lockId());
      LockModels.RateLockStatus nextStatus = expirationStatus(current.status(), confirmation.expiresAt(), command.evaluatedAt(), command.warningThresholdSeconds());
      if (nextStatus == current.status()) {
        noOp++;
        continue;
      }
      processed++;
      if (nextStatus == LockModels.RateLockStatus.EXPIRING_SOON) {
        expiringSoon++;
      } else if (nextStatus == LockModels.RateLockStatus.EXPIRED) {
        expired++;
      }
      if (!command.dryRun()) {
        saveExpirationTransition(command, confirmation, current, nextStatus, runHash);
      }
    }

    Instant completedAt = command.evaluatedAt();
    LockModels.LockExpirationRunRecord runRecord = new LockModels.LockExpirationRunRecord(
      command.tenantId(), command.runId(), command.evaluatedAt(), completedAt, command.dryRun() ? "DRY_RUN" : "COMPLETED",
      processed, expiringSoon, expired, noOp, replayRef, command.correlationId()
    );
    repository.saveExpirationRun(runRecord);
    lockExpirationRunTotal++;
    locksExpiringSoonTotal += expiringSoon;
    locksExpiredTotal += expired;
    return new LockModels.LockExpirationRunResponse(
      command.tenantId(), command.runId(), command.evaluatedAt(), completedAt, runRecord.status(), processed,
      expiringSoon, expired, noOp, List.of(), auditRef, replayRef, command.correlationId()
    );
  }

  public LockModels.LockExpirationRunRecord getExpirationRun(UUID tenantId, String runId) {
    return repository.findExpirationRun(tenantId, runId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock expiration run was not found for tenant"));
  }

  public LockModels.LockExpirationSchedule getExpirationSchedule(UUID tenantId, String lockId) {
    return repository.findExpirationSchedule(tenantId, lockId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock expiration schedule was not found for tenant"));
  }

  public LockModels.LockAuditReportResponse createAuditReportReplayAware(LockModels.LockAuditReportCommand command) {
    validateAuditReportRequired(command);
    String reportHash = hash(command);
    repository.findAuditReportIdempotency(command.tenantId(), command.idempotencyKey(), reportHash)
      .ifPresent(response -> { throw new AuditReportIdempotencyReplay(response); });

    String reportId = "REPORT-" + hash(command.tenantId() + "|" + command.requestId() + "|" + command.idempotencyKey()).substring(0, 16).toUpperCase();
    String manifestHash = hash(command.tenantId() + "|" + reportId + "|" + reportHash + "|" + Objects.hashCode(command.criteria()));
    String auditRef = "AUDIT-LOCK-REPORT-" + reportId;
    String replayRef = "REPLAY-LOCK-REPORT-" + reportHash.substring(0, 16);
    LockModels.LockAuditReportRecord report = new LockModels.LockAuditReportRecord(
      command.tenantId(), reportId, LockModels.LockAuditReportStatus.READY, command.actorId(), command.requestedAt(),
      reportHash, manifestHash, command.idempotencyKey(), command.correlationId()
    );
    LockModels.LockAuditReportResponse response = new LockModels.LockAuditReportResponse(
      command.tenantId(), reportId, LockModels.LockAuditReportStatus.READY, 1,
      "Lock audit report metadata persisted without changing historical lock facts", List.of(), auditRef, replayRef,
      command.correlationId(), "lock.audit_report_ready.v1", manifestHash
    );
    LockModels.LockEvent event = new LockModels.LockEvent(
      "lock.audit_report_ready.v1", "1", command.tenantId() + ":" + reportId, command.tenantId(), reportId,
      command.actorId(), command.correlationId(), command.requestId(), command.idempotencyKey(), command.requestedAt(), Map.of(
        "reportId", reportId,
        "status", LockModels.LockAuditReportStatus.READY.name(),
        "criteriaHash", reportHash,
        "manifestHash", manifestHash,
        "sourceRefs", String.valueOf(Objects.hashCode(command.sourceRefs()))
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      auditRef, command.tenantId(), reportId, "LOCK_AUDIT_REPORT_READY", command.actorId(), null,
      LockModels.LockAuditReportStatus.READY.name(), null, null, command.correlationId(), manifestHash
    );
    repository.saveAuditReport(report, response, reportHash, event, audit);
    lockAuditReportTotal++;
    return response;
  }

  public LockModels.LockAuditReportResponse createAuditReport(LockModels.LockAuditReportCommand command) {
    try {
      return createAuditReportReplayAware(command);
    } catch (AuditReportIdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.LockAuditReportRecord getAuditReport(UUID tenantId, String reportId) {
    return repository.findAuditReport(tenantId, reportId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock audit report was not found for tenant"));
  }

  public LockModels.LockReplayResponse replayLock(LockModels.LockReplayCommand command) {
    validateReplayRequired(command);
    String replayHash = hash(command);
    repository.findReplayIdempotency(command.tenantId(), command.idempotencyKey(), replayHash)
      .ifPresent(response -> { throw new ReplayIdempotencyReplay(response); });
    getLock(command.tenantId(), command.lockId());
    LockModels.LockReplayMismatchClass mismatchClass = replayMismatchClass(command);
    String replayId = LockModels.normalized(command.replayId()).isEmpty()
      ? "REPLAY-" + hash(command.tenantId() + "|" + command.lockId() + "|" + command.idempotencyKey()).substring(0, 16).toUpperCase()
      : command.replayId();
    String auditRef = "AUDIT-LOCK-REPLAY-" + replayId;
    String replayRef = "REPLAY-LOCK-" + replayHash.substring(0, 16);
    LockModels.LockReplayResult result = new LockModels.LockReplayResult(
      command.tenantId(), replayId, command.lockId(), command.capturedInputHash(), command.configGraphHash(),
      command.eventSequenceHash(), command.expectedResultHash(), command.actualResultHash(), mismatchClass, replayHash,
      command.idempotencyKey(), command.correlationId(), command.replayedAt()
    );
    LockModels.LockReplayResponse response = new LockModels.LockReplayResponse(
      command.tenantId(), replayId, command.lockId(), mismatchClass, replayHash,
      "Lock replay evidence recorded using historical captured policy and config references", List.of(), auditRef, replayRef,
      command.correlationId(), "lock.replay_completed.v1"
    );
    LockModels.LockEvent event = new LockModels.LockEvent(
      "lock.replay_completed.v1", "1", command.tenantId() + ":" + command.lockId() + ":" + replayId,
      command.tenantId(), command.lockId(), command.actorId(), command.correlationId(), replayId, command.idempotencyKey(),
      command.replayedAt(), Map.of(
        "replayId", replayId,
        "inputHash", command.capturedInputHash(),
        "configGraphHash", command.configGraphHash(),
        "eventSequenceHash", command.eventSequenceHash(),
        "mismatchClass", mismatchClass.name(),
        "evidenceHash", replayHash
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      auditRef, command.tenantId(), command.lockId(), "LOCK_REPLAY_COMPLETED", command.actorId(), null,
      mismatchClass.name(), null, null, command.correlationId(), replayHash
    );
    repository.saveReplayResult(result, response, replayHash, event, audit);
    lockReplayTotal++;
    if (mismatchClass != LockModels.LockReplayMismatchClass.MATCH) {
      lockReplayMismatchTotal++;
    }
    return response;
  }

  public LockModels.LockReplayResponse replayLockReplayAware(LockModels.LockReplayCommand command) {
    try {
      return replayLock(command);
    } catch (ReplayIdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.LockReplayResult getReplayResult(UUID tenantId, String replayId) {
    return repository.findReplayResult(tenantId, replayId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock replay result was not found for tenant"));
  }

  public LockModels.LockCancellationResponse cancelLock(LockModels.LockCancellationCommand command) {
    validateCancellationRequired(command);
    String cancellationHash = hash(command);
    repository.findCancellationIdempotency(command.tenantId(), command.idempotencyKey(), cancellationHash)
      .ifPresent(response -> { throw new CancellationIdempotencyReplay(response); });
    LockModels.RateLockRecord current = getLock(command.tenantId(), command.lockId());
    if (current.version() != command.expectedVersion()) {
      throw new LockServiceException("VERSION_CONFLICT", "Cancellation expected aggregate version " + command.expectedVersion() + " but current version is " + current.version());
    }
    if (!cancellationEligible(current.status())) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Lock cancellation is not allowed from " + current.status());
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_CANCEL permission is required");
    }
    if (!command.cancellationPolicyResolved()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Tenant cancellation policy configuration is missing or ambiguous");
    }
    String cancellationId = LockModels.normalized(command.cancellationId()).isEmpty()
      ? "CANCEL-" + hash(command.tenantId() + "|" + command.lockId() + "|" + command.idempotencyKey()).substring(0, 16).toUpperCase()
      : command.cancellationId();
    String auditRef = "AUDIT-LOCK-CANCEL-" + cancellationId;
    String replayRef = "REPLAY-LOCK-CANCEL-" + cancellationHash.substring(0, 16);
    LockModels.RateLockRecord updated = new LockModels.RateLockRecord(
      current.tenantId(), current.lockId(), current.requestId(), current.quoteId(), current.loanId(), current.scenarioHash(),
      LockModels.RateLockStatus.CANCELLED, current.version() + 1, current.createdAt(), command.cancelledAt(), current.expiresAt(),
      current.idempotencyKey(), command.correlationId(), command.policyVersionId(), cancellationHash, auditRef, replayRef,
      "lock.cancelled.v1"
    );
    LockModels.LockCancellationRecord cancellation = new LockModels.LockCancellationRecord(
      command.tenantId(), cancellationId, command.lockId(), LockModels.upper(command.reasonCode()), command.actorId(),
      command.cancelledAt(), command.policyVersionId(), command.externalNotifyRequired(), cancellationHash, command.idempotencyKey(),
      command.correlationId()
    );
    LockModels.LockCancellationResponse response = new LockModels.LockCancellationResponse(
      command.tenantId(), cancellationId, command.lockId(), current.status(), updated.status(), updated.version(),
      "Lock cancelled with append-only cancellation evidence", List.of(), auditRef, replayRef, command.correlationId(),
      "lock.cancelled.v1", cancellationHash
    );
    LockModels.LockEvent event = new LockModels.LockEvent(
      "lock.cancelled.v1", "1", command.tenantId() + ":" + command.lockId(), command.tenantId(), command.lockId(),
      command.actorId(), command.correlationId(), cancellationId, command.idempotencyKey(), command.cancelledAt(), Map.of(
        "cancellationId", cancellationId,
        "previousStatus", current.status().name(),
        "status", LockModels.RateLockStatus.CANCELLED.name(),
        "version", String.valueOf(updated.version()),
        "reasonCode", LockModels.upper(command.reasonCode()),
        "policyVersion", command.policyVersionId(),
        "externalNotifyRequired", String.valueOf(command.externalNotifyRequired()),
        "evidenceHash", cancellationHash
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      auditRef, command.tenantId(), command.lockId(), "LOCK_CANCELLED", command.actorId(), current.status().name(),
      LockModels.RateLockStatus.CANCELLED.name(), command.policyVersionId(), command.complianceEvidenceRef(), command.correlationId(),
      cancellationHash
    );
    repository.saveCancellation(updated, cancellation, response, cancellationHash, event, audit);
    lockCancellationTotal++;
    return response;
  }

  public LockModels.LockCancellationResponse cancelLockReplayAware(LockModels.LockCancellationCommand command) {
    try {
      return cancelLock(command);
    } catch (CancellationIdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.LockCancellationRecord getCancellation(UUID tenantId, String cancellationId) {
    return repository.findCancellation(tenantId, cancellationId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock cancellation was not found for tenant"));
  }

  public LockModels.LockEvidenceExportResponse createEvidenceExport(LockModels.LockEvidenceExportCommand command) {
    validateEvidenceExportRequired(command);
    if (!command.redactedByDefault()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Evidence export manifest must redact and minimize borrower PII by default");
    }
    getAuditReport(command.tenantId(), command.reportId());
    String manifestHash = hash(command);
    repository.findEvidenceExportIdempotency(command.tenantId(), command.idempotencyKey(), manifestHash)
      .ifPresent(response -> { throw new EvidenceExportIdempotencyReplay(response); });
    String exportId = LockModels.normalized(command.exportId()).isEmpty()
      ? "EXPORT-" + hash(command.tenantId() + "|" + command.reportId() + "|" + command.idempotencyKey()).substring(0, 16).toUpperCase()
      : command.exportId();
    String auditRef = "AUDIT-LOCK-EVIDENCE-EXPORT-" + exportId;
    String replayRef = "REPLAY-LOCK-EVIDENCE-EXPORT-" + manifestHash.substring(0, 16);
    Map<String, String> actorRefs = Map.of("actorId", command.actorId());
    LockModels.LockEvidenceExportRecord export = new LockModels.LockEvidenceExportRecord(
      command.tenantId(), exportId, command.reportId(), command.actorId(), actorRefs, LockModels.upper(command.purposeCode()),
      true, manifestHash, command.eventIds(), command.schemaVersions(), command.configVersions(), command.snapshotHashes(),
      command.generatedFileHashes(), command.idempotencyKey(), command.correlationId(), command.generatedAt()
    );
    LockModels.LockEvidenceExportResponse response = new LockModels.LockEvidenceExportResponse(
      command.tenantId(), exportId, command.reportId(), "READY", manifestHash, command.eventIds(),
      command.schemaVersions(), command.configVersions(), command.snapshotHashes(), actorRefs, command.generatedFileHashes(), true,
      command.actorId(),
      List.of("Borrower PII minimized by default; full evidence requires external secure object storage scope"), auditRef, replayRef,
      command.correlationId(), "lock.evidence_export_manifested.v1"
    );
    LockModels.LockEvent event = new LockModels.LockEvent(
      "lock.evidence_export_manifested.v1", "1", command.tenantId() + ":" + exportId, command.tenantId(), command.reportId(),
      command.actorId(), command.correlationId(), exportId, command.idempotencyKey(), command.generatedAt(), Map.of(
        "exportId", exportId,
        "reportId", command.reportId(),
        "eventIds", String.join(",", command.eventIds()),
        "schemaVersions", manifestRefs(command.schemaVersions()),
        "configVersions", manifestRefs(command.configVersions()),
        "snapshotHashes", manifestRefs(command.snapshotHashes()),
        "actorRefs", manifestRefs(actorRefs),
        "generatedFileHashes", manifestRefs(command.generatedFileHashes()),
        "manifestHash", manifestHash,
        "redactedByDefault", "true"
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      auditRef, command.tenantId(), command.reportId(), "LOCK_EVIDENCE_EXPORT_MANIFESTED", command.actorId(), null,
      "READY", null, null, command.correlationId(), manifestHash
    );
    repository.saveEvidenceExport(export, response, manifestHash, event, audit);
    lockEvidenceExportTotal++;
    return response;
  }

  public LockModels.LockEvidenceExportResponse createEvidenceExportReplayAware(LockModels.LockEvidenceExportCommand command) {
    try {
      return createEvidenceExport(command);
    } catch (EvidenceExportIdempotencyReplay replay) {
      return replay.response;
    }
  }

  public LockModels.LockEvidenceExportRecord getEvidenceExport(UUID tenantId, String exportId) {
    return repository.findEvidenceExport(tenantId, exportId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Lock evidence export was not found for tenant"));
  }

  public LockModels.LockSyncAttemptResponse syncLockStatusReplayAware(LockModels.LockStatusSyncCommand command) {
    validateStatusSyncRequired(command);
    String payloadHash = hash(command);
    LockModels.LockSyncTarget target = command.target();
    var existingAttempt = repository.findSyncAttemptByEventTarget(command.tenantId(), command.eventId(), target.targetId());
    if (existingAttempt.isPresent()) {
      LockModels.LockSyncAttempt existing = existingAttempt.get();
      if (!existing.payloadHash().equals(payloadHash)) {
        throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Sync event and target were reused with a different payload");
      }
      return responseFor(existing, "Lock status sync replayed for existing event target", List.of(), existingPayloadEvent(existing), payloadHash);
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_SYNC_RETRY permission is required");
    }
    validateSyncTarget(target);
    if (!command.tenantId().equals(target.tenantId())) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "Sync target tenant does not match command tenant");
    }
    LockModels.RateLockRecord lock = getLock(command.tenantId(), command.lockId());

    String attemptId = "SYNC-" + hash(command.tenantId() + "|" + command.eventId() + "|" + target.targetId()).substring(0, 16).toUpperCase();
    String auditRef = "AUDIT-LOCK-SYNC-" + attemptId;
    String replayRef = "REPLAY-LOCK-SYNC-" + payloadHash.substring(0, 16);
    LockModels.LockSyncAttempt attempt = new LockModels.LockSyncAttempt(
      command.tenantId(), attemptId, command.lockId(), command.eventId(), target.targetId(),
      LockModels.LockSyncStatus.SENT, payloadHash, 0, null, null, command.correlationId(),
      target.policyVersion(), target.contractVersion(), command.requestedAt()
    );
    LockModels.LockEvent event = new LockModels.LockEvent(
      "lock.status_sync.sent.v1", "1", command.tenantId() + ":" + attemptId, command.tenantId(),
      command.lockId(), command.actorId(), command.correlationId(), command.eventId(), command.idempotencyKey(),
      command.requestedAt(), Map.of(
        "attemptId", attemptId,
        "eventId", command.eventId(),
        "targetId", target.targetId(),
        "targetSystem", target.system(),
        "lockStatus", lock.status().name(),
        "lockVersion", String.valueOf(lock.version()),
        "payloadHash", payloadHash,
        "contractVersion", target.contractVersion(),
        "policyVersion", target.policyVersion()
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      auditRef, command.tenantId(), command.lockId(), "LOCK_STATUS_SYNC_SENT", command.actorId(),
      null, LockModels.LockSyncStatus.SENT.name(), target.policyVersion(), null, command.correlationId(), payloadHash
    );
    repository.saveSyncAttempt(attempt, event, audit);
    lockSyncSentTotal++;
    return responseFor(attempt, "Lock status sync sent to configured target", List.of(), event.eventType(), payloadHash);
  }

  public LockModels.LockSyncAttemptResponse acknowledgeLockStatus(LockModels.LockStatusAckCommand command) {
    validateStatusAckRequired(command);
    String payloadHash = hash(command);
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_SYNC_ACK_WRITE permission is required");
    }
    if (!command.sourceTrusted()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Configured source trust is required for lock sync acknowledgement");
    }
    requirePolicyVersion(command.policyVersion());
    LockModels.RateLockRecord current = getLock(command.tenantId(), command.lockId());
    if (command.expectedCurrentStatus() != null && current.status() != command.expectedCurrentStatus()) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Acknowledgement expected lock status " + command.expectedCurrentStatus() + " but current status is " + current.status());
    }
    LockModels.LockSyncAttempt currentAttempt = repository.findSyncAttemptByEventTarget(command.tenantId(), command.eventId(), command.targetId())
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Sync attempt was not found for tenant event target"));
    if (repository.findSyncAcknowledgement(command.tenantId(), command.ackId()).isPresent()) {
      LockModels.LockSyncAttempt replayAttempt = repository.findSyncAttempt(command.tenantId(), currentAttempt.attemptId()).orElse(currentAttempt);
      return responseFor(replayAttempt, "Lock sync acknowledgement replayed", List.of(), ackEventType(replayAttempt.status()), replayAttempt.payloadHash());
    }

    LockModels.RateLockStatus nextLockStatus = command.requestedLockStatus();
    if (nextLockStatus != null && nextLockStatus != current.status() && !current.status().allowedNextStates().contains(nextLockStatus)) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "External correction cannot transition from " + current.status() + " to " + nextLockStatus);
    }
    LockModels.LockSyncStatus nextSyncStatus = command.ackStatus() == LockModels.LockSyncStatus.RECONCILED
      ? LockModels.LockSyncStatus.RECONCILED
      : LockModels.LockSyncStatus.ACKED;
    LockModels.RateLockRecord updatedLock = nextLockStatus == null || nextLockStatus == current.status()
      ? current
      : new LockModels.RateLockRecord(
        current.tenantId(), current.lockId(), current.requestId(), current.quoteId(), current.loanId(), current.scenarioHash(),
        nextLockStatus, current.version() + 1, current.createdAt(), command.receivedAt(), current.expiresAt(), current.idempotencyKey(),
        command.correlationId(), command.policyVersion(), current.requestHash(), "AUDIT-LOCK-SYNC-ACK-" + command.ackId(),
        "REPLAY-LOCK-SYNC-ACK-" + payloadHash.substring(0, 16), "lock.sync_reconciled.v1"
      );
    LockModels.LockSyncAttempt updatedAttempt = new LockModels.LockSyncAttempt(
      currentAttempt.tenantId(), currentAttempt.attemptId(), currentAttempt.lockId(), currentAttempt.eventId(), currentAttempt.targetId(),
      nextSyncStatus, currentAttempt.payloadHash(), currentAttempt.retryCount(), null, command.ackRef(), command.correlationId(),
      command.policyVersion(), command.contractVersion(), command.receivedAt()
    );
    LockModels.LockSyncAcknowledgement acknowledgement = new LockModels.LockSyncAcknowledgement(
      command.tenantId(), command.ackId(), command.lockId(), command.eventId(), command.targetId(), nextSyncStatus,
      command.ackRef(), payloadHash, command.receivedAt(), command.correlationId()
    );
    LockModels.LockReconciliationRecord reconciliation = null;
    if (nextSyncStatus == LockModels.LockSyncStatus.RECONCILED || updatedLock.status() != current.status()) {
      reconciliation = new LockModels.LockReconciliationRecord(
        command.tenantId(), "RECON-" + hash(command.tenantId() + "|" + command.ackId()).substring(0, 16).toUpperCase(),
        command.lockId(), command.targetId(), updatedLock.status() == current.status() ? "ACK_ONLY" : "STATUS_DRIFT",
        updatedLock.status() == current.status() ? "ACK_RECORDED" : "STATE_MACHINE_VALIDATED", command.actorId(),
        command.receivedAt(), "REPLAY-LOCK-SYNC-ACK-" + payloadHash.substring(0, 16), command.correlationId()
      );
    }
    String eventType = ackEventType(nextSyncStatus);
    LockModels.LockEvent event = new LockModels.LockEvent(
      eventType, "1", command.tenantId() + ":" + updatedAttempt.attemptId(), command.tenantId(), command.lockId(),
      command.actorId(), command.correlationId(), command.ackId(), command.idempotencyKey(), command.receivedAt(), Map.of(
        "attemptId", updatedAttempt.attemptId(),
        "ackId", command.ackId(),
        "targetId", command.targetId(),
        "syncStatus", nextSyncStatus.name(),
        "lockStatus", updatedLock.status().name(),
        "payloadHash", payloadHash,
        "policyVersion", command.policyVersion()
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      "AUDIT-LOCK-SYNC-ACK-" + command.ackId(), command.tenantId(), command.lockId(),
      nextSyncStatus == LockModels.LockSyncStatus.RECONCILED ? "LOCK_SYNC_RECONCILED" : "LOCK_SYNC_ACKED",
      command.actorId(), current.status().name(), updatedLock.status().name(), command.policyVersion(),
      null, command.correlationId(), payloadHash
    );
    repository.saveSyncAcknowledgement(updatedLock, updatedAttempt, acknowledgement, reconciliation, event, audit);
    if (nextSyncStatus == LockModels.LockSyncStatus.RECONCILED) {
      lockSyncReconciledTotal++;
    } else {
      lockSyncAckedTotal++;
    }
    return responseFor(updatedAttempt, "Lock sync acknowledgement accepted through state-machine validation", List.of(), eventType, payloadHash);
  }

  public LockModels.LockSyncAttempt getSyncAttempt(UUID tenantId, String attemptId) {
    return repository.findSyncAttempt(tenantId, attemptId)
      .orElseThrow(() -> new LockServiceException("NOT_FOUND", "Sync attempt was not found for tenant"));
  }

  public List<LockModels.LockSyncAttempt> syncStatus(UUID tenantId, String lockId) {
    getLock(tenantId, lockId);
    return repository.syncAttemptsForLock(tenantId, lockId);
  }

  public int committedSyncAttemptCount() {
    return repository.syncAttemptCount();
  }

  public int committedSyncAcknowledgementCount() {
    return repository.syncAcknowledgementCount();
  }

  public int committedReconciliationCount() {
    return repository.reconciliationCount();
  }

  public int committedAuditReportCount() {
    return repository.auditReportCount();
  }

  public int committedReplayResultCount() {
    return repository.replayResultCount();
  }

  public int committedCancellationCount() {
    return repository.cancellationCount();
  }

  public int committedEvidenceExportCount() {
    return repository.evidenceExportCount();
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

  public int committedConfirmationCount() {
    return repository.confirmationCount();
  }

  public int committedExpirationRunCount() {
    return repository.expirationRunCount();
  }

  public int committedExtensionCount() {
    return repository.extensionCount();
  }

  public int committedRelockCount() {
    return repository.relockCount();
  }

  public LockModels.MetricsSnapshot metrics() {
    return new LockModels.MetricsSnapshot(
      lockRequestTotal, lockRequestRejectedTotal, lockApprovalTotal, lockRejectionTotal,
      lockDecisionPolicyBlockedTotal, freshnessCheckTotal, freshnessPolicyResolutionFailureTotal,
      freshnessExpiresSoonTotal, lockConfirmationTotal, pendingInvestorConfirmationTotal,
      investorMismatchTotal, lockExpirationRunTotal, locksExpiringSoonTotal, locksExpiredTotal, 0,
      extensionRequestTotal, extensionApprovalTotal, extensionRejectionTotal, extensionCancellationTotal,
      extensionConfirmationFailureTotal, extensionRequestTotal == 0 ? 0.0 : (double) extensionRequestedDaysTotal / extensionRequestTotal,
      Map.copyOf(extensionFeeConfigRefsByReason), lockSyncSentTotal, lockSyncAckedTotal, lockSyncFailedTotal,
      lockSyncDlqTotal, lockSyncReconciledTotal, lockAuditReportTotal, lockReplayTotal, lockReplayMismatchTotal,
      lockCancellationTotal, lockEvidenceExportTotal
    );
  }

  private static void validateExtensionPreviewRequired(LockModels.LockExtensionPreviewCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension preview command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.requestedExpiresAt(), "requestedExpiresAt", missing);
    require(command.reasonCode(), "reasonCode", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    validateCostSnapshot(command.costSnapshot(), missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (command.requestedDays() <= 0) missing.add("requestedDays");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateStatusSyncRequired(LockModels.LockStatusSyncCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock status sync command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.eventId(), "eventId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.target(), "target", missing);
    require(command.requestedAt(), "requestedAt", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateStatusAckRequired(LockModels.LockStatusAckCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock status ack command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.ackId(), "ackId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.eventId(), "eventId", missing);
    require(command.targetId(), "targetId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.ackStatus(), "ackStatus", missing);
    require(command.ackRef(), "ackRef", missing);
    require(command.policyVersion(), "policyVersion", missing);
    require(command.contractVersion(), "contractVersion", missing);
    require(command.receivedAt(), "receivedAt", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    if (command.ackStatus() != LockModels.LockSyncStatus.ACKED && command.ackStatus() != LockModels.LockSyncStatus.RECONCILED) {
      missing.add("ackStatus ACKED or RECONCILED");
    }
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateSyncTarget(LockModels.LockSyncTarget target) {
    List<String> missing = new ArrayList<>();
    require(target.tenantId(), "target.tenantId", missing);
    require(target.targetId(), "target.targetId", missing);
    require(target.system(), "target.system", missing);
    require(target.contractVersion(), "target.contractVersion", missing);
    require(target.policyVersion(), "target.policyVersion", missing);
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
    if (!target.enabled()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Configured sync target is disabled");
    }
    requirePolicyVersion(target.policyVersion());
  }

  private static void requirePolicyVersion(String policyVersion) {
    if (LockModels.normalized(policyVersion).isEmpty()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Tenant-scoped lock sync policy version is required");
    }
  }

  private static void validateAuditReportRequired(LockModels.LockAuditReportCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock audit report command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.requestId(), "requestId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.requestedAt(), "requestedAt", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_AUDIT_READ permission is required");
    }
  }

  private static void validateReplayRequired(LockModels.LockReplayCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock replay command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.capturedInputHash(), "capturedInputHash", missing);
    require(command.configGraphHash(), "configGraphHash", missing);
    require(command.eventSequenceHash(), "eventSequenceHash", missing);
    require(command.expectedResultHash(), "expectedResultHash", missing);
    require(command.actualResultHash(), "actualResultHash", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.replayedAt(), "replayedAt", missing);
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_REPLAY_RUN permission is required");
    }
  }

  private static void validateCancellationRequired(LockModels.LockCancellationCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock cancellation command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.reasonCode(), "reasonCode", missing);
    require(command.actorId(), "actorId", missing);
    require(command.policyVersionId(), "policyVersionId", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.cancelledAt(), "cancelledAt", missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateEvidenceExportRequired(LockModels.LockEvidenceExportCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock evidence export command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.reportId(), "reportId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.purposeCode(), "purposeCode", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.generatedAt(), "generatedAt", missing);
    if (command.eventIds() == null || command.eventIds().isEmpty()) missing.add("eventIds");
    if (command.schemaVersions() == null || command.schemaVersions().isEmpty()) missing.add("schemaVersions");
    if (command.configVersions() == null || command.configVersions().isEmpty()) missing.add("configVersions");
    if (command.snapshotHashes() == null || command.snapshotHashes().isEmpty()) missing.add("snapshotHashes");
    if (command.generatedFileHashes() == null || command.generatedFileHashes().isEmpty()) missing.add("generatedFileHashes");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_AUDIT_EXPORT permission is required");
    }
  }

  private static LockModels.LockReplayMismatchClass replayMismatchClass(LockModels.LockReplayCommand command) {
    if (command.historicalRefs() == null || command.historicalRefs().isEmpty()) {
      return LockModels.LockReplayMismatchClass.CONFIG_REF_MISSING;
    }
    return LockModels.normalized(command.expectedResultHash()).equals(LockModels.normalized(command.actualResultHash()))
      ? LockModels.LockReplayMismatchClass.MATCH
      : LockModels.LockReplayMismatchClass.RESULT_MISMATCH;
  }

  private static boolean cancellationEligible(LockModels.RateLockStatus status) {
    return status == LockModels.RateLockStatus.REQUESTED
      || status == LockModels.RateLockStatus.PENDING_APPROVAL
      || status == LockModels.RateLockStatus.APPROVED
      || status == LockModels.RateLockStatus.PENDING_INVESTOR_CONFIRMATION
      || status == LockModels.RateLockStatus.ACTIVE
      || status == LockModels.RateLockStatus.EXPIRING_SOON
      || status == LockModels.RateLockStatus.EXTENSION_REQUESTED
      || status == LockModels.RateLockStatus.EXTENSION_APPROVED
      || status == LockModels.RateLockStatus.PENDING_INVESTOR_EXTENSION_CONFIRMATION
      || status == LockModels.RateLockStatus.RELOCK_REQUESTED
      || status == LockModels.RateLockStatus.RELOCK_APPROVED
      || status == LockModels.RateLockStatus.PENDING_INVESTOR_RELOCK_CONFIRMATION;
  }

  private static LockModels.LockSyncAttemptResponse responseFor(
    LockModels.LockSyncAttempt attempt,
    String summary,
    List<String> validationMessages,
    String eventType,
    String payloadHash
  ) {
    return new LockModels.LockSyncAttemptResponse(
      attempt.tenantId(), attempt.attemptId(), attempt.lockId(), attempt.eventId(), attempt.targetId(),
      attempt.status(), payloadHash, attempt.retryCount(), attempt.nextRetryAt(), attempt.ackRef(), summary,
      validationMessages, "AUDIT-LOCK-SYNC-" + attempt.attemptId(), "REPLAY-LOCK-SYNC-" + payloadHash.substring(0, 16),
      attempt.correlationId(), eventType
    );
  }

  private static String existingPayloadEvent(LockModels.LockSyncAttempt attempt) {
    return switch (attempt.status()) {
      case ACKED -> "lock.sync_acknowledged.v1";
      case RECONCILED -> "lock.sync_reconciled.v1";
      case FAILED -> "lock.sync_failed.v1";
      case DLQ -> "lock.sync_dlq.v1";
      default -> "lock.status_sync.sent.v1";
    };
  }

  private static String ackEventType(LockModels.LockSyncStatus status) {
    return status == LockModels.LockSyncStatus.RECONCILED ? "lock.sync_reconciled.v1" : "lock.sync_acknowledged.v1";
  }

  private static void validateExtensionRequestRequired(LockModels.LockExtensionRequestCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension request command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.requestId(), "requestId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.requestedAt(), "requestedAt", missing);
    require(command.requestedExpiresAt(), "requestedExpiresAt", missing);
    require(command.reasonCode(), "reasonCode", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    validateCostSnapshot(command.costSnapshot(), missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (command.requestedDays() <= 0) missing.add("requestedDays");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateExtensionDecisionRequired(LockModels.LockExtensionDecisionCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension decision command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.extensionId(), "extensionId", missing);
    require(command.decision(), "decision", missing);
    require(command.actorId(), "actorId", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.decidedAt(), "decidedAt", missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateExtensionConfirmationRequired(LockModels.LockExtensionConfirmationCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension confirmation command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.extensionId(), "extensionId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.investorConfirmationRef(), "investorConfirmationRef", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.confirmedAt(), "confirmedAt", missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateExtensionCancelRequired(LockModels.LockExtensionCancelCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "extension cancellation command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.extensionId(), "extensionId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.cancelledAt(), "cancelledAt", missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private void recordExtensionRequestMetrics(String reasonCode, LockModels.ExtensionCostSnapshot costSnapshot, int requestedDays) {
    extensionRequestTotal++;
    extensionRequestedDaysTotal += requestedDays;
    extensionFeeConfigRefsByReason.put(LockModels.upper(reasonCode), LockModels.normalized(costSnapshot.feeAmount()));
  }

  private static void validateCostSnapshot(LockModels.ExtensionCostSnapshot costSnapshot, List<String> missing) {
    if (costSnapshot == null) {
      missing.add("costSnapshot");
      return;
    }
    require(costSnapshot.priceAdjustment(), "costSnapshot.priceAdjustment", missing);
    require(costSnapshot.feeAmount(), "costSnapshot.feeAmount", missing);
    require(costSnapshot.payerType(), "costSnapshot.payerType", missing);
    require(costSnapshot.roundingMode(), "costSnapshot.roundingMode", missing);
    require(costSnapshot.reasonCode(), "costSnapshot.reasonCode", missing);
    require(costSnapshot.policyVersionId(), "costSnapshot.policyVersionId", missing);
  }

  private static void validateExtensionEligibility(
    LockModels.RateLockRecord current,
    int expectedVersion,
    int requestedDays,
    Instant requestedExpiresAt,
    LockModels.ExtensionCostSnapshot costSnapshot
  ) {
    if (current.version() != expectedVersion) {
      throw new LockServiceException("VERSION_CONFLICT", "Extension expected aggregate version " + expectedVersion + " but current version is " + current.version());
    }
    if (current.status() != LockModels.RateLockStatus.ACTIVE && current.status() != LockModels.RateLockStatus.EXPIRING_SOON) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Lock extension requires an active or expiring lock");
    }
    if (current.expiresAt() == null || requestedExpiresAt == null || !requestedExpiresAt.isAfter(current.expiresAt())) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Requested extension expiration must be after current configured expiration");
    }
    if (requestedDays <= 0) {
      throw new LockServiceException("VALIDATION_FAILED", "requestedDays must be a configured positive value");
    }
    if (costSnapshot == null || LockModels.normalized(costSnapshot.policyVersionId()).isEmpty()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Extension cost policy snapshot is required");
    }
  }

  private static void validateExtensionPolicy(
    boolean permissionGranted,
    boolean extensionPolicyResolved,
    boolean compliancePermitsAmendedTerms,
    boolean investorSupportsExtension,
    String permission
  ) {
    if (!permissionGranted) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", permission + " permission is required");
    }
    if (!extensionPolicyResolved) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Extension policy configuration is missing or ambiguous");
    }
    if (!compliancePermitsAmendedTerms) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Compliance evidence does not permit amended lock terms");
    }
    if (!investorSupportsExtension) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Investor policy does not support lock extension for this product/channel");
    }
  }

  private static LockModels.RateLockRecord extensionLockRecord(
    LockModels.RateLockRecord current,
    LockModels.RateLockStatus nextStatus,
    Instant expiresAt,
    String correlationId,
    String policyVersionId,
    String resultHash,
    String auditRef,
    String replayRef,
    String eventType,
    Instant updatedAt
  ) {
    return new LockModels.RateLockRecord(
      current.tenantId(), current.lockId(), current.requestId(), current.quoteId(), current.loanId(), current.scenarioHash(),
      nextStatus, current.version() + 1, current.createdAt(), updatedAt, expiresAt, current.idempotencyKey(), correlationId,
      policyVersionId, resultHash, auditRef, replayRef, eventType
    );
  }

  private static LockModels.LockExtensionResponse extensionResponse(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockExtensionRecord extension,
    LockModels.ExtensionCostSnapshot costSnapshot,
    BusinessDayCalculator.ExpirationCalculation expirationCalculation,
    String summary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType,
    String resultHash
  ) {
    return new LockModels.LockExtensionResponse(
      extension.tenantId(), extension.lockId(), extension.extensionId(), extension.status(), lockRecord.status(), lockRecord.version(),
      lockRecord.expiresAt(), extension.requestedDays(), extension.requestedExpiresAt(), costSnapshot,
      expirationCalculation == null ? null : expirationCalculation.breakdown(),
      expirationCalculation == null ? "" : expirationCalculation.calendarConfigHash(),
      summary, validationMessages, auditRef, replayRef, correlationId, eventType, resultHash
    );
  }

  private static LockModels.LockEvent extensionEvent(
    String eventType,
    UUID tenantId,
    String lockId,
    String extensionId,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    Instant occurredAt,
    LockModels.RateLockStatus status,
    int version,
    LockModels.ExtensionCostSnapshot costSnapshot,
    String resultHash
  ) {
    return new LockModels.LockEvent(
      eventType, "1", tenantId + ":" + lockId + ":" + extensionId, tenantId, lockId, actorId, correlationId,
      causationId, idempotencyKey, occurredAt, Map.of(
        "extensionId", extensionId,
        "status", status.name(),
        "version", String.valueOf(version),
        "policyVersion", LockModels.normalized(costSnapshot.policyVersionId()),
        "costSnapshotHash", costSnapshotHash(costSnapshot),
        "resultHash", resultHash
      )
    );
  }

  private static LockModels.AuditSnapshot extensionAudit(
    String auditRef,
    UUID tenantId,
    String lockId,
    String action,
    String actorId,
    String beforeState,
    String afterState,
    String policyVersionId,
    String complianceEvidenceRef,
    String correlationId,
    String replayHash
  ) {
    return new LockModels.AuditSnapshot(
      auditRef, tenantId, lockId, action, actorId, beforeState, afterState, policyVersionId, complianceEvidenceRef, correlationId, replayHash
    );
  }

  private static String costSnapshotHash(LockModels.ExtensionCostSnapshot costSnapshot) {
    return hash(String.join("|",
      LockModels.normalized(costSnapshot.priceAdjustment()), LockModels.normalized(costSnapshot.feeAmount()),
      LockModels.normalized(costSnapshot.payerType()), LockModels.normalized(costSnapshot.roundingMode()),
      LockModels.normalized(costSnapshot.reasonCode()), LockModels.normalized(costSnapshot.policyVersionId())
    ));
  }

  private static LockModels.ExtensionCostSnapshot extensionCostFromRecord(LockModels.LockExtensionRecord extension) {
    return new LockModels.ExtensionCostSnapshot(
      "configured-snapshot", "configured-snapshot", "configured-snapshot", "configured-snapshot",
      extension.reasonCode(), extension.policyVersionId()
    );
  }

  private static void validateRelockPreviewRequired(LockModels.RelockPreviewCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock preview command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.sourceLockId(), "sourceLockId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.currentQuoteId(), "currentQuoteId", missing);
    require(command.reasonCode(), "reasonCode", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    validateRelockSnapshots(command.originalTerms(), command.currentTerms(), command.selectedTerms(), command.policySnapshot(), missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateRelockRequestRequired(LockModels.RelockRequestCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock request command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.sourceLockId(), "sourceLockId", missing);
    require(command.requestId(), "requestId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.currentQuoteId(), "currentQuoteId", missing);
    require(command.requestedAt(), "requestedAt", missing);
    require(command.reasonCode(), "reasonCode", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    validateRelockSnapshots(command.originalTerms(), command.currentTerms(), command.selectedTerms(), command.policySnapshot(), missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateRelockDecisionRequired(LockModels.RelockDecisionCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock decision command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.sourceLockId(), "sourceLockId", missing);
    require(command.relockId(), "relockId", missing);
    require(command.decision(), "decision", missing);
    require(command.actorId(), "actorId", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.decidedAt(), "decidedAt", missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateRelockConfirmationRequired(LockModels.RelockConfirmationCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock confirmation command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.sourceLockId(), "sourceLockId", missing);
    require(command.relockId(), "relockId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.investorConfirmationRef(), "investorConfirmationRef", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.confirmedAt(), "confirmedAt", missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateRelockCancelRequired(LockModels.RelockCancelCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "relock cancellation command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.sourceLockId(), "sourceLockId", missing);
    require(command.relockId(), "relockId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    require(command.cancelledAt(), "cancelledAt", missing);
    if (command.expectedVersion() <= 0) missing.add("expectedVersion");
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
  }

  private static void validateRelockSnapshots(
    LockModels.RelockTermsSnapshot originalTerms,
    LockModels.RelockTermsSnapshot currentTerms,
    LockModels.RelockTermsSnapshot selectedTerms,
    LockModels.RelockPolicySnapshot policySnapshot,
    List<String> missing
  ) {
    validateRelockTerms(originalTerms, "originalTerms", missing);
    validateRelockTerms(currentTerms, "currentTerms", missing);
    validateRelockTerms(selectedTerms, "selectedTerms", missing);
    if (policySnapshot == null) {
      missing.add("policySnapshot");
      return;
    }
    require(policySnapshot.policyVersionId(), "policySnapshot.policyVersionId", missing);
    require(policySnapshot.selectionModeRef(), "policySnapshot.selectionModeRef", missing);
    require(policySnapshot.waitingPeriodRef(), "policySnapshot.waitingPeriodRef", missing);
    require(policySnapshot.feeTreatmentRef(), "policySnapshot.feeTreatmentRef", missing);
    require(policySnapshot.eligibilityThresholdRef(), "policySnapshot.eligibilityThresholdRef", missing);
    require(policySnapshot.benefitLedgerRef(), "policySnapshot.benefitLedgerRef", missing);
  }

  private static void validateRelockTerms(LockModels.RelockTermsSnapshot terms, String prefix, List<String> missing) {
    if (terms == null) {
      missing.add(prefix);
      return;
    }
    require(terms.productId(), prefix + ".productId", missing);
    require(terms.investorId(), prefix + ".investorId", missing);
    require(terms.rateSheetVersion(), prefix + ".rateSheetVersion", missing);
    require(terms.priceRef(), prefix + ".priceRef", missing);
    require(terms.rateRef(), prefix + ".rateRef", missing);
    require(terms.feeRef(), prefix + ".feeRef", missing);
    require(terms.lockPeriodRef(), prefix + ".lockPeriodRef", missing);
    require(terms.termsHash(), prefix + ".termsHash", missing);
  }

  private static void validateRelockEligibility(
    LockModels.RateLockRecord source,
    int expectedVersion,
    LockModels.RelockPolicySnapshot policySnapshot,
    LockModels.RelockTermsSnapshot originalTerms,
    LockModels.RelockTermsSnapshot currentTerms,
    LockModels.RelockTermsSnapshot selectedTerms
  ) {
    if (source.version() != expectedVersion) {
      throw new LockServiceException("VERSION_CONFLICT", "Relock expected aggregate version " + expectedVersion + " but current version is " + source.version());
    }
    if (source.status() != LockModels.RateLockStatus.ACTIVE
      && source.status() != LockModels.RateLockStatus.EXPIRED
      && source.status() != LockModels.RateLockStatus.CANCELLED) {
      throw new LockServiceException("LOCK_STATE_CONFLICT", "Relock requires an active, expired, or cancelled source lock");
    }
    if (!policySnapshot.sourceStateEligible() || !policySnapshot.currentQuoteFresh()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Relock source state and current quote freshness must satisfy configured policy");
    }
    if (!policySnapshot.waitingPeriodSatisfied()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Relock waiting period and cutoff must satisfy configured policy");
    }
    if (!policySnapshot.relockPolicyResolved()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Relock policy configuration is missing or ambiguous");
    }
    if (!policySnapshot.compliancePermitsSelectedTerms()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Compliance evidence does not permit selected relock terms");
    }
    if (!source.requestHash().equals(originalTerms.termsHash())) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Original lock terms hash must match source lock replay hash");
    }
    if (!selectedTerms.termsHash().equals(originalTerms.termsHash()) && !selectedTerms.termsHash().equals(currentTerms.termsHash())) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Selected relock terms must come from configured original/current comparison snapshot");
    }
  }

  private static LockModels.RateLockRecord relockLockRecord(
    LockModels.RateLockRecord current,
    LockModels.RateLockStatus nextStatus,
    String correlationId,
    String policyVersionId,
    String resultHash,
    String auditRef,
    String replayRef,
    String eventType,
    Instant updatedAt
  ) {
    return new LockModels.RateLockRecord(
      current.tenantId(), current.lockId(), current.requestId(), current.quoteId(), current.loanId(), current.scenarioHash(),
      nextStatus, current.version() + 1, current.createdAt(), updatedAt, current.expiresAt(), current.idempotencyKey(), correlationId,
      policyVersionId, resultHash, auditRef, replayRef, eventType
    );
  }

  private static LockModels.RelockResponse relockResponse(
    LockModels.RateLockRecord source,
    LockModels.RateLockRecord replacement,
    LockModels.RelockRecord relock,
    String summary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType,
    String resultHash
  ) {
    return new LockModels.RelockResponse(
      relock.tenantId(), relock.relockId(), relock.sourceLockId(), relock.replacementLockId(), relock.status(), source.status(),
      replacement.status(), source.version(), summary, validationMessages, auditRef, replayRef, correlationId, eventType, resultHash
    );
  }

  private static LockModels.LockEvent relockEvent(
    String eventType,
    UUID tenantId,
    String sourceLockId,
    String relockId,
    String replacementLockId,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    Instant occurredAt,
    LockModels.RateLockStatus sourceStatus,
    LockModels.RateLockStatus replacementStatus,
    int version,
    LockModels.RelockPolicySnapshot policySnapshot,
    String resultHash
  ) {
    return new LockModels.LockEvent(
      eventType, "1", tenantId + ":" + sourceLockId + ":" + relockId, tenantId, sourceLockId, actorId, correlationId,
      causationId, idempotencyKey, occurredAt, Map.of(
        "relockId", relockId,
        "replacementLockId", replacementLockId,
        "sourceStatus", sourceStatus.name(),
        "replacementStatus", replacementStatus.name(),
        "version", String.valueOf(version),
        "policyVersion", policySnapshot.policyVersionId(),
        "eligibilityThresholdRef", policySnapshot.eligibilityThresholdRef(),
        "benefitLedgerRef", policySnapshot.benefitLedgerRef(),
        "comparisonHash", resultHash
      )
    );
  }

  private static LockModels.AuditSnapshot relockAudit(
    String auditRef,
    UUID tenantId,
    String lockId,
    String action,
    String actorId,
    String beforeState,
    String afterState,
    String policyVersionId,
    String complianceEvidenceRef,
    String correlationId,
    String replayHash
  ) {
    return new LockModels.AuditSnapshot(
      auditRef, tenantId, lockId, action, actorId, beforeState, afterState, policyVersionId, complianceEvidenceRef, correlationId, replayHash
    );
  }

  private static LockModels.RelockPolicySnapshot relockPolicyFromRecord(LockModels.RelockRecord relock) {
    return new LockModels.RelockPolicySnapshot(
      relock.policyVersionId(), "configured-snapshot", "configured-snapshot", "configured-snapshot", "configured-snapshot",
      "configured-snapshot", true, true, true, true, true, relock.investorConfirmationRequired()
    );
  }

  private static String previewRelockId(UUID tenantId, String sourceLockId, String idempotencyKey) {
    return "RELOCK-PREVIEW-" + hash(tenantId + "|" + sourceLockId + "|" + idempotencyKey).substring(0, 16).toUpperCase();
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

  private static void validateConfirmationRequired(LockModels.LockConfirmationCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.lockId(), "lockId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.confirmationType(), "confirmationType", missing);
    require(command.lockNumber(), "lockNumber", missing);
    require(command.confirmedAt(), "confirmedAt", missing);
    require(command.expiresAt(), "expiresAt", missing);
    require(command.requestedProductId(), "requestedProductId", missing);
    require(command.responseProductId(), "responseProductId", missing);
    require(command.requestedLoanId(), "requestedLoanId", missing);
    require(command.responseLoanId(), "responseLoanId", missing);
    require(command.requestedPolicyVersionId(), "requestedPolicyVersionId", missing);
    require(command.responsePolicyVersionId(), "responsePolicyVersionId", missing);
    require(command.complianceEvidenceRef(), "complianceEvidenceRef", missing);
    require(command.idempotencyKey(), "idempotencyKey", missing);
    require(command.correlationId(), "correlationId", missing);
    if (command.expectedVersion() <= 0) {
      missing.add("expectedVersion");
    }
    if (command.lockPeriodDays() <= 0) {
      missing.add("lockPeriodDays");
    }
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", confirmationPermission(command.confirmationType()) + " permission is required");
    }
    if (!command.confirmationPolicyResolved()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Confirmation policy configuration is missing or ambiguous");
    }
    if (!command.freshnessLockable() && command.confirmationType() != LockModels.LockConfirmationType.INVESTOR_CALLBACK) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Freshness guard must be lockable immediately before confirmation");
    }
  }

  private BusinessDayCalculator.ExpirationCalculation expirationForConfirmation(LockModels.LockConfirmationCommand command) {
    if (!calendarEnabled(command.sourceRefs())) {
      return null;
    }
    return businessDayCalculator.calculateExpiration(command.tenantId(), command.confirmedAt(), command.lockPeriodDays());
  }

  private static boolean calendarEnabled(Map<String, String> sourceRefs) {
    if (sourceRefs == null) {
      return false;
    }
    return sourceRefs.containsKey("tenantCalendarConfig") || sourceRefs.containsKey("calendarConfig") || sourceRefs.containsKey("tenantCalendarConfigHash");
  }

  private static void validateExpirationRunRequired(LockModels.LockExpirationRunCommand command) {
    if (command == null) {
      throw new LockServiceException("VALIDATION_FAILED", "command is required");
    }
    List<String> missing = new ArrayList<>();
    require(command.tenantId(), "tenantId", missing);
    require(command.runId(), "runId", missing);
    require(command.actorId(), "actorId", missing);
    require(command.evaluatedAt(), "evaluatedAt", missing);
    require(command.policyVersionId(), "policyVersionId", missing);
    require(command.correlationId(), "correlationId", missing);
    if (command.warningThresholdSeconds() <= 0) {
      missing.add("warningThresholdSeconds");
    }
    if (!missing.isEmpty()) {
      throw new LockServiceException("VALIDATION_FAILED", "Missing or invalid fields: " + String.join(", ", missing));
    }
    if (!command.permissionGranted()) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", "LOCK_EXPIRATION_RUN permission is required");
    }
    if (!command.expirationPolicyResolved()) {
      throw new LockServiceException("POLICY_NOT_SATISFIED", "Expiration policy configuration is missing or ambiguous");
    }
  }

  private void saveExpirationTransition(
    LockModels.LockExpirationRunCommand command,
    LockModels.LockConfirmationRecord confirmation,
    LockModels.RateLockRecord current,
    LockModels.RateLockStatus nextStatus,
    String runHash
  ) {
    LockModels.RateLockRecord updated = new LockModels.RateLockRecord(
      current.tenantId(), current.lockId(), current.requestId(), current.quoteId(), current.loanId(), current.scenarioHash(),
      nextStatus, current.version() + 1, current.createdAt(), command.evaluatedAt(), confirmation.expiresAt(), current.idempotencyKey(),
      command.correlationId(), command.policyVersionId(), current.requestHash(), "AUDIT-LOCK-EXPIRATION-" + command.runId() + "-" + current.lockId(),
      "REPLAY-LOCK-EXPIRATION-" + runHash.substring(0, 16), expirationEventType(nextStatus)
    );
    LockModels.LockExpirationSchedule schedule = new LockModels.LockExpirationSchedule(
      command.tenantId(), current.lockId(), confirmation.expiresAt(),
      confirmation.expirationBusinessDays(), confirmation.expirationCalculatedAt(), confirmation.calendarConfigHash(), confirmation.expirationBreakdown(),
      nextStatus == LockModels.RateLockStatus.EXPIRING_SOON ? null : confirmation.expiresAt().minusSeconds(command.warningThresholdSeconds()),
      command.policyVersionId(), command.evaluatedAt()
    );
    LockModels.LockEvent event = new LockModels.LockEvent(
      expirationEventType(nextStatus), "1", command.tenantId() + ":" + current.lockId(), command.tenantId(),
      current.lockId(), command.actorId(), command.correlationId(), command.runId(), command.runId(),
      command.evaluatedAt(), Map.of(
        "runId", command.runId(),
        "previousStatus", current.status().name(),
        "status", nextStatus.name(),
        "version", String.valueOf(updated.version()),
        "expiresAt", confirmation.expiresAt().toString(),
        "policyVersion", command.policyVersionId()
      )
    );
    LockModels.AuditSnapshot audit = new LockModels.AuditSnapshot(
      updated.auditRef(), command.tenantId(), current.lockId(), expirationAuditAction(nextStatus), command.actorId(),
      current.status().name(), nextStatus.name(), command.policyVersionId(), "expiration-run:" + command.runId(),
      command.correlationId(), runHash
    );
    repository.saveExpirationEvaluation(updated, schedule, event, audit);
  }

  private static LockModels.RateLockStatus expirationStatus(
    LockModels.RateLockStatus currentStatus,
    Instant expiresAt,
    Instant evaluatedAt,
    long warningThresholdSeconds
  ) {
    if (currentStatus == LockModels.RateLockStatus.EXPIRED
      || currentStatus == LockModels.RateLockStatus.CANCELLED
      || currentStatus == LockModels.RateLockStatus.REJECTED
      || currentStatus == LockModels.RateLockStatus.INVESTOR_REJECTED) {
      return currentStatus;
    }
    if (currentStatus != LockModels.RateLockStatus.ACTIVE && currentStatus != LockModels.RateLockStatus.EXPIRING_SOON) {
      return currentStatus;
    }
    if (!expiresAt.isAfter(evaluatedAt)) {
      return LockModels.RateLockStatus.EXPIRED;
    }
    if (Duration.between(evaluatedAt, expiresAt).getSeconds() <= warningThresholdSeconds) {
      return LockModels.RateLockStatus.EXPIRING_SOON;
    }
    return LockModels.RateLockStatus.ACTIVE;
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

  private static LockModels.RateLockStatus confirmationStatus(LockModels.LockConfirmationCommand command) {
    return switch (command.confirmationType()) {
      case INTERNAL -> LockModels.RateLockStatus.ACTIVE;
      case INVESTOR_REQUEST -> LockModels.RateLockStatus.PENDING_INVESTOR_CONFIRMATION;
      case INVESTOR_CALLBACK -> confirmationTermsMatch(command)
        ? LockModels.RateLockStatus.ACTIVE
        : LockModels.RateLockStatus.INVESTOR_REJECTED;
    };
  }

  private static boolean confirmationTermsMatch(LockModels.LockConfirmationCommand command) {
    return command.investorResponseMatches()
      && LockModels.normalized(command.requestedProductId()).equals(LockModels.normalized(command.responseProductId()))
      && LockModels.normalized(command.requestedLoanId()).equals(LockModels.normalized(command.responseLoanId()))
      && LockModels.normalized(command.requestedPolicyVersionId()).equals(LockModels.normalized(command.responsePolicyVersionId()));
  }

  private static String confirmationPermission(LockModels.LockConfirmationType confirmationType) {
    return switch (confirmationType) {
      case INTERNAL -> "LOCK_CONFIRM_INTERNAL";
      case INVESTOR_REQUEST, INVESTOR_CALLBACK -> "LOCK_CONFIRM_INVESTOR";
    };
  }

  private static String confirmationEventType(LockModels.RateLockStatus status) {
    return switch (status) {
      case PENDING_INVESTOR_CONFIRMATION -> "lock.confirmation_requested.v1";
      case ACTIVE -> "lock.confirmed.v1";
      case INVESTOR_REJECTED -> "lock.investor_rejected.v1";
      default -> throw new IllegalArgumentException("Unsupported confirmation status " + status);
    };
  }

  private static String confirmationAuditAction(LockModels.RateLockStatus status) {
    return switch (status) {
      case PENDING_INVESTOR_CONFIRMATION -> "LOCK_CONFIRMATION_REQUESTED";
      case ACTIVE -> "LOCK_CONFIRMED";
      case INVESTOR_REJECTED -> "LOCK_INVESTOR_REJECTED";
      default -> throw new IllegalArgumentException("Unsupported confirmation status " + status);
    };
  }

  private static String confirmationSummary(LockModels.RateLockStatus status) {
    return switch (status) {
      case PENDING_INVESTOR_CONFIRMATION -> "Lock confirmation request queued for configured investor adapter";
      case ACTIVE -> "Lock confirmed using current tenant policy configuration";
      case INVESTOR_REJECTED -> "Investor confirmation response rejected by configured mismatch policy";
      default -> throw new IllegalArgumentException("Unsupported confirmation status " + status);
    };
  }

  private static String expirationEventType(LockModels.RateLockStatus status) {
    return switch (status) {
      case EXPIRING_SOON -> "lock.expiring_soon.v1";
      case EXPIRED -> "lock.expired.v1";
      default -> throw new IllegalArgumentException("Unsupported expiration status " + status);
    };
  }

  private static String expirationAuditAction(LockModels.RateLockStatus status) {
    return switch (status) {
      case EXPIRING_SOON -> "LOCK_EXPIRING_SOON";
      case EXPIRED -> "LOCK_EXPIRED";
      default -> throw new IllegalArgumentException("Unsupported expiration status " + status);
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

  private static String hash(LockModels.LockConfirmationCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), command.actorId(),
      String.valueOf(command.expectedVersion()), command.confirmationType().name(), command.lockNumber(),
      String.valueOf(command.lockNumberOverrideAllowed()), LockModels.normalized(command.investorId()),
      LockModels.normalized(command.investorConfirmationRef()), String.valueOf(command.lockPeriodDays()),
      command.confirmedAt().toString(), command.expiresAt().toString(), command.requestedProductId(),
      command.responseProductId(), command.requestedLoanId(), command.responseLoanId(),
      command.requestedPolicyVersionId(), command.responsePolicyVersionId(),
      String.valueOf(command.freshnessLockable()), String.valueOf(command.confirmationPolicyResolved()),
      String.valueOf(command.investorResponseMatches()), command.complianceEvidenceRef(),
      String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.LockStatusSyncCommand command) {
    LockModels.LockSyncTarget target = command.target();
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), command.eventId(), command.actorId(),
      target.targetId(), target.system(), String.valueOf(target.enabled()), target.contractVersion(),
      target.policyVersion(), command.requestedAt().toString(), String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.LockStatusAckCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.ackId(), command.lockId(), command.eventId(), command.targetId(),
      command.actorId(), String.valueOf(command.sourceTrusted()), String.valueOf(command.expectedCurrentStatus()),
      String.valueOf(command.requestedLockStatus()), command.ackStatus().name(), command.ackRef(),
      command.policyVersion(), command.contractVersion(), command.receivedAt().toString(),
      String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.LockExtensionPreviewCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), command.actorId(), String.valueOf(command.expectedVersion()),
      String.valueOf(command.requestedDays()), command.requestedExpiresAt().toString(), LockModels.upper(command.reasonCode()),
      costSnapshotHash(command.costSnapshot()), String.valueOf(command.extensionPolicyResolved()),
      String.valueOf(command.compliancePermitsAmendedTerms()), String.valueOf(command.investorSupportsExtension()),
      String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.LockExtensionRequestCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), command.requestId(), command.actorId(), String.valueOf(command.expectedVersion()),
      String.valueOf(command.requestedDays()), command.requestedAt().toString(), command.requestedExpiresAt().toString(),
      LockModels.upper(command.reasonCode()), costSnapshotHash(command.costSnapshot()), String.valueOf(command.extensionPolicyResolved()),
      String.valueOf(command.compliancePermitsAmendedTerms()), String.valueOf(command.investorSupportsExtension()),
      command.complianceEvidenceRef(), String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.LockExtensionDecisionCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), command.extensionId(), command.decision().name(), command.actorId(),
      LockModels.normalized(command.requesterActorId()), String.valueOf(command.expectedVersion()),
      String.valueOf(command.investorConfirmationRequired()), String.valueOf(command.separationOfDutiesConfigured()),
      String.valueOf(command.decisionPolicyCurrent()), String.join(",", normalizedReasons(command.reasonCodes())),
      command.complianceEvidenceRef(), command.decidedAt().toString()
    ));
  }

  private static String hash(LockModels.LockExtensionConfirmationCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), command.extensionId(), command.actorId(), String.valueOf(command.expectedVersion()),
      String.valueOf(command.investorResponseMatches()), command.investorConfirmationRef(), command.complianceEvidenceRef(),
      command.confirmedAt().toString(), String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.LockExtensionCancelCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), command.extensionId(), command.actorId(), String.valueOf(command.expectedVersion()),
      String.join(",", normalizedReasons(command.reasonCodes())), command.complianceEvidenceRef(), command.cancelledAt().toString()
    ));
  }

  private static String hash(LockModels.LockExpirationRunCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.runId(), command.actorId(), command.evaluatedAt().toString(),
      String.valueOf(command.warningThresholdSeconds()), command.policyVersionId(), String.valueOf(command.dryRun())
    ));
  }

  private static String hash(LockModels.RelockPreviewCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.sourceLockId(), command.actorId(), String.valueOf(command.expectedVersion()),
      command.currentQuoteId(), relockTermsHash(command.originalTerms()), relockTermsHash(command.currentTerms()),
      relockTermsHash(command.selectedTerms()), relockPolicyHash(command.policySnapshot()), LockModels.upper(command.reasonCode()),
      String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.RelockRequestCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.sourceLockId(), command.requestId(), command.actorId(), String.valueOf(command.expectedVersion()),
      command.currentQuoteId(), command.requestedAt().toString(), relockTermsHash(command.originalTerms()),
      relockTermsHash(command.currentTerms()), relockTermsHash(command.selectedTerms()), relockPolicyHash(command.policySnapshot()),
      LockModels.upper(command.reasonCode()), command.complianceEvidenceRef(), String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.RelockDecisionCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.sourceLockId(), command.relockId(), command.decision().name(), command.actorId(),
      LockModels.normalized(command.requesterActorId()), String.valueOf(command.expectedVersion()),
      String.valueOf(command.separationOfDutiesConfigured()), String.valueOf(command.decisionPolicyCurrent()),
      String.join(",", normalizedReasons(command.reasonCodes())), command.complianceEvidenceRef(), command.decidedAt().toString()
    ));
  }

  private static String hash(LockModels.RelockConfirmationCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.sourceLockId(), command.relockId(), command.actorId(), String.valueOf(command.expectedVersion()),
      String.valueOf(command.investorResponseMatches()), command.investorConfirmationRef(), command.complianceEvidenceRef(),
      command.confirmedAt().toString(), String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.RelockCancelCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.sourceLockId(), command.relockId(), command.actorId(), String.valueOf(command.expectedVersion()),
      String.join(",", normalizedReasons(command.reasonCodes())), command.complianceEvidenceRef(), command.cancelledAt().toString()
    ));
  }

  private static String hash(LockModels.LockAuditReportCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.requestId(), command.actorId(), command.requestedAt().toString(),
      String.valueOf(Objects.hashCode(command.criteria())), String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.LockReplayCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), LockModels.normalized(command.replayId()), command.actorId(),
      command.capturedInputHash(), command.configGraphHash(), command.eventSequenceHash(), command.expectedResultHash(),
      command.actualResultHash(), command.replayedAt().toString(), String.valueOf(Objects.hashCode(command.historicalRefs()))
    ));
  }

  private static String hash(LockModels.LockCancellationCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.lockId(), LockModels.normalized(command.cancellationId()), LockModels.upper(command.reasonCode()),
      String.valueOf(Objects.hashCode(LockModels.normalized(command.note()))), command.actorId(), String.valueOf(command.expectedVersion()),
      String.valueOf(command.externalNotifyRequired()), command.policyVersionId(), command.complianceEvidenceRef(),
      command.cancelledAt().toString(), String.valueOf(Objects.hashCode(command.sourceRefs()))
    ));
  }

  private static String hash(LockModels.LockEvidenceExportCommand command) {
    return hash(String.join("|",
      command.tenantId().toString(), command.reportId(), LockModels.normalized(command.exportId()), command.actorId(),
      String.join(",", command.eventIds()), String.valueOf(Objects.hashCode(command.schemaVersions())),
      String.valueOf(Objects.hashCode(command.configVersions())), String.valueOf(Objects.hashCode(command.snapshotHashes())),
      String.valueOf(Objects.hashCode(command.generatedFileHashes())), LockModels.upper(command.purposeCode()),
      String.valueOf(command.redactedByDefault()), command.generatedAt().toString()
    ));
  }

  private static String manifestRefs(Map<String, String> refs) {
    List<String> entries = new ArrayList<>();
    refs.entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .forEach(entry -> entries.add(entry.getKey() + "=" + entry.getValue()));
    return String.join(";", entries);
  }

  private static String relockTermsHash(LockModels.RelockTermsSnapshot terms) {
    return hash(String.join("|",
      LockModels.normalized(terms.productId()), LockModels.normalized(terms.investorId()),
      LockModels.normalized(terms.rateSheetVersion()), LockModels.normalized(terms.priceRef()),
      LockModels.normalized(terms.rateRef()), LockModels.normalized(terms.feeRef()),
      LockModels.normalized(terms.lockPeriodRef()), LockModels.normalized(terms.termsHash())
    ));
  }

  private static String relockPolicyHash(LockModels.RelockPolicySnapshot policy) {
    return hash(String.join("|",
      LockModels.normalized(policy.policyVersionId()), LockModels.normalized(policy.selectionModeRef()),
      LockModels.normalized(policy.waitingPeriodRef()), LockModels.normalized(policy.feeTreatmentRef()),
      String.valueOf(policy.sourceStateEligible()), String.valueOf(policy.currentQuoteFresh()),
      String.valueOf(policy.waitingPeriodSatisfied()), String.valueOf(policy.relockPolicyResolved()), String.valueOf(policy.compliancePermitsSelectedTerms()),
      String.valueOf(policy.investorConfirmationRequired())
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

  private static List<String> normalizedReasons(List<String> reasonCodes) {
    if (reasonCodes == null) {
      return List.of();
    }
    return reasonCodes.stream()
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

  private static final class ConfirmationIdempotencyReplay extends RuntimeException {
    private final LockModels.LockConfirmationResponse response;

    private ConfirmationIdempotencyReplay(LockModels.LockConfirmationResponse response) {
      this.response = response;
    }
  }

  private static final class ExtensionIdempotencyReplay extends RuntimeException {
    private final LockModels.LockExtensionResponse response;

    private ExtensionIdempotencyReplay(LockModels.LockExtensionResponse response) {
      this.response = response;
    }
  }

  private static final class RelockIdempotencyReplay extends RuntimeException {
    private final LockModels.RelockResponse response;

    private RelockIdempotencyReplay(LockModels.RelockResponse response) {
      this.response = response;
    }
  }

  private static final class AuditReportIdempotencyReplay extends RuntimeException {
    private final LockModels.LockAuditReportResponse response;

    private AuditReportIdempotencyReplay(LockModels.LockAuditReportResponse response) {
      this.response = response;
    }
  }

  private static final class ReplayIdempotencyReplay extends RuntimeException {
    private final LockModels.LockReplayResponse response;

    private ReplayIdempotencyReplay(LockModels.LockReplayResponse response) {
      this.response = response;
    }
  }

  private static final class CancellationIdempotencyReplay extends RuntimeException {
    private final LockModels.LockCancellationResponse response;

    private CancellationIdempotencyReplay(LockModels.LockCancellationResponse response) {
      this.response = response;
    }
  }

  private static final class EvidenceExportIdempotencyReplay extends RuntimeException {
    private final LockModels.LockEvidenceExportResponse response;

    private EvidenceExportIdempotencyReplay(LockModels.LockEvidenceExportResponse response) {
      this.response = response;
    }
  }
}
