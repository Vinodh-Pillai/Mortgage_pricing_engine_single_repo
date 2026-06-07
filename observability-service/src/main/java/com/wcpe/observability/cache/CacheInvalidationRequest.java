package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CacheInvalidationRequest(
    UUID id,
    UUID tenantId,
    TenantCacheNamespace namespace,
    CacheInvalidationScopeType scopeType,
    String scopeRef,
    String sourceEventId,
    String sourceEventType,
    String versionGraphDigest,
    String idempotencyKey,
    CacheInvalidationStatus status,
    int attemptCount,
    String lastErrorCode,
    String requestedBy,
    String operatorReason,
    String correlationId,
    Instant createdAt,
    Instant completedAt,
    List<String> diagnostics) {
  public CacheInvalidationRequest {
    id = Objects.requireNonNull(id, "id is required");
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    namespace = Objects.requireNonNull(namespace, "namespace is required");
    scopeType = Objects.requireNonNull(scopeType, "scopeType is required");
    scopeRef = SafeCacheText.requireSafeToken(scopeRef, "scopeRef", 160);
    sourceEventId = SafeCacheText.requireSafeToken(sourceEventId, "sourceEventId", 120);
    sourceEventType = SafeCacheText.requireSafeToken(sourceEventType, "sourceEventType", 120);
    versionGraphDigest = SafeCacheText.requireSafeToken(versionGraphDigest, "versionGraphDigest", 160);
    idempotencyKey = SafeCacheText.requireSafeToken(idempotencyKey, "idempotencyKey", 160);
    status = Objects.requireNonNull(status, "status is required");
    lastErrorCode = lastErrorCode == null ? null : SafeCacheText.requireSafeToken(lastErrorCode, "lastErrorCode", 80);
    requestedBy = SafeCacheText.requireSafeToken(requestedBy, "requestedBy", 120);
    operatorReason = operatorReason == null ? null : SafeCacheText.requireSafeToken(operatorReason, "operatorReason", 160);
    correlationId = SafeCacheText.requireSafeToken(correlationId, "correlationId", 80);
    createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    if (attemptCount < 0) {
      throw new IllegalArgumentException("attemptCount must not be negative");
    }
  }

  static CacheInvalidationRequest requested(UUID id, CacheInvalidationSourceEvent event, String idempotencyKey) {
    return new CacheInvalidationRequest(
        id,
        event.tenantId(),
        event.namespace(),
        event.scopeType(),
        event.scopeRef(),
        event.eventId(),
        event.eventType(),
        event.versionGraphDigest(),
        idempotencyKey,
        CacheInvalidationStatus.REQUESTED,
        0,
        null,
        event.producer(),
        null,
        event.correlationId(),
        event.occurredAt(),
        null,
        List.of("cache invalidation requested from consumed event"));
  }

  static CacheInvalidationRequest manual(UUID id, ManualCacheInvalidationCommand command, String idempotencyKey) {
    return new CacheInvalidationRequest(
        id,
        command.tenantId(),
        command.namespace(),
        command.scopeType(),
        command.scopeRef(),
        "manual-" + id,
        "TenantCacheFlushRequested.v1",
        "manual-operator-request",
        idempotencyKey,
        CacheInvalidationStatus.REQUESTED,
        0,
        null,
        command.requestedBy(),
        command.operatorReason(),
        command.correlationId(),
        command.requestedAt(),
        null,
        List.of("manual invalidation accepted with operator reason and permission context"));
  }

  CacheInvalidationRequest replayed() {
    return transition(CacheInvalidationStatus.REPLAYED, attemptCount, lastErrorCode,
        List.of("duplicate idempotency key replayed without duplicate side effects"));
  }

  CacheInvalidationRequest succeeded(Instant completedAt) {
    return new CacheInvalidationRequest(id, tenantId, namespace, scopeType, scopeRef, sourceEventId,
        sourceEventType, versionGraphDigest, idempotencyKey, CacheInvalidationStatus.SUCCEEDED,
        attemptCount + 1, null, requestedBy, operatorReason, correlationId, createdAt, completedAt,
        appendDiagnostic("cache keys made unreachable by version bump or deterministic scope"));
  }

  CacheInvalidationRequest deadLettered(String errorCode, Instant completedAt) {
    return new CacheInvalidationRequest(id, tenantId, namespace, scopeType, scopeRef, sourceEventId,
        sourceEventType, versionGraphDigest, idempotencyKey, CacheInvalidationStatus.DEAD_LETTERED,
        attemptCount + 1, errorCode, requestedBy, operatorReason, correlationId, createdAt, completedAt,
        appendDiagnostic("invalid schema/version moved to dead-letter evidence"));
  }

  boolean sameCommand(CacheInvalidationRequest other) {
    return tenantId.equals(other.tenantId)
        && namespace.equals(other.namespace)
        && scopeType == other.scopeType
        && scopeRef.equals(other.scopeRef)
        && sourceEventId.equals(other.sourceEventId)
        && sourceEventType.equals(other.sourceEventType)
        && versionGraphDigest.equals(other.versionGraphDigest);
  }

  private CacheInvalidationRequest transition(
      CacheInvalidationStatus nextStatus,
      int nextAttemptCount,
      String nextErrorCode,
      List<String> nextDiagnostics) {
    return new CacheInvalidationRequest(id, tenantId, namespace, scopeType, scopeRef, sourceEventId,
        sourceEventType, versionGraphDigest, idempotencyKey, nextStatus, nextAttemptCount,
        nextErrorCode, requestedBy, operatorReason, correlationId, createdAt, completedAt, nextDiagnostics);
  }

  private List<String> appendDiagnostic(String diagnostic) {
    java.util.ArrayList<String> values = new java.util.ArrayList<>(diagnostics);
    values.add(diagnostic);
    return values;
  }
}
