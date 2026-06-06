package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuoteCreateIdempotencyTest {
    @Test
    void sameIdempotencyKeyReplaysSameQuoteAndConflictOnDifferentInput() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote first = service.createQuote(QuoteTestSupport.request("idem-replay"));

        Quote replay = service.createQuote(QuoteTestSupport.request("idem-replay"));

        assertThat(replay.quoteId()).isEqualTo(first.quoteId());
        assertThat(service.outboxEvents()).extracting(OutboxEvent::eventType)
            .containsExactly("quote.created.v1", "quote.ready.v1", "best_execution.ranked.v1");
        QuoteCreateRequest differentScenarioVersion = new QuoteCreateRequest(
            QuoteTestSupport.TENANT,
            QuoteTestSupport.SCENARIO,
            8,
            QuoteTestSupport.request("idem-replay").requestedLockPeriods(),
            QuoteTestSupport.request("idem-replay").filters(),
            "USD",
            java.util.Map.of(),
            "actor-1",
            "idem-replay",
            "corr-1",
            QuoteTestSupport.request("idem-replay").effectiveDate()
        );
        assertThatThrownBy(() -> service.createQuote(differentScenarioVersion))
            .isInstanceOf(QuoteCreateException.class)
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("DUPLICATE_IDEMPOTENCY_CONFLICT"));
    }
}
