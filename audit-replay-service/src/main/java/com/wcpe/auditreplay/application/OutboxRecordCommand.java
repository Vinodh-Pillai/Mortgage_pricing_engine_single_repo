package com.wcpe.auditreplay.application;

import java.util.Map;
import java.util.UUID;

public record OutboxRecordCommand(
        UUID tenantId,
        String aggregateType,
        String aggregateId,
        Long aggregateVersion,
        String eventType,
        Integer eventVersion,
        String eventKey,
        String partitionKey,
        Map<String, Object> payload,
        UUID correlationId,
        UUID causationId,
        String actorId,
        String idempotencyKey,
        String schemaRef) {
}
