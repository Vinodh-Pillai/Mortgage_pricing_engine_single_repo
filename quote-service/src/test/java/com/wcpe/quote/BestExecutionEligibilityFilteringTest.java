package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BestExecutionEligibilityFilteringTest {
    @Test
    void excludesIneligibleWithReasonBeforeRanking() {
        EligibilityFilter filter = candidate -> candidate.investorId().endsWith("B")
            ? new EligibilityDecision(false, List.of(new EligibilityFailure("FICO", "MIN_FICO", "FICO 680 < min 700 for Cash-Out", 680, 700)), List.of())
            : EligibilityDecision.allowed();

        BestExecutionRankingResult result = new BestExecutionRanker(filter).rankWithEligibility(
            List.of(QuoteTestSupport.candidate("A"), QuoteTestSupport.candidate("B")),
            policy(),
            Instant.parse("2026-06-04T16:00:00Z")
        );

        assertThat(result.rankedOptions()).extracting(QuoteOption::investorId).containsExactly("investor-A");
        assertThat(result.ineligibleCandidates()).containsKey("B");
        assertThat(result.ineligibleCandidates().get("B").failures().get(0).code()).isEqualTo("MIN_FICO");
    }

    @Test
    void returnsEmptyRankingWhenAllInvestorsIneligible() {
        BestExecutionRankingResult result = new BestExecutionRanker(candidate -> new EligibilityDecision(false, List.of(new EligibilityFailure("STATE", "STATE_NOT_ALLOWED", "state not allowed", "TX", List.of("CA"))), List.of()))
            .rankWithEligibility(List.of(QuoteTestSupport.candidate("A")), policy(), Instant.parse("2026-06-04T16:00:00Z"));

        assertThat(result.rankedOptions()).isEmpty();
        assertThat(result.ineligibleCandidates()).containsKey("A");
    }

    private RankingPolicy policy() {
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
                List.of(),
                Map.of("source", "unit-test")
            )
        );
    }
}
