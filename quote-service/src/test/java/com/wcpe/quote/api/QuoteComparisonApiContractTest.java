package com.wcpe.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.quote.InMemoryQuoteCache;
import com.wcpe.quote.Quote;
import com.wcpe.quote.QuoteApplicationService;
import com.wcpe.quote.QuoteComparisonExport;
import com.wcpe.quote.QuoteComparisonResponse;
import com.wcpe.quote.QuoteTestSupport;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuoteComparisonApiContractTest {
    @Test
    void getComparisonReturnsStoryContractFieldsAndExportIsIdempotent() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        QuoteController controller = new QuoteController(service);
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-api-comparison"));

        QuoteComparisonResponse comparison = controller.getTenantQuoteComparison(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            QuoteTestSupport.comparisonView(),
            Set.of(),
            "actor-1",
            "corr-api-comparison"
        );
        QuoteComparisonExport export = controller.postTenantQuoteComparisonExport(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            "idem-api-export",
            QuoteTestSupport.comparisonView(),
            Set.of(),
            "actor-1",
            "corr-api-export",
            "json"
        );

        assertThat(comparison.quoteId()).isEqualTo(quote.quoteId());
        assertThat(comparison.status()).isEqualTo("READY");
        assertThat(comparison.viewConfigVersion()).isEqualTo("view-v1");
        assertThat(comparison.hiddenFields()).hasSize(2);
        assertThat(comparison.rows()).hasSize(2);
        assertThat(export.quoteId()).isEqualTo(quote.quoteId());
        assertThat(export.idempotencyKey()).isEqualTo("idem-api-export");
    }
}
