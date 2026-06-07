package com.wcpe.quote;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record OutboxEvent(
    String eventType,
    String eventVersion,
    String key,
    Instant occurredAt,
    Map<String, String> headers,
    Map<String, String> payload
) {
    public OutboxEvent {
        headers = Map.copyOf(headers == null ? Map.of() : headers);
        payload = Map.copyOf(payload == null ? Map.of() : payload);
    }

    public String eventId() {
        return headers.getOrDefault("eventId", UUID.nameUUIDFromBytes((key + ":" + eventType + ":" + eventVersion).getBytes(StandardCharsets.UTF_8)).toString());
    }

    public String tenantId() {
        return headers.getOrDefault("tenantId", payload.getOrDefault("tenantId", ""));
    }

    public String aggregateId() {
        return headers.getOrDefault("aggregateId", payload.getOrDefault("quoteId", payload.getOrDefault("jobId", "")));
    }

    public Map<String, String> envelopeHeaders() {
        Map<String, String> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", eventVersion);
        envelope.put("tenantId", tenantId());
        envelope.put("aggregateType", headers.getOrDefault("aggregateType", "Quote"));
        envelope.put("aggregateId", aggregateId());
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("correlationId", headers.getOrDefault("correlationId", ""));
        envelope.put("causationId", headers.getOrDefault("causationId", eventId()));
        envelope.put("idempotencyKey", headers.getOrDefault("idempotencyKey", ""));
        envelope.put("schemaRef", headers.getOrDefault("schemaRef", "quote-service/events/" + eventType + ".schema.json"));
        envelope.put("producer", headers.getOrDefault("producer", headers.getOrDefault("sourceService", "quote-service")));
        envelope.put("piiClassification", headers.getOrDefault("piiClassification", "NO_BORROWER_PII"));
        envelope.putAll(headers);
        return Map.copyOf(envelope);
    }
}
