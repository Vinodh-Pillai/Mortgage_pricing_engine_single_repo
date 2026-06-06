package com.wcpe.auditreplay.domain;

import java.time.Instant;
import java.util.Map;

public record EventEnvelope(
    String eventId,
    String tenantId,
    String eventType,
    Integer eventVersion,
    Instant occurredAt,
    String producer,
    Aggregate aggregate,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    String schemaRef,
    Map<String, Object> payload,
    String integrityHash
) {
    public record Aggregate(String type, String id, Long version) {}
}
