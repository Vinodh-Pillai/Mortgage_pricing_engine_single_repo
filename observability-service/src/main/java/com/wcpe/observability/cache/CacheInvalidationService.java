package com.wcpe.observability.cache;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CacheInvalidationService {
  public static final String REQUESTED_EVENT_TYPE = "CacheInvalidationRequested.v1";
  public static final String COMPLETED_EVENT_TYPE = "CacheInvalidationCompleted.v1";
  public static final String FAILED_EVENT_TYPE = "CacheInvalidationFailed.v1";

  private final Clock clock;
  private final CacheInvalidationRepository repository;

  public CacheInvalidationService(Clock clock, CacheInvalidationRepository repository) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.repository = Objects.requireNonNull(repository, "repository is required");
  }

  public CacheInvalidationResult processSourceEvent(
      CacheInvalidationSourceEvent event,
      String idempotencyKey) {
    Objects.requireNonNull(event, "event is required");
    idempotencyKey = SafeCacheText.requireSafeToken(idempotencyKey, "idempotencyKey", 160);
    CacheInvalidationRequest requested = CacheInvalidationRequest.requested(
        UUID.nameUUIDFromBytes((event.tenantId() + ":" + idempotencyKey).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        event,
        idempotencyKey);
    return process(requested, event.hasSupportedSchemaVersion() ? null : "SCHEMA_VERSION_UNSUPPORTED");
  }

  public CacheInvalidationResult requestManual(
      ManualCacheInvalidationCommand command,
      String idempotencyKey) {
    Objects.requireNonNull(command, "command is required");
    idempotencyKey = SafeCacheText.requireSafeToken(idempotencyKey, "idempotencyKey", 160);
    CacheInvalidationRequest requested = CacheInvalidationRequest.manual(
        UUID.nameUUIDFromBytes((command.tenantId() + ":" + idempotencyKey).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        command,
        idempotencyKey);
    return process(requested, null);
  }

  private CacheInvalidationResult process(CacheInvalidationRequest requested, String failClosedCode) {
    var byKey = repository.findByTenantIdAndIdempotencyKey(requested.tenantId(), requested.idempotencyKey());
    if (byKey.isPresent()) {
      CacheInvalidationRequest existing = byKey.get();
      if (!existing.sameCommand(requested)) {
        throw new IllegalStateException("IDEMPOTENCY_CONFLICT: idempotency key belongs to another invalidation request");
      }
      return new CacheInvalidationResult(existing.replayed(), List.of(), List.of(), metricNames(false), traceNames());
    }
    var bySource = repository.findByTenantIdSourceEventAndNamespace(
        requested.tenantId(), requested.sourceEventId(), requested.namespace());
    if (bySource.isPresent()) {
      return new CacheInvalidationResult(bySource.get().replayed(), List.of(), List.of(), metricNames(false), traceNames());
    }

    Instant completedAt = clock.instant();
    CacheInvalidationRequest finalRequest = failClosedCode == null
        ? requested.succeeded(completedAt)
        : requested.deadLettered(failClosedCode, completedAt);
    repository.save(finalRequest);
    return new CacheInvalidationResult(
        finalRequest,
        List.of(envelope(finalRequest, REQUESTED_EVENT_TYPE),
            envelope(finalRequest, failClosedCode == null ? COMPLETED_EVENT_TYPE : FAILED_EVENT_TYPE)),
        List.of(audit(finalRequest)),
        metricNames(failClosedCode == null),
        traceNames());
  }

  private CacheInvalidationEventEnvelope envelope(CacheInvalidationRequest request, String eventType) {
    return new CacheInvalidationEventEnvelope(
        request.tenantId(),
        request.id() + ":" + eventType,
        eventType,
        1,
        "observability-service",
        request.requestedBy(),
        request.correlationId(),
        request.sourceEventId(),
        request.idempotencyKey(),
        clock.instant(),
        Map.of(
            "id", request.id().toString(),
            "tenantId", request.tenantId().toString(),
            "status", request.status().name(),
            "operatorReason", request.operatorReason() == null ? "not-manual" : request.operatorReason(),
            "version", "1",
            "summary", request.namespace().value() + ":" + request.scopeType().name() + ":" + request.scopeRef(),
            "sourceRefs", request.sourceEventType() + ":" + request.sourceEventId()));
  }

  private CacheOperationAudit audit(CacheInvalidationRequest request) {
    return new CacheOperationAudit(
        request.id(),
        request.tenantId(),
        request.namespace(),
        CacheOperation.FLUSH_NAMESPACE,
        "wcp:*:tenant:" + request.tenantId() + ":" + request.namespace().value() + ":*",
        request.requestedBy(),
        request.operatorReason(),
        request.correlationId(),
        request.status().name(),
        request.lastErrorCode(),
        clock.instant());
  }

  private static List<String> metricNames(boolean success) {
    return success
        ? List.of("cache.invalidation.request.count", "cache.invalidation.duration", "cache.invalidation.lag")
        : List.of("cache.invalidation.request.count", "cache.invalidation.failed.count", "cache.invalidation.dlq.count");
  }

  private static List<String> traceNames() {
    return List.of("cache.invalidation.consume", "cache.invalidation.resolve_scope", "cache.invalidation.delete_or_bump");
  }
}
