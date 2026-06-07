package com.wcpe.quote;

public record QuoteEventReplayResult(
    String originalEventId,
    String deliveryId,
    OutboxEvent replayedEvent,
    AuditEntry auditEntry
) {
}
