package com.wcpe.quote;

import java.math.BigDecimal;
import java.util.Map;

public record QuoteCandidate(
    String candidateId,
    String eligibilityRef,
    String productId,
    String investorId,
    String channel,
    int lockPeriodDays,
    BigDecimal noteRatePercent,
    BigDecimal basePriceBps,
    BigDecimal adjustmentBps,
    BigDecimal marginBps,
    Map<String, String> upstreamRefs
) {
    public QuoteCandidate {
        upstreamRefs = Map.copyOf(upstreamRefs == null ? Map.of() : upstreamRefs);
    }
}
