package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RestrictedMarginFieldsAreMaskedTest {
    @Test
    void restrictedFieldsAreAbsentFromRowsAndReportedAsHidden() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-restricted-fields"));

        QuoteComparisonResponse response = service.compareQuoteOptions(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            QuoteTestSupport.comparisonView(),
            Set.of(),
            "actor-1",
            "corr-restricted"
        );

        assertThat(response.columns()).extracting(ComparisonColumn::field).doesNotContain("margin", "investorLabel");
        assertThat(response.hiddenFields()).extracting(ComparisonHiddenField::field).containsExactly("investorLabel", "margin");
        assertThat(response.rows()).allSatisfy(row -> assertThat(row).doesNotContainKeys("margin", "investorLabel"));
        assertThat(service.auditEntries()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("QUOTE_COMPARISON_VIEWED");
            assertThat(entry.summary()).containsEntry("maskedFieldCount", "2");
        });
    }
}
