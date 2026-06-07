package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuoteOutboxIdempotencyTest {
    @Test
    void idempotentQuoteReplayDoesNotCreateDuplicateOutboxEvents() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote first = service.createQuote(QuoteTestSupport.request("idem-event-outbox"));
        int eventCount = service.quoteEvents(first.tenantId(), first.quoteId()).size();

        Quote replay = service.createQuote(QuoteTestSupport.request("idem-event-outbox"));

        assertThat(replay.quoteId()).isEqualTo(first.quoteId());
        assertThat(service.quoteEvents(first.tenantId(), first.quoteId())).hasSize(eventCount);
    }

    @Test
    void eventReadIsTenantScoped() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-event-tenant-scope"));

        assertThatThrownBy(() -> service.quoteEvents(QuoteTestSupport.OTHER_TENANT, quote.quoteId()))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("Quote not found");
    }
}
