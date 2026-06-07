package com.wcpe.tenantcontext.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
    UUID eventId,
    String eventName,
    int eventVersion,
    Instant occurredAt,
    String tenantId,
    EventActor actor,
    String correlationId,
    String causationId,
    String idempotencyKey,
    String sourceService,
    String schemaRef,
    DataClassification dataClassification,
    String payloadHash,
    String payloadJson
) {
    public EventEnvelope {
        if (eventId == null) {
            throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", "eventId is required");
        }
        eventName = required(eventName, "eventName");
        if (eventVersion < 1) {
            throw new EventEnvelopeValidationException("EVENT_VERSION_UNSUPPORTED", "eventVersion must be positive");
        }
        if (occurredAt == null) {
            throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", "occurredAt is required");
        }
        tenantId = required(tenantId, "tenantId");
        if (actor == null) {
            throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", "actor is required");
        }
        correlationId = required(correlationId, "correlationId");
        causationId = required(causationId, "causationId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        sourceService = required(sourceService, "sourceService");
        schemaRef = required(schemaRef, "schemaRef");
        dataClassification = DataClassification.require(dataClassification);
        payloadHash = required(payloadHash, "payloadHash");
        if (!payloadHash.startsWith("sha256:")) {
            throw new EventEnvelopeValidationException("PAYLOAD_HASH_MISMATCH", "payloadHash must use sha256 prefix");
        }
        payloadJson = required(payloadJson, "payloadJson");
    }

    public String topicName(String boundedContext, String aggregateOrStream) {
        return required(boundedContext, "boundedContext") + "." + required(aggregateOrStream, "aggregateOrStream") + ".v" + eventVersion;
    }

    public String partitionKey(String aggregateIdOrEventId) {
        String stableId = aggregateIdOrEventId == null || aggregateIdOrEventId.isBlank()
            ? eventId.toString()
            : aggregateIdOrEventId.trim();
        return tenantId + ":" + stableId;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }
}
