package com.wcpe.observability.ratelimit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RateLimitEventEnvelope(
    UUID tenantId,
    String eventId,
    String eventType,
    int eventVersion,
    String sourceService,
    String correlationId,
    String policyKey,
    int policyVersion,
    Instant occurredAt,
    Map<String, String> payload) {
  public RateLimitEventEnvelope {
    payload = Map.copyOf(payload);
  }
}
