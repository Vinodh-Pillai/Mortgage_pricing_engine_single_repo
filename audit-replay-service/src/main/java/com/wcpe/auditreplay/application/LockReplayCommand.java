package com.wcpe.auditreplay.application;

import com.wcpe.auditreplay.domain.LockReplayMode;
import java.util.UUID;

public record LockReplayCommand(
        UUID tenantId,
        UUID sourceAuditRecordId,
        String sourceLockId,
        String decisionType,
        LockReplayMode mode,
        String reason,
        String requestedBy,
        String expectedHash,
        UUID correlationId,
        UUID requestId,
        String idempotencyKey) {
}
