package com.wcpe.lock.nonqm;

import static com.wcpe.lock.nonqm.NonQmLockModels.*;

import com.wcpe.lock.LockServiceException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class FailClosedNonQmLockRepository implements NonQmLockRepository {
  @Override
  public Optional<Object> findIdempotency(UUID tenantId, String idempotencyKey, String payloadHash) {
    return unavailable();
  }

  @Override
  public void saveIdempotency(UUID tenantId, String idempotencyKey, String payloadHash, Object response) {
    unavailable();
  }

  @Override
  public Optional<SecondaryDeliveryProfile> findDeliveryProfile(String profileCode) {
    return unavailable();
  }

  @Override
  public void saveDeliveryProfile(SecondaryDeliveryProfile profile) {
    unavailable();
  }

  @Override
  public Optional<NonQmLockPolicy> findPolicy(String policyCode) {
    return unavailable();
  }

  @Override
  public List<NonQmLockPolicy> policies() {
    return unavailable();
  }

  @Override
  public void savePolicy(NonQmLockPolicy policy) {
    unavailable();
  }

  @Override
  public Optional<NonQmLockRecord> findLock(UUID tenantId, String lockId) {
    return unavailable();
  }

  @Override
  public void saveLock(NonQmLockRecord record) {
    unavailable();
  }

  @Override
  public void saveExtension(NonQmExtensionDecision decision) {
    unavailable();
  }

  @Override
  public void saveFloatDown(UUID tenantId, String lockId, FloatDownDecision decision) {
    unavailable();
  }

  @Override
  public void addAudit(Map<String, String> auditRecord) {
    unavailable();
  }

  @Override
  public List<Map<String, String>> auditTrail() {
    return unavailable();
  }

  private static <T> T unavailable() {
    throw new LockServiceException(
      "PERSISTENCE_NOT_DURABLE",
      "Durable Non-QM lock repository is not wired; production Non-QM lock state cannot use process-local storage."
    );
  }
}
