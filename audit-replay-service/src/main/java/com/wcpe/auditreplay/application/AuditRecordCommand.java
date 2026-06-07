package com.wcpe.auditreplay.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AuditRecordCommand(
        UUID tenantId,
        String action,
        String subjectType,
        String subjectId,
        Long subjectVersion,
        String actorType,
        String actorId,
        String actorDisplay,
        UUID correlationId,
        UUID causationId,
        UUID requestId,
        String sourceIpHash,
        String userAgentHash,
        byte[] beforeSnapshotJson,
        byte[] afterSnapshotJson,
        byte[] snapshotJson,
        String snapshotEncryptionKeyRef,
        String redactionProfile,
        byte[] configVersionRefsJson,
        String result,
        String reasonCode,
        Instant occurredAt,
        LocalDate retentionUntil,
        boolean legalHold,
        String idempotencyKey) {
}
