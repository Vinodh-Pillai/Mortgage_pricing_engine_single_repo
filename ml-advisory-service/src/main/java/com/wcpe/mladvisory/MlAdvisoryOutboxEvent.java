package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.Map;

public record MlAdvisoryOutboxEvent(
    String eventId,
    String eventType,
    String tenantId,
    String aggregateId,
    String actorId,
    String correlationId,
    String idempotencyKey,
    Instant occurredAt,
    Map<String, String> payload) {}
