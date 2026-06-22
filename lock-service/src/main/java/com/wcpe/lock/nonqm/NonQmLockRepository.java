package com.wcpe.lock.nonqm;

import static com.wcpe.lock.nonqm.NonQmLockModels.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

interface NonQmLockRepository {
  Optional<Object> findIdempotency(UUID tenantId, String idempotencyKey, String payloadHash);

  void saveIdempotency(UUID tenantId, String idempotencyKey, String payloadHash, Object response);

  Optional<SecondaryDeliveryProfile> findDeliveryProfile(String profileCode);

  void saveDeliveryProfile(SecondaryDeliveryProfile profile);

  Optional<NonQmLockPolicy> findPolicy(String policyCode);

  List<NonQmLockPolicy> policies();

  void savePolicy(NonQmLockPolicy policy);

  Optional<NonQmLockRecord> findLock(UUID tenantId, String lockId);

  void saveLock(NonQmLockRecord record);

  void saveExtension(NonQmExtensionDecision decision);

  void saveFloatDown(UUID tenantId, String lockId, FloatDownDecision decision);

  void addAudit(Map<String, String> auditRecord);

  List<Map<String, String>> auditTrail();
}
