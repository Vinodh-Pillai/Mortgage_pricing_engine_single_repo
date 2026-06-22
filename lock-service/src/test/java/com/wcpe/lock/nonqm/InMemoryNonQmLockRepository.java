package com.wcpe.lock.nonqm;

import static com.wcpe.lock.nonqm.NonQmLockModels.*;

import com.wcpe.lock.LockServiceException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class InMemoryNonQmLockRepository implements NonQmLockRepository {
  private final Map<String, NonQmLockPolicy> policiesByCode = new HashMap<>();
  private final Map<String, SecondaryDeliveryProfile> deliveryProfilesByCode = new HashMap<>();
  private final Map<String, NonQmLockRecord> locksByTenantAndId = new HashMap<>();
  private final Map<String, NonQmExtensionDecision> extensionsByTenantAndId = new HashMap<>();
  private final Map<String, FloatDownDecision> floatDownsByTenantAndId = new HashMap<>();
  private final Map<String, Idempotency> idempotency = new HashMap<>();
  private final List<Map<String, String>> auditTrail = new ArrayList<>();

  @Override
  public Optional<Object> findIdempotency(UUID tenantId, String idempotencyKey, String payloadHash) {
    Idempotency record = idempotency.get(idempotencyKey(tenantId, idempotencyKey));
    if (record == null) return Optional.empty();
    if (!record.payloadHash().equals(payloadHash)) {
      throw new LockServiceException("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different Non-QM payload");
    }
    return Optional.of(record.response());
  }

  @Override
  public void saveIdempotency(UUID tenantId, String idempotencyKey, String payloadHash, Object response) {
    idempotency.put(idempotencyKey(tenantId, idempotencyKey), new Idempotency(payloadHash, response));
  }

  @Override
  public Optional<SecondaryDeliveryProfile> findDeliveryProfile(String profileCode) {
    return Optional.ofNullable(deliveryProfilesByCode.get(profileCode));
  }

  @Override
  public void saveDeliveryProfile(SecondaryDeliveryProfile profile) {
    deliveryProfilesByCode.put(profile.profileCode(), profile);
  }

  @Override
  public Optional<NonQmLockPolicy> findPolicy(String policyCode) {
    return Optional.ofNullable(policiesByCode.get(policyCode));
  }

  @Override
  public List<NonQmLockPolicy> policies() {
    return List.copyOf(policiesByCode.values());
  }

  @Override
  public void savePolicy(NonQmLockPolicy policy) {
    policiesByCode.put(policy.policyCode(), policy);
  }

  @Override
  public Optional<NonQmLockRecord> findLock(UUID tenantId, String lockId) {
    return Optional.ofNullable(locksByTenantAndId.get(key(tenantId, lockId)));
  }

  @Override
  public void saveLock(NonQmLockRecord record) {
    locksByTenantAndId.put(key(record.tenantId(), record.lockId()), record);
  }

  @Override
  public void saveExtension(NonQmExtensionDecision decision) {
    extensionsByTenantAndId.put(decision.lockId() + ":" + decision.extensionId(), decision);
  }

  @Override
  public void saveFloatDown(UUID tenantId, String lockId, FloatDownDecision decision) {
    floatDownsByTenantAndId.put(key(tenantId, lockId), decision);
  }

  @Override
  public void addAudit(Map<String, String> auditRecord) {
    auditTrail.add(Map.copyOf(auditRecord));
  }

  @Override
  public List<Map<String, String>> auditTrail() {
    return List.copyOf(auditTrail);
  }

  private static String key(UUID tenantId, String id) {
    return tenantId + ":" + id;
  }

  private static String idempotencyKey(UUID tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private record Idempotency(String payloadHash, Object response) {}
}
