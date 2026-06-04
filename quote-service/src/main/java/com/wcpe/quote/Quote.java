package com.wcpe.quote;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Quote(
    UUID tenantId,
    UUID quoteId,
    UUID scenarioId,
    int scenarioVersion,
    QuoteStatus status,
    String rankingPolicyId,
    String rankingPolicyVersion,
    QuoteInputVersionSet inputVersionSet,
    List<QuoteOption> options,
    Instant expiresAt,
    String auditRef,
    String replayHash,
    String idempotencyKey,
    String createdBy,
    Instant createdAt,
    String correlationId,
    int version
) {
    public Quote {
        options = List.copyOf(options == null ? List.of() : options);
    }
}
