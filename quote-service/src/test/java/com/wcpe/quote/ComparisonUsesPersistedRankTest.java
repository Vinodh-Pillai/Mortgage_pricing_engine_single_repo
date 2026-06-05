package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ComparisonUsesPersistedRankTest {
    @Test
    void defaultRowOrderUsesPersistedQuoteOptionRank() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-persisted-rank"));

        QuoteComparisonResponse response = service.compareQuoteOptions(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            QuoteTestSupport.comparisonView(),
            Set.of("margin", "investorLabel"),
            "actor-1",
            "corr-rank"
        );

        assertThat(response.rows()).extracting(row -> row.get("rank")).containsExactly(1, 2);
        assertThat(response.rows()).extracting(row -> row.get("productLabel")).containsExactly("product-B", "product-A");
    }
}
