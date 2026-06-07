package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ManualCacheInvalidationCommand(
    UUID tenantId,
    TenantCacheNamespace namespace,
    CacheInvalidationScopeType scopeType,
    String scopeRef,
    String requestedBy,
    String operatorReason,
    String permissionContext,
    String correlationId,
    Instant requestedAt) {
  public ManualCacheInvalidationCommand {
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    namespace = Objects.requireNonNull(namespace, "namespace is required");
    scopeType = Objects.requireNonNull(scopeType, "scopeType is required");
    scopeRef = SafeCacheText.requireSafeToken(scopeRef, "scopeRef", 160);
    requestedBy = SafeCacheText.requireSafeToken(requestedBy, "requestedBy", 120);
    operatorReason = SafeCacheText.requireSafeToken(operatorReason, "operatorReason", 160);
    permissionContext = SafeCacheText.requireSafeToken(permissionContext, "permissionContext", 160);
    correlationId = SafeCacheText.requireSafeToken(correlationId, "correlationId", 80);
    requestedAt = Objects.requireNonNull(requestedAt, "requestedAt is required");
    if (!permissionContext.contains("observability:cache-invalidation-events:write")) {
      throw new IllegalArgumentException("TENANT_ACCESS_DENIED: cache invalidation permission context is required");
    }
  }
}
