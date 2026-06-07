package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CacheOperationAudit(
    UUID id,
    UUID tenantId,
    TenantCacheNamespace namespace,
    CacheOperation operation,
    String keyPattern,
    String requestedBy,
    String operatorReason,
    String correlationId,
    String status,
    String failureCode,
    Instant createdAt) {
  public CacheOperationAudit {
    id = Objects.requireNonNull(id, "id is required");
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    namespace = Objects.requireNonNull(namespace, "namespace is required");
    operation = Objects.requireNonNull(operation, "operation is required");
    keyPattern = keyPattern == null ? null : SafeCacheText.requireSafeToken(keyPattern, "keyPattern", 300);
    requestedBy = SafeCacheText.requireSafeToken(requestedBy, "requestedBy", 120);
    operatorReason = operatorReason == null ? null : SafeCacheText.requireSafeToken(operatorReason, "operatorReason", 160);
    correlationId = SafeCacheText.requireSafeToken(correlationId, "correlationId", 80);
    status = SafeCacheText.requireSafeToken(status, "status", 30);
    failureCode = failureCode == null ? null : SafeCacheText.requireSafeToken(failureCode, "failureCode", 80);
    createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
  }
}
