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
  private final Map<String, LockModels.FreshnessCheckRecord> freshnessChecksByTenantAndId = new HashMap<>();
  private final Map<String, LockModels.LockConfirmationRecord> confirmationsByTenantAndId = new HashMap<>();
  private final Map<String, String> activeConfirmationByLock = new HashMap<>();
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
    }
    outboxEvents.add(event);
    auditSnapshots.add(audit);
  }

  Optional<LockModels.LockConfirmationRecord> findConfirmation(UUID tenantId, String confirmationId) {
    return Optional.ofNullable(confirmationsByTenantAndId.get(key(tenantId, confirmationId)));
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
}
