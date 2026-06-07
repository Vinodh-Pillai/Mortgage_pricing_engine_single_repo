package com.wcpe.tenantcontext.consumer;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
    String tenantId,
    UUID eventId,
    String eventName,
    String schemaRef,
    int schemaVersion,
    String payloadJson,
    String correlationId,
    String causationId,
    Instant occurredAt
) {
    public EventEnvelope {
        tenantId = required(tenantId, "tenantId");
        if (eventId == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "eventId is required");
        }
        eventName = required(eventName, "eventName");
        schemaRef = required(schemaRef, "schemaRef");
        if (schemaVersion < 1) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "schemaVersion must be positive");
        }
        payloadJson = required(payloadJson, "payloadJson");
        correlationId = optional(correlationId);
        causationId = optional(causationId);
        if (occurredAt == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "occurredAt is required");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
