package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.quote.api.QuoteController;
import org.junit.jupiter.api.Test;

class QuoteEventReplayApiTest {
    @Test
    void replayRepublishesOriginalPayloadWithReplayHeadersAndAuditEvidence() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        QuoteController controller = new QuoteController(service);
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-replay-quote-event"));
        OutboxEvent original = controller.getTenantQuoteEvents(quote.tenantId(), quote.quoteId()).stream()
            .filter(event -> event.eventType().equals("quote.created.v1"))
            .findFirst()
            .orElseThrow();

        QuoteEventReplayResult result = controller.postTenantQuoteEventReplay(
            quote.tenantId(),
            original.eventId(),
            "ops-actor",
            "corr-replay",
            "operational replay verification"
        );

        assertThat(result.originalEventId()).isEqualTo(original.eventId());
        assertThat(result.replayedEvent().eventType()).isEqualTo(original.eventType());
        assertThat(result.replayedEvent().payload()).isEqualTo(original.payload());
        assertThat(result.replayedEvent().envelopeHeaders())
            .containsEntry("replay", "true")
            .containsEntry("originalEventId", original.eventId())
            .containsEntry("correlationId", "corr-replay")
            .containsEntry("actorId", "ops-actor")
            .containsKey("deliveryId");
        assertThat(result.auditEntry().action()).isEqualTo("QUOTE_EVENT_REPLAY_REQUESTED");
        assertThat(service.auditEntries()).contains(result.auditEntry());
    }

    @Test
    void replayFailsClosedWithoutReasonForAccess() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-replay-reason-required"));
        OutboxEvent original = service.quoteEvents(quote.tenantId(), quote.quoteId()).get(0);

        assertThatThrownBy(() -> service.replayQuoteEvent(quote.tenantId(), original.eventId(), "ops-actor", "corr-replay", ""))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("reason-for-access");
    }
}
