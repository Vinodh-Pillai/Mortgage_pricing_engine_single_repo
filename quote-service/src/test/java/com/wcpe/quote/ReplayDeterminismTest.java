package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReplayDeterminismTest {
    @Test
    void sameVersionedInputsProduceSameReplayHashAcrossServiceInstances() {
        Quote first = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache())
            .createQuote(QuoteTestSupport.request("idem-hash"));
        Quote second = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache())
            .createQuote(QuoteTestSupport.request("idem-hash"));

        assertThat(second.replayHash()).isEqualTo(first.replayHash());
    }
}
