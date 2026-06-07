package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class QuoteEventEnvelopeValidationTest {
    @Test
    void quoteLifecycleEventsExposeRequiredEnvelopeAndAvoidBorrowerPii() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-quote-event-envelope"));

        assertThat(service.quoteEvents(quote.tenantId(), quote.quoteId()))
            .extracting(OutboxEvent::eventType)
            .contains("quote.created.v1", "quote.ready.v1", "best_execution.ranked.v1", "quote.snapshot_created.v1");

        for (OutboxEvent event : service.quoteEvents(quote.tenantId(), quote.quoteId())) {
            assertThat(event.envelopeHeaders()).containsKeys(
                "eventId", "eventType", "eventVersion", "tenantId", "aggregateType", "aggregateId",
                "occurredAt", "correlationId", "causationId", "idempotencyKey", "schemaRef", "producer", "piiClassification"
            );
            assertThat(event.envelopeHeaders()).containsEntry("tenantId", quote.tenantId().toString());
            assertThat(event.envelopeHeaders()).containsEntry("aggregateId", quote.quoteId().toString());
            assertThat(event.envelopeHeaders()).containsEntry("piiClassification", "NO_BORROWER_PII");
            assertThat(event.payload().keySet()).doesNotContain("borrowerName", "ssn", "creditScore", "hiddenMargin");
        }
    }

    @Test
    void catalogDefinesEveryRequiredQuoteEventSchema() {
        assertThat(QuoteEventContractCatalog.requiredEventTypes()).containsAll(Set.of(
            "quote.created.v1", "quote.ready.v1", "quote.no_options.v1", "quote.failed.v1", "quote.expired.v1",
            "quote.stale_detected.v1", "best_execution.ranked.v1", "quote.snapshot_created.v1",
            "quote_option.selected.v1", "quote_replay.completed.v1"
        ));
        assertThat(QuoteEventContractCatalog.schemaFor("quote_replay.completed.v1"))
            .containsEntry("eventVersion", "1")
            .containsKey("envelopeFields");
    }
}
