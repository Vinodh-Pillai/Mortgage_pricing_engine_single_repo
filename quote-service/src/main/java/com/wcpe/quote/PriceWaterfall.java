package com.wcpe.quote;

import java.math.BigDecimal;
import java.util.Map;

public record PriceWaterfall(
    BigDecimal basePriceBps,
    BigDecimal totalAdjustmentBps,
    BigDecimal marginBps,
    BigDecimal finalPriceBps,
    Map<String, String> upstreamRefs
) {
    public PriceWaterfall {
        upstreamRefs = Map.copyOf(upstreamRefs == null ? Map.of() : upstreamRefs);
    }
}
