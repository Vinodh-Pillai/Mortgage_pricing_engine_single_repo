package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BestExecutionTieBreakersAreConfigDrivenTest {
    private final BestExecutionRanker ranker = new BestExecutionRanker();

    @Test
    void usesConfiguredTieBreakerDirectionAndField() {
        RankingPolicy policy = policy(List.of(new TieBreaker("lowest-note-rate", "noteRatePercent", "ASC", 1, Map.of())));

        List<QuoteOption> ranked = ranker.rank(
            List.of(candidate("B", "6.75000"), candidate("A", "6.12500")),
            policy,
            Instant.parse("2026-06-04T16:00:00Z")
        );

        assertThat(ranked).extracting(QuoteOption::productId).containsExactly("product-A", "product-B");
        assertThat(ranked.get(0).tieBreakerTrace()).contains("lowest-note-rate:ASC:noteRatePercent");
        assertThat(ranked.get(0).warnings()).isEmpty();
    }

    private static RankingPolicy policy(List<TieBreaker> tieBreakers) {
        return new RankingPolicy(
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
                tieBreakers,
                Map.of("source", "unit-test")
            )
        );
    }

    private static QuoteCandidate candidate(String id, String noteRate) {
        return new QuoteCandidate(
            id,
            "eligibility-" + id,
            "product-" + id,
            "investor-" + id,
            "retail",
            30,
            new BigDecimal(noteRate),
            new BigDecimal("10000.0000"),
            new BigDecimal("25.2500"),
            new BigDecimal("10.0000"),
            Map.of("eligibilityRef", "eligibility-" + id, "priceRef", "price-" + id)
        );
    }
}
