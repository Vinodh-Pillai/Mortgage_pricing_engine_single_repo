package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class QuoteComparisonExportOutboxIT {
    @Test
    void exportIsIdempotentAndRecordsAuditSafeOutboxEvidence() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-export-quote"));

        QuoteComparisonExport first = service.exportComparison(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            QuoteTestSupport.comparisonView(),
            Set.of("margin", "investorLabel"),
            "actor-1",
            "corr-export",
            "idem-export",
            "json"
        );
        QuoteComparisonExport replay = service.exportComparison(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            QuoteTestSupport.comparisonView(),
            Set.of("margin", "investorLabel"),
            "actor-1",
            "corr-export",
            "idem-export",
            "json"
        );

        assertThat(replay).isEqualTo(first);
        assertThat(first.storageRef()).startsWith("audit-safe-export:");
        assertThat(first.redactionProfile()).isEqualTo("audit-safe-redaction-v1");
        assertThat(service.outboxEvents()).anySatisfy(event -> assertThat(event.eventType()).isEqualTo("quote.comparison_exported.v1"));
        assertThat(service.auditEntries()).anySatisfy(entry -> assertThat(entry.action()).isEqualTo("QUOTE_COMPARISON_EXPORTED"));
    }

    @Test
    void exportIdempotencyKeyConflictsOnDifferentInput() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-export-conflict"));
        service.exportComparison(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            QuoteTestSupport.comparisonView(),
            Set.of("margin", "investorLabel"),
            "actor-1",
            "corr-export",
            "idem-export-conflict",
            "json"
        );

        ComparisonViewConfig otherViewVersion = new ComparisonViewConfig(
            "default-view",
            "view-v2",
            QuoteTestSupport.comparisonView().columns(),
            QuoteTestSupport.comparisonView().restrictedFields(),
            null,
            3,
            "audit-safe-redaction-v1"
        );

        assertThatThrownBy(() -> service.exportComparison(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            otherViewVersion,
            Set.of("margin", "investorLabel"),
            "actor-1",
            "corr-export",
            "idem-export-conflict",
            "json"
        )).isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("Comparison export idempotency key was used with different input");
    }
}
