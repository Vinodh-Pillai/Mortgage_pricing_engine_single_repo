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
  private final Map<String, LockModels.FreshnessCheckRecord> freshnessChecksByTenantAndId = new HashMap<>();
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

  int lockCount() {
    return locksByTenantAndId.size();
  }

  int freshnessCheckCount() {
    return freshnessChecksByTenantAndId.size();
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
}
