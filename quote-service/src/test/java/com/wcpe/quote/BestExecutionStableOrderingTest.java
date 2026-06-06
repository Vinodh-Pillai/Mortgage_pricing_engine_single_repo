package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BestExecutionStableOrderingTest {
    @Test
    void fallsBackToNeutralCandidateIdOrderingWhenConfiguredTieBreakersDoNotResolveTie() {
        RankingPolicy policy = new RankingPolicy(
            "ranking-policy-fixture",
            "rank-v1",
            Duration.ofHours(4),
            Map.of(),
            Map.of(),
            Map.of(),
            new RankingPolicyRef(
                "ranking-policy-fixture",
                "rank-v1",
                List.of(new RankingCriterion("configured-score", "numeric_min", "basePriceBps", BigDecimal.ONE, true, "{}")),
                List.of(),
                Map.of("source", "unit-test")
            )
        );

        List<QuoteOption> ranked = new BestExecutionRanker().rank(
            List.of(QuoteTestSupport.candidate("B"), QuoteTestSupport.candidate("A")),
            policy,
            Instant.parse("2026-06-04T16:00:00Z")
        );

        assertThat(ranked).extracting(QuoteOption::productId).containsExactly("product-A", "product-B");
        assertThat(ranked).allSatisfy(option -> assertThat(option.warnings()).contains("UNRESOLVED_TIE"));
    }
}
