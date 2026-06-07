package com.wcpe.auditreplay.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EventEnvelopeV1(
        UUID eventId,
        UUID tenantId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        Producer producer,
        Aggregate aggregate,
        Actor actor,
        UUID correlationId,
        UUID causationId,
        String idempotencyKey,
        String schemaRef,
        Map<String, Object> payload,
        String payloadHash,
        String previousHash,
        String integrityHash,
        List<String> legalHoldTags) {

    public record Producer(String service, String version) {}

    public record Aggregate(String type, String id, Long version) {}

    public record Actor(String type, String id) {}
}
