package com.wcpe.auditreplay.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvidenceExportCommand(
        UUID tenantId,
        List<UUID> sourceAuditRecordIds,
        List<UUID> replayRunIds,
        String purpose,
        String format,
        String redactionProfile,
        boolean includeSnapshots,
        boolean includeDiffs,
        boolean includeHashes,
        String requestedBy,
        UUID correlationId,
        UUID causationId,
        String idempotencyKey,
        Instant expiresAt,
        Map<String, Object> clientContext) {
}
