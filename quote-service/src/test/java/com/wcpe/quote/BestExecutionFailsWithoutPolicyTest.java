package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BestExecutionFailsWithoutPolicyTest {
    @Test
    void failsClosedWhenPolicyHasNoConfiguredCriteria() {
        RankingPolicy policy = new RankingPolicy(
            "ranking-policy-fixture",
            "rank-v1",
            Duration.ofHours(4),
            Map.of(),
            Map.of(),
            Map.of(),
            new RankingPolicyRef("ranking-policy-fixture", "rank-v1", List.of(), List.of(), Map.of())
        );

        assertThatThrownBy(() -> new BestExecutionRanker().rank(
            List.of(QuoteTestSupport.candidate("A")),
            policy,
            Instant.parse("2026-06-04T16:00:00Z")
        ))
            .isInstanceOf(QuoteCreateException.class)
            .hasMessageContaining("Ranking policy must provide explicit criteria");
    }
}
