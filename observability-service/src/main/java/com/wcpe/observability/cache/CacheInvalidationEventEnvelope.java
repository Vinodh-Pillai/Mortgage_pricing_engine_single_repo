package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CacheInvalidationEventEnvelope(
    UUID tenantId,
    String eventId,
    String eventType,
    int eventVersion,
    String sourceService,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    Instant occurredAt,
    Map<String, String> payload) {
  public CacheInvalidationEventEnvelope {
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    eventId = SafeCacheText.requireSafeToken(eventId, "eventId", 120);
    eventType = SafeCacheText.requireSafeToken(eventType, "eventType", 120);
    sourceService = SafeCacheText.requireSafeToken(sourceService, "sourceService", 80);
    actorId = SafeCacheText.requireSafeToken(actorId, "actorId", 120);
    correlationId = SafeCacheText.requireSafeToken(correlationId, "correlationId", 80);
    causationId = SafeCacheText.requireSafeToken(causationId, "causationId", 120);
    idempotencyKey = SafeCacheText.requireSafeToken(idempotencyKey, "idempotencyKey", 160);
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    payload = payload == null ? Map.of() : Map.copyOf(payload);
    payload.forEach((key, value) -> {
      SafeCacheText.requireSafeToken(key, "payload key", 80);
      SafeCacheText.requireSafeToken(value, "payload value", 180);
    });
    if (eventVersion != 1) {
      throw new IllegalArgumentException("eventVersion must be 1");
    }
  }
}
