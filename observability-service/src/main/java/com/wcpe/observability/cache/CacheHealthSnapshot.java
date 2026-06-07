package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CacheHealthSnapshot(
    UUID tenantId,
    TenantCacheNamespace namespace,
    RedisCacheHealthStatus status,
    long hitCount,
    long missCount,
    long fallbackCount,
    long evictionCount,
    long staleReadCount,
    Instant lastInvalidationAt,
    CacheFallbackReason fallbackReason) {
  public CacheHealthSnapshot {
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    namespace = Objects.requireNonNull(namespace, "namespace is required");
    status = Objects.requireNonNull(status, "status is required");
    requireNonNegative(hitCount, "hitCount");
    requireNonNegative(missCount, "missCount");
    requireNonNegative(fallbackCount, "fallbackCount");
    requireNonNegative(evictionCount, "evictionCount");
    requireNonNegative(staleReadCount, "staleReadCount");
  }

  public boolean degraded() {
    return status != RedisCacheHealthStatus.HEALTHY || fallbackCount > 0 || staleReadCount > 0;
  }

  private static void requireNonNegative(long value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must not be negative");
    }
  }
}
