package com.wcpe.quote;

import java.util.List;
import java.util.UUID;

public record RankingPreviewRequest(
    UUID tenantId,
    String actorId,
    String correlationId,
    RankingPolicyRef policyRef,
    List<QuoteCandidate> candidates
) {
    public RankingPreviewRequest {
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
    }
}
