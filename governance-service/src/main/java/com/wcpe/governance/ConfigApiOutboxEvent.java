package com.wcpe.governance;

import java.time.Instant;
import java.util.Map;

public record ConfigApiOutboxEvent(
    String eventId,
    String eventType,
    int eventVersion,
    String tenantId,
    String aggregateId,
    String versionId,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    Instant occurredAt,
    Map<String, String> payload) {}
