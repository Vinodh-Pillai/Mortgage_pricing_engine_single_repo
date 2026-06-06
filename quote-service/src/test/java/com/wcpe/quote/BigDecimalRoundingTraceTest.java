package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BigDecimalRoundingTraceTest {
    @Test
    void explanationIncludesReplayableRoundingTrace() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());

        Quote quote = service.createQuote(QuoteTestSupport.request("idem-rounding-trace"));
        PriceWaterfall.RoundingTrace trace = quote.options().get(0).waterfall().roundingTrace();

        assertThat(trace.mode()).isEqualTo("HALF_UP");
        assertThat(trace.scale()).isEqualTo(4);
        assertThat(trace.roundedFinalPriceBps()).isEqualByComparingTo(quote.options().get(0).finalPriceBps());
    }
}
