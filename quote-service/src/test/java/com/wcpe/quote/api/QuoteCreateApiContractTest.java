package com.wcpe.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.quote.InMemoryQuoteCache;
import com.wcpe.quote.QuoteApplicationService;
import com.wcpe.quote.QuoteTestSupport;
import org.junit.jupiter.api.Test;

class QuoteCreateApiContractTest {
    @Test
    void postTenantQuoteReturnsStoryContractFields() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        QuoteController controller = new QuoteController(service);

        QuoteCreateResponse response = controller.postTenantQuote(
            QuoteTestSupport.TENANT,
            "idem-api",
            "corr-api",
            QuoteTestSupport.request("ignored")
        );

        assertThat(response.quoteId()).isNotNull();
        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.inputVersionSet()).containsEntry("rankingPolicyVersion", "rank-v1");
        assertThat(response.options()).hasSize(2);
        assertThat(response.auditRef()).isEqualTo("audit:corr-api");
        assertThat(response.replayHash()).hasSize(64);
        assertThat(response.correlationId()).isEqualTo("corr-api");
    }
}
