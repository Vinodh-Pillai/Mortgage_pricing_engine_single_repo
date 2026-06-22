package com.wcpe.observability.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class TestCacheInvalidationRepository implements CacheInvalidationRepository {
  private final Map<String, CacheInvalidationRequest> byIdempotencyKey = new LinkedHashMap<>();
  private final Map<String, CacheInvalidationRequest> bySourceEventNamespace = new LinkedHashMap<>();

  @Override
  public Optional<CacheInvalidationRequest> findByTenantIdAndIdempotencyKey(
      java.util.UUID tenantId,
      String idempotencyKey) {
    return Optional.ofNullable(byIdempotencyKey.get(tenantId + "|" + idempotencyKey));
  }

  @Override
  public Optional<CacheInvalidationRequest> findByTenantIdSourceEventAndNamespace(
      java.util.UUID tenantId,
      String sourceEventId,
      TenantCacheNamespace namespace) {
    return Optional.ofNullable(bySourceEventNamespace.get(tenantId + "|" + sourceEventId + "|" + namespace.value()));
  }

  @Override
  public void save(CacheInvalidationRequest request) {
    byIdempotencyKey.put(request.tenantId() + "|" + request.idempotencyKey(), request);
    bySourceEventNamespace.put(request.tenantId() + "|" + request.sourceEventId() + "|" + request.namespace().value(), request);
  }
}
