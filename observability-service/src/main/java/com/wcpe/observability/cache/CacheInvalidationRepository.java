package com.wcpe.observability.cache;

import java.util.Optional;
import java.util.UUID;

interface CacheInvalidationRepository {
  Optional<CacheInvalidationRequest> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

  Optional<CacheInvalidationRequest> findByTenantIdSourceEventAndNamespace(
      UUID tenantId,
      String sourceEventId,
      TenantCacheNamespace namespace);

  void save(CacheInvalidationRequest request);
}
