package com.wcpe.tenantcontext.observability;

import java.time.Instant;

public record DiagnosticTimelineItem(
    String type,
    String service,
    String correlationId,
    String causationId,
    String refId,
    String status,
    String summary,
    Instant occurredAt,
    String redactionLevel
) {
    public DiagnosticTimelineItem {
        type = required(type, "type");
        service = required(service, "service");
        correlationId = required(correlationId, "correlationId");
        causationId = required(causationId, "causationId");
        refId = required(refId, "refId");
        status = required(status, "status");
        summary = required(summary, "summary");
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        redactionLevel = redactionLevel == null || redactionLevel.isBlank() ? "redacted" : redactionLevel.trim();
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
