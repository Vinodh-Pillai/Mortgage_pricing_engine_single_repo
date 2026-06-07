package com.wcpe.observability.backpressure;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BackpressureEventEnvelope(
    UUID tenantId,
    String eventId,
    String eventType,
    int eventVersion,
    String sourceService,
    String actorId,
    String correlationId,
    String causationId,
    Instant occurredAt,
    Map<String, String> payload) {
  public BackpressureEventEnvelope {
    Objects.requireNonNull(tenantId, "tenantId is required");
    eventId = SafeBackpressureText.requireSafeToken(eventId, "eventId", 160);
    eventType = SafeBackpressureText.requireSafeToken(eventType, "eventType", 120);
    sourceService = SafeBackpressureText.requireSafeToken(sourceService, "sourceService", 80);
    actorId = SafeBackpressureText.requireSafeToken(actorId, "actorId", 128);
    correlationId = SafeBackpressureText.requireSafeToken(correlationId, "correlationId", 128);
    causationId = SafeBackpressureText.requireSafeToken(causationId, "causationId", 160);
    Objects.requireNonNull(occurredAt, "occurredAt is required");
    payload = Map.copyOf(Objects.requireNonNull(payload, "payload is required"));
  }
}
