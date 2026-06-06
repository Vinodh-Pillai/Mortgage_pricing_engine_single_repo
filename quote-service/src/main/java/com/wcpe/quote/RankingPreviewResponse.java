package com.wcpe.quote;

import java.util.List;
import java.util.UUID;

public record RankingPreviewResponse(
    UUID tenantId,
    String policyId,
    String policyVersion,
    List<QuoteOption> options,
    String replayHash,
    String correlationId
) {
    public RankingPreviewResponse {
        options = List.copyOf(options == null ? List.of() : options);
    }
}
