package com.wcpe.quote;

import java.time.Instant;
import java.util.Map;

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
}
