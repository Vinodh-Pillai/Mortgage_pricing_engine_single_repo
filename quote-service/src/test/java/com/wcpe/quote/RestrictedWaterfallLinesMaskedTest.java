package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RestrictedWaterfallLinesMaskedTest {
    @Test
    void masksRestrictedMarginLinesUnlessAllowed() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-mask-waterfall"));
        QuoteOption option = quote.options().get(0);

        QuoteExplanationResponse masked = service.explainQuoteOption(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            option.optionId(),
            Set.of(),
            "actor-1",
            "corr-mask"
        );
        QuoteExplanationResponse unmasked = service.explainQuoteOption(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            option.optionId(),
            Set.of("margin"),
            "actor-1",
            "corr-unmask"
        );

        assertThat(masked.hiddenFields()).containsExactly("margin");
        assertThat(masked.waterfallSections())
            .flatExtracting(PriceWaterfall.WaterfallSection::lines)
            .filteredOn(line -> "margin".equals(line.lineId()))
            .first()
            .extracting(PriceWaterfall.WaterfallLine::amountBps, PriceWaterfall.WaterfallLine::visibility)
            .containsExactly(null, "MASKED");
        assertThat(unmasked.hiddenFields()).isEmpty();
        assertThat(unmasked.waterfallSections())
            .flatExtracting(PriceWaterfall.WaterfallSection::lines)
            .filteredOn(line -> "margin".equals(line.lineId()))
            .first()
            .extracting(PriceWaterfall.WaterfallLine::amountBps, PriceWaterfall.WaterfallLine::visibility)
            .containsExactly(option.marginBps(), "RESTRICTED");
    }
}
