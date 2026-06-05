package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class QuotePresentationModelRespectsColumnConfigTest {
    @Test
    void comparisonIncludesOnlyConfiguredVisibleColumnsInConfiguredOrder() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-comparison-columns"));

        QuoteComparisonResponse response = service.compareQuoteOptions(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            QuoteTestSupport.comparisonView(),
            Set.of("margin", "investorLabel"),
            "actor-1",
            "corr-columns"
        );

        assertThat(response.viewConfigVersion()).isEqualTo("view-v1");
        assertThat(response.columns()).extracting(ComparisonColumn::field).containsExactly(
            "rank", "productLabel", "investorLabel", "noteRate", "pricePoints", "lockDays", "totalAdjustments", "margin", "expiration"
        );
        assertThat(response.rows()).hasSize(2);
        assertThat(response.rows().get(0)).containsKeys("rank", "productLabel", "investorLabel", "pricePoints", "margin", "expiration");
        assertThat(response.auditRef()).isEqualTo(quote.auditRef());
    }
}
