package com.wcpe.quote.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.quote.BestExecutionRanker;
import com.wcpe.quote.InMemoryQuoteCache;
import com.wcpe.quote.InMemoryQuoteJobRepository;
import com.wcpe.quote.InMemoryQuoteRepository;
import com.wcpe.quote.InMemoryQuoteSnapshotRepository;
import com.wcpe.quote.QuoteApplicationService;
import com.wcpe.quote.QuoteTestSupport;
import com.wcpe.quote.RankingCriterion;
import com.wcpe.quote.RankingPolicyRef;
import com.wcpe.quote.RankingPreviewRequest;
import com.wcpe.quote.RankingPreviewResponse;
import com.wcpe.quote.TieBreaker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RankingPreviewApiContractTest {
    @Test
    void previewsRankingWithoutPersistingQuote() {
        QuoteApplicationService service = new QuoteApplicationService(
            new InMemoryQuoteRepository(),
            new InMemoryQuoteJobRepository(),
            new InMemoryQuoteSnapshotRepository(),
            QuoteTestSupport.dependenciesWithPolicy(),
            new InMemoryQuoteCache(),
            new BestExecutionRanker(),
            QuoteTestSupport.CLOCK
        );
        QuoteController controller = new QuoteController(service);

        RankingPreviewResponse response = controller.postTenantRankingPreview(
            QuoteTestSupport.TENANT,
            "corr-preview",
            new RankingPreviewRequest(
                QuoteTestSupport.OTHER_TENANT,
                "actor-1",
                "ignored-correlation",
                policyRef(),
                List.of(QuoteTestSupport.candidate("B"), QuoteTestSupport.candidate("A"))
            )
        );

        assertThat(response.tenantId()).isEqualTo(QuoteTestSupport.TENANT);
        assertThat(response.policyVersion()).isEqualTo("rank-v1");
        assertThat(response.options()).extracting(option -> option.productId()).containsExactly("product-A", "product-B");
        assertThat(response.replayHash()).hasSize(64);
        assertThat(service.outboxEvents()).isEmpty();
        assertThat(service.auditEntries()).isEmpty();
    }

    private static RankingPolicyRef policyRef() {
        return new RankingPolicyRef(
            "ranking-policy-fixture",
            "rank-v1",
            List.of(new RankingCriterion("configured-score", "numeric_min", "basePriceBps", BigDecimal.ONE, true, "{}")),
            List.of(new TieBreaker("lowest-note-rate", "noteRatePercent", "ASC", 1, Map.of())),
            Map.of("source", "unit-test")
        );
    }
}
