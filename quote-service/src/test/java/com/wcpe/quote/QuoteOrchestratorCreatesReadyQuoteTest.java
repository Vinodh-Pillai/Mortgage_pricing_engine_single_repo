package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteOrchestratorCreatesReadyQuoteTest {
    @Test
    void createsReadyQuoteWithConfiguredRankingOutboxAuditAndWaterfall() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());

        Quote quote = service.createQuote(QuoteTestSupport.request("idem-ready"));

        assertThat(quote.status()).isEqualTo(QuoteStatus.READY);
        assertThat(quote.options()).hasSize(2);
        assertThat(quote.options().get(0).rank()).isEqualTo(1);
        assertThat(quote.options().get(0).productId()).isEqualTo("product-B");
        assertThat(quote.options().get(0).finalPriceBps()).isEqualByComparingTo("10035.2500");
        assertThat(quote.inputVersionSet().rankingPolicyVersion()).isEqualTo("rank-v1");
        assertThat(quote.replayHash()).hasSize(64);
        assertThat(service.outboxEvents()).extracting(OutboxEvent::eventType)
            .containsExactly("quote.created.v1", "quote.ready.v1", "best_execution.ranked.v1");
        assertThat(service.auditEntries()).extracting(AuditEntry::action)
            .containsExactly(
                "QUOTE_CREATE_REQUESTED",
                "QUOTE_ORCHESTRATION_COMPLETED",
                "BEST_EXECUTION_POLICY_APPLIED",
                "BEST_EXECUTION_TIE_BREAKER_APPLIED"
            );
    }
}
