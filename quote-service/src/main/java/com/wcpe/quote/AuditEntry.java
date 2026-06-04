package com.wcpe.quote;

import java.time.Instant;
import java.util.Map;

public record AuditEntry(
    String action,
    String actorId,
    String tenantId,
    String correlationId,
    String replayHash,
    Instant occurredAt,
    Map<String, String> summary
) {
    public AuditEntry {
        summary = Map.copyOf(summary == null ? Map.of() : summary);
    }
}
