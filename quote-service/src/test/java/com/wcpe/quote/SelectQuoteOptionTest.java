package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SelectQuoteOptionTest {
    @Test
    void selectsFreshEligibleQuoteOptionAndPublishesLineageWithoutLockConfirmation() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("selection-create"));

        QuoteSelection selection = service.selectQuoteOption(command(quote, quote.options().get(0), policy(true, false), "select-1", ""));

        assertThat(selection.status()).isEqualTo(QuoteSelectionStatus.SELECTED);
        assertThat(selection.lineageRefs()).containsEntry("scenarioVersion", Integer.toString(quote.scenarioVersion()));
        assertThat(selection.lineageRefs()).containsKey("snapshotRef");
        assertThat(selection.lineageRefs()).containsEntry("quoteReplayHash", quote.replayHash());
        assertThat(selection.lockEligibilityRef()).startsWith("lock-eligibility:pending:");
        QuoteSelectionResponse response = QuoteSelectionResponse.from(selection);
        assertThat(response.optionId()).isEqualTo(quote.options().get(0).optionId());
        assertThat(response.scenarioVersion()).isEqualTo(quote.scenarioVersion());
        assertThat(response.snapshotRef()).startsWith("snapshot:");
        assertThat(response.auditIds()).contains(response.auditRef(), quote.auditRef(), quote.replayHash());
        assertThat(service.outboxEvents()).extracting(OutboxEvent::eventType).contains("quote_option.selected.v1");
        OutboxEvent selectedEvent = service.outboxEvents().stream()
            .filter(event -> event.eventType().equals("quote_option.selected.v1"))
            .findFirst()
            .orElseThrow();
        assertThat(selectedEvent.payload()).containsEntry("status", "SELECTED");
        assertThat(selectedEvent.payload()).containsEntry("lockEligibilityRef", selection.lockEligibilityRef());
        assertThat(selectedEvent.payload().get("lineageRefs")).contains("quoteReplayHash");
        assertThat(service.auditEntries()).extracting(AuditEntry::action)
            .contains("QUOTE_OPTION_SELECTION_ATTEMPTED", "QUOTE_OPTION_SELECTED");
    }

    @Test
    void rejectsExpiredQuoteAndReplaysIdempotentSelection() {
        InMemoryQuoteRepository repository = new InMemoryQuoteRepository();
        QuoteApplicationService service = new QuoteApplicationService(repository, QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache(), new BestExecutionRanker(), QuoteTestSupport.CLOCK);
        Quote quote = service.createQuote(QuoteTestSupport.request("selection-expired"));
        QuoteApplicationService expiredService = new QuoteApplicationService(
            repository,
            new QuoteTestSupport.FixtureDependencies(true, List.of(QuoteTestSupport.candidate("A"))),
            new InMemoryQuoteCache(),
            new BestExecutionRanker(),
            java.time.Clock.fixed(quote.expiresAt().plusSeconds(1), java.time.ZoneOffset.UTC)
        );
        QuoteSelection rejected = expiredService.selectQuoteOption(command(quote, quote.options().get(0), policy(true, false), "expired-select", ""));

        assertThat(rejected.status()).isEqualTo(QuoteSelectionStatus.REJECTED);
        assertThat(rejected.rejectedReason()).isEqualTo("QUOTE_NOT_READY_OR_EXPIRED");
        assertThat(expiredService.outboxEvents()).extracting(OutboxEvent::eventType).doesNotContain("quote_option.selected.v1");
        assertThat(expiredService.selectQuoteOption(command(quote, quote.options().get(0), policy(true, false), "expired-select", "")))
            .isEqualTo(rejected);
    }

    @Test
    void nonTopRankSelectionIsConfigurableAndAudited() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("selection-non-top"));
        QuoteOption nonTop = quote.options().stream().filter(option -> option.rank() != 1).findFirst().orElseThrow();

        QuoteSelection rejected = service.selectQuoteOption(command(quote, nonTop, policy(false, false), "non-top-reject", ""));
        QuoteSelection selected = service.selectQuoteOption(command(quote, nonTop, policy(true, false), "non-top-select", "borrower preference acknowledged by configured policy"));

        assertThat(rejected.status()).isEqualTo(QuoteSelectionStatus.REJECTED);
        assertThat(rejected.rejectedReason()).isEqualTo("NON_TOP_RANK_NOT_ALLOWED");
        assertThat(selected.status()).isEqualTo(QuoteSelectionStatus.SELECTED);
        assertThat(service.auditEntries()).extracting(AuditEntry::action).contains("QUOTE_OPTION_SELECTION_REJECTED", "QUOTE_OPTION_SELECTED");
    }

    @Test
    void duplicateIdempotencyWithDifferentOptionConflicts() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("selection-idem"));

        service.selectQuoteOption(command(quote, quote.options().get(0), policy(true, false), "same-key", ""));

        assertThatThrownBy(() -> service.selectQuoteOption(command(quote, quote.options().get(1), policy(true, true), "same-key", "configured alternate")))
            .isInstanceOf(QuoteCreateException.class)
            .satisfies(ex -> assertThat(((QuoteCreateException) ex).code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    private static SelectQuoteOptionCommand command(Quote quote, QuoteOption option, QuoteSelectionPolicy policy, String idempotencyKey, String nonTopRankReason) {
        return new SelectQuoteOptionCommand(
            quote.tenantId(),
            quote.quoteId(),
            option.optionId(),
            "LOCK_ELIGIBILITY_HANDOFF",
            List.of("selection-risk-ack"),
            nonTopRankReason,
            policy,
            "actor-1",
            idempotencyKey,
            "corr-selection",
            Map.of("source", "unit-test")
        );
    }

    private static QuoteSelectionPolicy policy(boolean allowNonTopRank, boolean allowReselection) {
        return new QuoteSelectionPolicy("selection-policy-fixture", "select-v1", true, allowNonTopRank, allowReselection, List.of("selection-risk-ack"));
    }
}
