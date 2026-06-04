package com.wcpe.quote.api;

import com.wcpe.quote.Quote;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuoteCreateResponse(
    UUID quoteId,
    String status,
    Map<String, String> inputVersionSet,
    String rankingPolicyVersion,
    Instant expiresAt,
    List<QuoteOptionResponse> options,
    String auditRef,
    String replayHash,
    String correlationId
) {
    public static QuoteCreateResponse from(Quote quote) {
        return new QuoteCreateResponse(
            quote.quoteId(),
            quote.status().name(),
            quote.inputVersionSet().asMap(),
            quote.rankingPolicyVersion(),
            quote.expiresAt(),
            quote.options().stream().map(QuoteOptionResponse::from).toList(),
            quote.auditRef(),
            quote.replayHash(),
            quote.correlationId()
        );
    }
}
