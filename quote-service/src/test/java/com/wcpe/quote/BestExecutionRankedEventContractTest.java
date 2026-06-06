package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BestExecutionRankedEventContractTest {
    @Test
    void quoteCreatePublishesBestExecutionRankedEventAndAuditTrace() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());

        Quote quote = service.createQuote(QuoteTestSupport.request("idem-best-execution-event"));

        assertThat(service.outboxEvents()).extracting(OutboxEvent::eventType)
            .contains("best_execution.ranked.v1");
        OutboxEvent event = service.outboxEvents().stream()
            .filter(candidate -> candidate.eventType().equals("best_execution.ranked.v1"))
            .findFirst()
            .orElseThrow();
        assertThat(event.payload()).containsEntry("policyRef", "ranking-policy-fixture:rank-v1");
        assertThat(event.payload().get("rankedOptionIds")).contains(quote.options().get(0).optionId().toString());
        assertThat(event.payload()).containsEntry("replayHash", quote.replayHash());
        assertThat(service.auditEntries()).extracting(AuditEntry::action)
            .contains("BEST_EXECUTION_POLICY_APPLIED", "BEST_EXECUTION_TIE_BREAKER_APPLIED");
    }
}
