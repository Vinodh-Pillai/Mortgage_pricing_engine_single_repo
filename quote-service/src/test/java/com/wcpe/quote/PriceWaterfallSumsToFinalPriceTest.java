package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PriceWaterfallSumsToFinalPriceTest {
    @Test
    void acceptsWaterfallWhenComponentsSumToFinalPrice() {
        PriceWaterfall waterfall = new PriceWaterfall(
            new BigDecimal("10000.0000"),
            new BigDecimal("25.2500"),
            new BigDecimal("10.0000"),
            new BigDecimal("10035.2500"),
            Map.of("priceRef", "pricing-v1")
        );

        assertThat(waterfall.finalPriceBps()).isEqualByComparingTo("10035.2500");
        assertThat(waterfall.roundingTrace().roundedFinalPriceBps()).isEqualByComparingTo("10035.2500");
    }

    @Test
    void rejectsWaterfallWhenComponentsDoNotSumToFinalPrice() {
        assertThatThrownBy(() -> new PriceWaterfall(
            new BigDecimal("10000.0000"),
            new BigDecimal("25.2500"),
            new BigDecimal("10.0000"),
            new BigDecimal("10035.2400"),
            Map.of("priceRef", "pricing-v1")
        ))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("reconcile");
    }
}
