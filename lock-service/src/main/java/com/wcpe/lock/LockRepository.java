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

  int lockCount() {
    return locksByTenantAndId.size();
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
}
