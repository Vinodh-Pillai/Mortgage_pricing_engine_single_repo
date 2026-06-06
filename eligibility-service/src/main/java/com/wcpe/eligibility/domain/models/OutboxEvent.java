package com.wcpe.eligibility.domain.models;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OutboxEvent(
    UUID eventId,
    UUID tenantId,
    String aggregateId,
    String eventType,
    String eventVersion,
    String aggregateType,
    Integer aggregateVersion,
    Map<String, Object> payload,
    Instant occurredAt,
    String correlationId,
    String causationId,
    String producer
) {}
