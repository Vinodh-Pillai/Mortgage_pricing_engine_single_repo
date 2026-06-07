package com.wcpe.auditreplay.application;

import com.wcpe.auditreplay.domain.QuoteReplayMode;
import java.util.UUID;

public record QuoteReplayCommand(
        UUID tenantId,
        UUID sourceAuditRecordId,
        String sourceQuoteId,
        QuoteReplayMode mode,
        String reason,
        String requestedBy,
        String expectedHash,
        UUID correlationId,
        UUID requestId,
        String idempotencyKey) {
}
