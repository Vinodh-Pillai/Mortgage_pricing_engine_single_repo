package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PriceWaterfallBuilderUsesBigDecimalScaleTest {
    @Test
    void finalPriceUsesBigDecimalAndFourDecimalScale() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());

        Quote quote = service.createQuote(QuoteTestSupport.request("idem-scale"));

        QuoteOption option = quote.options().get(0);
        assertThat(option.finalPriceBps().scale()).isEqualTo(4);
        assertThat(option.noteRatePercent().scale()).isEqualTo(5);
        assertThat(option.waterfall().finalPriceBps()).isEqualByComparingTo(option.finalPriceBps());
    }
}
