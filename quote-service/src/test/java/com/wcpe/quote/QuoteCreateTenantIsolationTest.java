package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuoteCreateTenantIsolationTest {
    @Test
    void lookupRequiresMatchingTenant() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-tenant"));

        assertThatThrownBy(() -> service.getQuote(QuoteTestSupport.OTHER_TENANT, quote.quoteId()))
            .isInstanceOf(QuoteCreateException.class)
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("NOT_FOUND"));

        assertThatThrownBy(() -> service.getQuote(QuoteTestSupport.TENANT, UUID.randomUUID()))
            .isInstanceOf(QuoteCreateException.class)
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("NOT_FOUND"));
    }
}
