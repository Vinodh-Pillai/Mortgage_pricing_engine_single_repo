package com.wcpe.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuoteOption(
    UUID optionId,
    String productId,
    String investorId,
    String channel,
    int lockPeriodDays,
    BigDecimal noteRatePercent,
    BigDecimal finalPriceBps,
    BigDecimal totalAdjustmentBps,
    BigDecimal marginBps,
    PriceWaterfall waterfall,
    int rank,
    BigDecimal rankScore,
    List<String> rankReasons,
    Map<String, String> upstreamRefs,
    Instant expiresAt
) {
    public QuoteOption {
        rankReasons = List.copyOf(rankReasons == null ? List.of() : rankReasons);
        upstreamRefs = Map.copyOf(upstreamRefs == null ? Map.of() : upstreamRefs);
    }
}
