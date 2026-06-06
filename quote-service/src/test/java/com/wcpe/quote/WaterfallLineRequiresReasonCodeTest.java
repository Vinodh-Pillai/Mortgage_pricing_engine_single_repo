package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WaterfallLineRequiresReasonCodeTest {
    @Test
    void rejectsLineWithoutReasonCode() {
        PriceWaterfall.WaterfallLine line = new PriceWaterfall.WaterfallLine(
            "base-price",
            "Base price",
            " ",
            "pricing-service",
            "pricing-v1",
            "+",
            new BigDecimal("10000.0000"),
            "VISIBLE",
            false
        );

        assertThatThrownBy(() -> new PriceWaterfall(
            new BigDecimal("10000.0000"),
            BigDecimal.ZERO.setScale(4),
            BigDecimal.ZERO.setScale(4),
            new BigDecimal("10000.0000"),
            Map.of("priceRef", "pricing-v1"),
            List.of(new PriceWaterfall.WaterfallSection("base-price", "Base price", 1, List.of(line))),
            new PriceWaterfall.RoundingTrace("HALF_UP", 4, new BigDecimal("10000.0000"), new BigDecimal("10000.0000"))
        ))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("reason code");
    }
}
