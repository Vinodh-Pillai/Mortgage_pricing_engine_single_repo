package com.wcpe.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.quote.InMemoryQuoteCache;
import com.wcpe.quote.OutboxEvent;
import com.wcpe.quote.Quote;
import com.wcpe.quote.QuoteApplicationService;
import com.wcpe.quote.QuoteExplanationResponse;
import com.wcpe.quote.QuoteTestSupport;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuoteExplanationApiContractTest {
    @Test
    void getExplanationReturnsWaterfallContractAndAuditEvidence() {
        QuoteApplicationService service = QuoteTestSupport.service(QuoteTestSupport.dependenciesWithPolicy(), new InMemoryQuoteCache());
        QuoteController controller = new QuoteController(service);
        Quote quote = service.createQuote(QuoteTestSupport.request("idem-api-explanation"));

        QuoteExplanationResponse response = controller.getTenantQuoteOptionExplanation(
            QuoteTestSupport.TENANT,
            quote.quoteId(),
            quote.options().get(0).optionId(),
            Set.of(),
            "actor-1",
            "corr-api-explanation"
        );

        assertThat(response.quoteId()).isEqualTo(quote.quoteId());
        assertThat(response.optionId()).isEqualTo(quote.options().get(0).optionId());
        assertThat(response.waterfallSections()).hasSize(3);
        assertThat(response.finalPrice()).isEqualByComparingTo(quote.options().get(0).finalPriceBps());
        assertThat(response.roundingTrace().roundedFinalPriceBps()).isEqualByComparingTo(response.finalPrice());
        assertThat(response.upstreamRefs()).containsKeys("eligibilityRef", "priceRef");
        assertThat(response.hiddenFields()).containsExactly("margin");
        assertThat(response.auditRef()).contains("corr-api-explanation");
        assertThat(service.outboxEvents()).extracting(OutboxEvent::eventType).contains("quote.explanation_viewed.v1");
        assertThat(service.auditEntries()).anySatisfy(audit -> {
            assertThat(audit.action()).isEqualTo("QUOTE_WATERFALL_VIEWED");
            assertThat(audit.summary()).containsEntry("maskedFieldCount", "1");
        });
    }
}
