package com.wcpe.tenantcontext.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventCommand(
    String tenantId,
    UUID eventId,
    String aggregateType,
    String aggregateId,
    String topic,
    String partitionKey,
    String schemaRef,
    String eventName,
    int eventVersion,
    String envelopeJson,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    Instant occurredAt
) {
    public OutboxEventCommand {
        tenantId = required(tenantId, "tenantId");
        if (eventId == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "eventId is required");
        }
        aggregateType = optionalTrim(aggregateType);
        aggregateId = optionalTrim(aggregateId);
        topic = required(topic, "topic");
        partitionKey = required(partitionKey, "partitionKey");
        schemaRef = required(schemaRef, "schemaRef");
        eventName = required(eventName, "eventName");
        if (eventVersion < 1) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "eventVersion must be positive");
        }
        envelopeJson = required(envelopeJson, "envelopeJson");
        actorId = required(actorId, "actorId");
        correlationId = required(correlationId, "correlationId");
        causationId = optionalTrim(causationId);
        idempotencyKey = optionalTrim(idempotencyKey);
        if (occurredAt == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "occurredAt is required");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }

    private static String optionalTrim(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }
}
