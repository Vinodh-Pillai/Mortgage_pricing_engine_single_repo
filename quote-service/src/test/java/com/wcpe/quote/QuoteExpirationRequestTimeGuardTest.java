package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class QuoteExpirationRequestTimeGuardTest {
    @Test
    void getQuoteMarksExpiredWhenCapturedExpiresAtHasPassed() {
        InMemoryQuoteRepository repository = new InMemoryQuoteRepository();
        InMemoryQuoteJobRepository jobRepository = new InMemoryQuoteJobRepository();
        InMemoryQuoteSnapshotRepository snapshotRepository = new InMemoryQuoteSnapshotRepository();
        InMemoryQuoteCache cache = new InMemoryQuoteCache();
        QuoteApplicationService createService = new QuoteApplicationService(
            repository,
            jobRepository,
            snapshotRepository,
            QuoteTestSupport.dependenciesWithPolicy(),
            cache,
            new BestExecutionRanker(),
            QuoteTestSupport.CLOCK
        );
        Quote quote = createService.createQuote(QuoteTestSupport.request("idem-expire"));
        QuoteApplicationService readService = new QuoteApplicationService(
            repository,
            jobRepository,
            snapshotRepository,
            QuoteTestSupport.dependenciesWithPolicy(),
            cache,
            new BestExecutionRanker(),
            Clock.fixed(quote.expiresAt().plus(Duration.ofSeconds(1)), ZoneOffset.UTC)
        );

        Quote expired = readService.getQuote(QuoteTestSupport.TENANT, quote.quoteId());

        assertThat(expired.status()).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(expired.expiresAt()).isEqualTo(quote.expiresAt());
        assertThat(expired.version()).isEqualTo(quote.version() + 1);
        assertThat(readService.outboxEvents()).extracting(OutboxEvent::eventType)
            .containsExactly("quote.expired.v1");
        assertThat(readService.auditEntries()).extracting(AuditEntry::action)
            .containsExactly("QUOTE_EXPIRED");
        assertThat(readService.getQuote(QuoteTestSupport.TENANT, quote.quoteId()).status()).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(readService.outboxEvents()).hasSize(1);
    }

    @Test
    void getQuoteKeepsReadyQuoteBeforeExpiration() {
        InMemoryQuoteCache cache = new InMemoryQuoteCache();
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), cache);
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-not-expired"));

        Quote current = service.getQuote(QuoteTestSupport.TENANT, quote.quoteId());

        assertThat(current.status()).isEqualTo(QuoteStatus.READY);
        assertThat(service.outboxEvents()).extracting(OutboxEvent::eventType)
            .containsExactly("quote.created.v1", "quote.ready.v1", "best_execution.ranked.v1", "quote.snapshot_created.v1");
    }
}
