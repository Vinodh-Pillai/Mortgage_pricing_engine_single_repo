package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BestExecutionTenantIsolationIT {
    @Test
    void persistedRankOutputCannotBeReadAcrossTenants() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-tenant-isolation"));

        assertThatThrownBy(() -> service.getQuote(QuoteTestSupport.OTHER_TENANT, quote.quoteId()))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("Quote not found");
    }
}
