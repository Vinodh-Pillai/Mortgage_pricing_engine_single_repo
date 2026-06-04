package com.wcpe.quote.api;

import com.wcpe.quote.QuoteOption;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record QuoteOptionResponse(
    int rank,
    String productId,
    String investorId,
    String channel,
    int lockPeriodDays,
    BigDecimal noteRatePercent,
    BigDecimal finalPriceBps,
    BigDecimal totalAdjustmentBps,
    BigDecimal marginBps,
    List<String> rankReasons,
    Map<String, String> upstreamRefs
) {
    public static QuoteOptionResponse from(QuoteOption option) {
        return new QuoteOptionResponse(
            option.rank(),
            option.productId(),
            option.investorId(),
            option.channel(),
            option.lockPeriodDays(),
            option.noteRatePercent(),
            option.finalPriceBps(),
            option.totalAdjustmentBps(),
            option.marginBps(),
            option.rankReasons(),
            option.upstreamRefs()
        );
    }
}
