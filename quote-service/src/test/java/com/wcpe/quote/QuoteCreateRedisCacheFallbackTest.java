package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteCreateRedisCacheFallbackTest {
    @Test
    void unavailableCacheFallsBackToRepository() {
        InMemoryQuoteCache cache = new InMemoryQuoteCache();
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), cache);
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-cache"));
        cache.setAvailable(false);

        Quote fallback = service.getQuote(QuoteTestSupport.TENANT, quote.quoteId());

        assertThat(fallback.quoteId()).isEqualTo(quote.quoteId());
        assertThat(fallback.status()).isEqualTo(QuoteStatus.READY);
    }
}
