package com.wcpe.lock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class LockRepository {
  private final Map<String, LockModels.RateLockRecord> locksByTenantAndId = new HashMap<>();
  private final Map<String, String> activeQuoteIndex = new HashMap<>();
  private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();
  private final Map<String, DecisionIdempotencyRecord> decisionIdempotency = new HashMap<>();
  private final Map<String, FreshnessIdempotencyRecord> freshnessIdempotency = new HashMap<>();
  private final Map<String, ConfirmationIdempotencyRecord> confirmationIdempotency = new HashMap<>();
  private final Map<String, ExtensionIdempotencyRecord> extensionIdempotency = new HashMap<>();
  private final Map<String, RelockIdempotencyRecord> relockIdempotency = new HashMap<>();
  private final Map<String, LockModels.FreshnessCheckRecord> freshnessChecksByTenantAndId = new HashMap<>();
  private final Map<String, LockModels.LockConfirmationRecord> confirmationsByTenantAndId = new HashMap<>();
  private final Map<String, LockModels.LockExtensionRecord> extensionsByTenantAndId = new HashMap<>();
  private final Map<String, LockModels.RelockRecord> relocksByTenantAndId = new HashMap<>();
  private final Map<String, LockModels.LockExpirationSchedule> expirationSchedulesByTenantAndLock = new HashMap<>();
  private final Map<String, LockModels.LockExpirationRunRecord> expirationRunsByTenantAndId = new HashMap<>();
  private final Map<String, LockModels.LockSyncAttempt> syncAttemptsByTenantAndId = new HashMap<>();
  private final Map<String, String> syncAttemptByTenantEventTarget = new HashMap<>();
  private final Map<String, LockModels.LockSyncAcknowledgement> syncAcksByTenantAndId = new HashMap<>();
  private final Map<String, LockModels.LockReconciliationRecord> reconciliationByTenantAndId = new HashMap<>();
  private final Map<String, String> activeConfirmationByLock = new HashMap<>();
  private final Map<String, String> openExtensionByLock = new HashMap<>();
  private final Map<String, String> openRelockBySourceLock = new HashMap<>();
  private final Map<String, String> lockNumberIndex = new HashMap<>();
  private final Map<String, String> investorExternalRefIndex = new HashMap<>();
  private final List<LockModels.LockEvent> outboxEvents = new ArrayList<>();
  private final List<LockModels.AuditSnapshot> auditSnapshots = new ArrayList<>();

  Optional<LockModels.LockRequestResponse> findIdempotency(UUID tenantId, String idempotencyKey, String requestHash) {
    IdempotencyRecord record = idempotency.get(tenantId + ":" + idempotencyKey);
    if (record == null) {
      return Optional.empty();
    }
    if (!record.requestHash.equals(requestHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different payload");
    }
    return Optional.of(record.response);
  }

  boolean hasActiveQuote(UUID tenantId, String quoteId) {
    return activeQuoteIndex.containsKey(tenantId + ":" + quoteId);
  }

  void saveCommitted(
    LockModels.RateLockRecord record,
    LockModels.LockRequestResponse response,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    locksByTenantAndId.put(key(record.tenantId(), record.lockId()), record);
    if (record.status().active()) {
      activeQuoteIndex.put(record.tenantId() + ":" + record.quoteId(), record.lockId());
    }
    idempotency.put(record.tenantId() + ":" + record.idempotencyKey(), new IdempotencyRecord(record.requestHash(), response));
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  Optional<LockModels.RateLockRecord> find(UUID tenantId, String lockId) {
    return Optional.ofNullable(locksByTenantAndId.get(key(tenantId, lockId)));
  }

  void replace(LockModels.RateLockRecord record) {
    locksByTenantAndId.put(key(record.tenantId(), record.lockId()), record);
    if (!record.status().active()) {
      activeQuoteIndex.remove(record.tenantId() + ":" + record.quoteId());
      activeConfirmationByLock.remove(record.tenantId() + ":" + record.lockId());
    }
  }

  Optional<LockModels.LockDecisionResponse> findDecisionIdempotency(UUID tenantId, String idempotencyKey, String decisionHash) {
    DecisionIdempotencyRecord record = decisionIdempotency.get(tenantId + ":" + idempotencyKey);
    if (record == null) {
      return Optional.empty();
    }
    if (!record.decisionHash.equals(decisionHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Decision idempotency key was reused with a different payload");
    }
    return Optional.of(record.response);
  }

  void saveDecision(
    LockModels.RateLockRecord record,
    LockModels.LockDecisionResponse response,
    String idempotencyKey,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    replace(record);
    decisionIdempotency.put(record.tenantId() + ":" + idempotencyKey, new DecisionIdempotencyRecord(response.decisionHash(), response));
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  Optional<LockModels.FreshnessCheckResponse> findFreshnessIdempotency(UUID tenantId, String idempotencyKey, String resultHash) {
    FreshnessIdempotencyRecord record = freshnessIdempotency.get(tenantId + ":" + idempotencyKey);
    if (record == null) {
      return Optional.empty();
    }
    if (!record.resultHash.equals(resultHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Freshness idempotency key was reused with a different payload");
    }
    return Optional.of(record.response);
  }

  void saveFreshnessCheck(
    LockModels.FreshnessCheckRecord record,
    LockModels.FreshnessCheckResponse response,
    String idempotencyKey,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    freshnessChecksByTenantAndId.put(key(record.tenantId(), record.checkId()), record);
    freshnessIdempotency.put(record.tenantId() + ":" + idempotencyKey, new FreshnessIdempotencyRecord(record.resultHash(), response));
    if (event != null) {
      outboxEvents.add(event);
    }
    if (audit != null) {
      auditSnapshots.add(audit);
    }
  }

  Optional<LockModels.FreshnessCheckRecord> findFreshnessCheck(UUID tenantId, String checkId) {
    return Optional.ofNullable(freshnessChecksByTenantAndId.get(key(tenantId, checkId)));
  }

  Optional<LockModels.LockConfirmationResponse> findConfirmationIdempotency(UUID tenantId, String idempotencyKey, String confirmationHash) {
    ConfirmationIdempotencyRecord record = confirmationIdempotency.get(tenantId + ":" + idempotencyKey);
    if (record == null) {
      return Optional.empty();
    }
    if (!record.confirmationHash.equals(confirmationHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Confirmation idempotency key was reused with a different payload");
    }
    return Optional.of(record.response);
  }

  Optional<LockModels.LockExtensionResponse> findExtensionIdempotency(UUID tenantId, String idempotencyKey, String resultHash) {
    ExtensionIdempotencyRecord record = extensionIdempotency.get(tenantId + ":" + idempotencyKey);
    if (record == null) {
      return Optional.empty();
    }
    if (!record.resultHash.equals(resultHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Extension idempotency key was reused with a different payload");
    }
    return Optional.of(record.response);
  }

  Optional<LockModels.RelockResponse> findRelockIdempotency(UUID tenantId, String idempotencyKey, String resultHash) {
    RelockIdempotencyRecord record = relockIdempotency.get(tenantId + ":" + idempotencyKey);
    if (record == null) {
      return Optional.empty();
    }
    if (!record.resultHash.equals(resultHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Relock idempotency key was reused with a different payload");
    }
    return Optional.of(record.response);
  }

  boolean hasActiveConfirmation(UUID tenantId, String lockId) {
    return activeConfirmationByLock.containsKey(tenantId + ":" + lockId);
  }

  boolean hasLockNumber(UUID tenantId, String lockNumber) {
    return lockNumber != null && lockNumberIndex.containsKey(tenantId + ":" + lockNumber);
  }

  boolean hasInvestorExternalRef(UUID tenantId, String investorId, String investorConfirmationRef) {
    return investorId != null && investorConfirmationRef != null
      && investorExternalRefIndex.containsKey(tenantId + ":" + investorId + ":" + investorConfirmationRef);
  }

  void saveConfirmation(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockConfirmationRecord confirmation,
    LockModels.LockConfirmationResponse response,
    String confirmationHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    replace(lockRecord);
    confirmationsByTenantAndId.put(key(confirmation.tenantId(), confirmation.confirmationId()), confirmation);
    confirmationIdempotency.put(confirmation.tenantId() + ":" + confirmation.idempotencyKey(), new ConfirmationIdempotencyRecord(confirmationHash, response));
    if (confirmation.status() == LockModels.RateLockStatus.ACTIVE) {
      activeConfirmationByLock.put(confirmation.tenantId() + ":" + confirmation.lockId(), confirmation.confirmationId());
      lockNumberIndex.put(confirmation.tenantId() + ":" + confirmation.lockNumber(), confirmation.confirmationId());
      if (!LockModels.normalized(confirmation.investorId()).isEmpty() && !LockModels.normalized(confirmation.investorConfirmationRef()).isEmpty()) {
        investorExternalRefIndex.put(confirmation.tenantId() + ":" + confirmation.investorId() + ":" + confirmation.investorConfirmationRef(), confirmation.confirmationId());
      }
      expirationSchedulesByTenantAndLock.put(
        confirmation.tenantId() + ":" + confirmation.lockId(),
        new LockModels.LockExpirationSchedule(
          confirmation.tenantId(), confirmation.lockId(), confirmation.expiresAt(), null, lockRecord.lockPolicyVersionId(), null
        )
      );
    }
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  Optional<LockModels.LockConfirmationRecord> findConfirmation(UUID tenantId, String confirmationId) {
    return Optional.ofNullable(confirmationsByTenantAndId.get(key(tenantId, confirmationId)));
  }

  boolean hasOpenExtension(UUID tenantId, String lockId) {
    return openExtensionByLock.containsKey(tenantId + ":" + lockId);
  }

  boolean hasOpenRelock(UUID tenantId, String sourceLockId) {
    return openRelockBySourceLock.containsKey(tenantId + ":" + sourceLockId);
  }

  Optional<LockModels.LockExtensionRecord> findExtension(UUID tenantId, String extensionId) {
    return Optional.ofNullable(extensionsByTenantAndId.get(key(tenantId, extensionId)));
  }

  Optional<LockModels.RelockRecord> findRelock(UUID tenantId, String relockId) {
    return Optional.ofNullable(relocksByTenantAndId.get(key(tenantId, relockId)));
  }

  void saveExtensionRequest(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockExtensionRecord extension,
    LockModels.LockExtensionResponse response,
    String resultHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    replace(lockRecord);
    extensionsByTenantAndId.put(key(extension.tenantId(), extension.extensionId()), extension);
    openExtensionByLock.put(extension.tenantId() + ":" + extension.lockId(), extension.extensionId());
    extensionIdempotency.put(extension.tenantId() + ":" + extension.idempotencyKey(), new ExtensionIdempotencyRecord(resultHash, response));
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  void saveExtensionUpdate(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockExtensionRecord extension,
    LockModels.LockExtensionResponse response,
    String idempotencyKey,
    String resultHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    replace(lockRecord);
    extensionsByTenantAndId.put(key(extension.tenantId(), extension.extensionId()), extension);
    if (extension.status() == LockModels.LockExtensionStatus.CONFIRMED
      || extension.status() == LockModels.LockExtensionStatus.REJECTED
      || extension.status() == LockModels.LockExtensionStatus.CANCELLED) {
      openExtensionByLock.remove(extension.tenantId() + ":" + extension.lockId());
      if (extension.status() == LockModels.LockExtensionStatus.CONFIRMED && lockRecord.status() == LockModels.RateLockStatus.ACTIVE) {
        expirationSchedulesByTenantAndLock.put(
          extension.tenantId() + ":" + extension.lockId(),
          new LockModels.LockExpirationSchedule(
            extension.tenantId(), extension.lockId(), extension.requestedExpiresAt(), null, extension.policyVersionId(), null
          )
        );
      }
    } else {
      openExtensionByLock.put(extension.tenantId() + ":" + extension.lockId(), extension.extensionId());
    }
    extensionIdempotency.put(extension.tenantId() + ":" + idempotencyKey, new ExtensionIdempotencyRecord(resultHash, response));
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  void saveRelockRequest(
    LockModels.RateLockRecord sourceLock,
    LockModels.RateLockRecord replacementLock,
    LockModels.RelockRecord relock,
    LockModels.RelockResponse response,
    String resultHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    replace(sourceLock);
    locksByTenantAndId.put(key(replacementLock.tenantId(), replacementLock.lockId()), replacementLock);
    if (replacementLock.status().active()) {
      activeQuoteIndex.put(replacementLock.tenantId() + ":" + replacementLock.quoteId(), replacementLock.lockId());
    }
    relocksByTenantAndId.put(key(relock.tenantId(), relock.relockId()), relock);
    openRelockBySourceLock.put(relock.tenantId() + ":" + relock.sourceLockId(), relock.relockId());
    relockIdempotency.put(relock.tenantId() + ":" + relock.idempotencyKey(), new RelockIdempotencyRecord(resultHash, response));
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  void saveRelockUpdate(
    LockModels.RateLockRecord sourceLock,
    LockModels.RateLockRecord replacementLock,
    LockModels.RelockRecord relock,
    LockModels.RelockResponse response,
    String idempotencyKey,
    String resultHash,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    replace(sourceLock);
    replace(replacementLock);
    relocksByTenantAndId.put(key(relock.tenantId(), relock.relockId()), relock);
    if (relock.status() == LockModels.RelockStatus.CONFIRMED
      || relock.status() == LockModels.RelockStatus.REJECTED
      || relock.status() == LockModels.RelockStatus.CANCELLED) {
      openRelockBySourceLock.remove(relock.tenantId() + ":" + relock.sourceLockId());
    } else {
      openRelockBySourceLock.put(relock.tenantId() + ":" + relock.sourceLockId(), relock.relockId());
    }
    relockIdempotency.put(relock.tenantId() + ":" + idempotencyKey, new RelockIdempotencyRecord(resultHash, response));
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  List<LockModels.LockConfirmationRecord> activeConfirmations(UUID tenantId) {
    return activeConfirmationByLock.entrySet().stream()
      .filter(entry -> entry.getKey().startsWith(tenantId + ":"))
      .map(entry -> confirmationsByTenantAndId.get(key(tenantId, entry.getValue())))
      .filter(record -> record != null && record.status() == LockModels.RateLockStatus.ACTIVE)
      .toList();
  }

  Optional<LockModels.LockExpirationSchedule> findExpirationSchedule(UUID tenantId, String lockId) {
    return Optional.ofNullable(expirationSchedulesByTenantAndLock.get(tenantId + ":" + lockId));
  }

  void saveExpirationEvaluation(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockExpirationSchedule schedule,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    replace(lockRecord);
    expirationSchedulesByTenantAndLock.put(schedule.tenantId() + ":" + schedule.lockId(), schedule);
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  void saveExpirationRun(LockModels.LockExpirationRunRecord runRecord) {
    expirationRunsByTenantAndId.put(key(runRecord.tenantId(), runRecord.runId()), runRecord);
  }

  Optional<LockModels.LockExpirationRunRecord> findExpirationRun(UUID tenantId, String runId) {
    return Optional.ofNullable(expirationRunsByTenantAndId.get(key(tenantId, runId)));
  }

  Optional<LockModels.LockSyncAttempt> findSyncAttempt(UUID tenantId, String attemptId) {
    return Optional.ofNullable(syncAttemptsByTenantAndId.get(key(tenantId, attemptId)));
  }

  Optional<LockModels.LockSyncAttempt> findSyncAttemptByEventTarget(UUID tenantId, String eventId, String targetId) {
    String attemptId = syncAttemptByTenantEventTarget.get(tenantId + ":" + eventId + ":" + targetId);
    return attemptId == null ? Optional.empty() : findSyncAttempt(tenantId, attemptId);
  }

  List<LockModels.LockSyncAttempt> syncAttemptsForLock(UUID tenantId, String lockId) {
    return syncAttemptsByTenantAndId.values().stream()
      .filter(attempt -> attempt.tenantId().equals(tenantId) && attempt.lockId().equals(lockId))
      .toList();
  }

  void saveSyncAttempt(
    LockModels.LockSyncAttempt attempt,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    syncAttemptsByTenantAndId.put(key(attempt.tenantId(), attempt.attemptId()), attempt);
    syncAttemptByTenantEventTarget.put(attempt.tenantId() + ":" + attempt.eventId() + ":" + attempt.targetId(), attempt.attemptId());
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  void saveSyncAcknowledgement(
    LockModels.RateLockRecord lockRecord,
    LockModels.LockSyncAttempt attempt,
    LockModels.LockSyncAcknowledgement acknowledgement,
    LockModels.LockReconciliationRecord reconciliation,
    LockModels.LockEvent event,
    LockModels.AuditSnapshot audit
  ) {
    replace(lockRecord);
    syncAttemptsByTenantAndId.put(key(attempt.tenantId(), attempt.attemptId()), attempt);
    syncAcksByTenantAndId.put(key(acknowledgement.tenantId(), acknowledgement.ackId()), acknowledgement);
    if (reconciliation != null) {
      reconciliationByTenantAndId.put(key(reconciliation.tenantId(), reconciliation.recordId()), reconciliation);
    }
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  Optional<LockModels.LockSyncAcknowledgement> findSyncAcknowledgement(UUID tenantId, String ackId) {
    return Optional.ofNullable(syncAcksByTenantAndId.get(key(tenantId, ackId)));
  }

  Optional<LockModels.LockReconciliationRecord> findReconciliation(UUID tenantId, String recordId) {
    return Optional.ofNullable(reconciliationByTenantAndId.get(key(tenantId, recordId)));
  }

  int lockCount() {
    return locksByTenantAndId.size();
  }

  int freshnessCheckCount() {
    return freshnessChecksByTenantAndId.size();
  }

  int confirmationCount() {
    return confirmationsByTenantAndId.size();
  }

  int expirationRunCount() {
    return expirationRunsByTenantAndId.size();
  }

  int extensionCount() {
    return extensionsByTenantAndId.size();
  }

  int relockCount() {
    return relocksByTenantAndId.size();
  }

  int syncAttemptCount() {
    return syncAttemptsByTenantAndId.size();
  }

  int syncAcknowledgementCount() {
    return syncAcksByTenantAndId.size();
  }

  int reconciliationCount() {
    return reconciliationByTenantAndId.size();
  }

  List<LockModels.LockEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  List<LockModels.AuditSnapshot> auditSnapshots() {
    return List.copyOf(auditSnapshots);
  }

  private static String key(UUID tenantId, String lockId) {
    return tenantId + ":" + lockId;
  }

  private record IdempotencyRecord(String requestHash, LockModels.LockRequestResponse response) {}

  private record DecisionIdempotencyRecord(String decisionHash, LockModels.LockDecisionResponse response) {}

  private record FreshnessIdempotencyRecord(String resultHash, LockModels.FreshnessCheckResponse response) {}

  private record ConfirmationIdempotencyRecord(String confirmationHash, LockModels.LockConfirmationResponse response) {}

  private record ExtensionIdempotencyRecord(String resultHash, LockModels.LockExtensionResponse response) {}

  private record RelockIdempotencyRecord(String resultHash, LockModels.RelockResponse response) {}
}
