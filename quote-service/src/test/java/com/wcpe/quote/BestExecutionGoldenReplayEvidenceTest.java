package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BestExecutionGoldenReplayEvidenceTest {
    private static final Path GOLDEN_DIR = Path.of("golden", "pii08");
    private static final Instant EXPIRES_AT = Instant.parse("2026-06-04T16:00:00Z");

    @Test
    void goldenFixturesAreExercisedByRankingEvidence() throws Exception {
        String multiInvestor = Files.readString(GOLDEN_DIR.resolve("ranking-multi-investor-policy-a.json"));
        String unresolvedTie = Files.readString(GOLDEN_DIR.resolve("ranking-unresolved-tie.json"));
        String versionChange = Files.readString(GOLDEN_DIR.resolve("ranking-policy-version-change.json"));

        assertThat(multiInvestor).contains("multi-investor-policy-a", "policy-golden-a", "BEST_EXECUTION_POLICY_APPLIED");
        assertThat(unresolvedTie).contains("unresolved-tie", "UNRESOLVED_TIE", "BEST_EXECUTION_TIE_BREAKER_APPLIED");
        assertThat(versionChange).contains("policy-version-change", "lowest_base_price", "lowest_note_rate");
    }

    @Test
    void samePolicyVersionReplaysCriterionTraceByteForByte() {
        RankingPolicy policy = priceFocusedPolicy("policy-golden-a", "v1");
        List<QuoteCandidate> candidates = List.of(
            candidate("opt-fnma", "FNMA", "conv-30yr", "10000.0000", "6.12500"),
            candidate("opt-fhlm", "FHLMC", "conv-30yr", "9500.0000", "6.25000"),
            candidate("opt-private", "PRIVATE-A", "conv-15yr", "9000.0000", "5.75000")
        );

        List<QuoteOption> first = new BestExecutionRanker().rank(candidates, policy, EXPIRES_AT);
        List<QuoteOption> replay = new BestExecutionRanker().rank(candidates, policy, EXPIRES_AT);

        assertThat(trace(first)).isEqualTo(trace(replay));
        assertThat(trace(first)).contains("v1", "opt-private", "lowest-base-price", "opt-fhlm", "opt-fnma");
    }

    @Test
    void differentPolicyVersionsCanChangeRankingWithoutHiddenInvestorPreference() {
        List<QuoteCandidate> candidates = List.of(
            candidate("opt-A", "FNMA", "conv-30yr", "10000.0000", "6.12500"),
            candidate("opt-B", "FHLMC", "conv-30yr", "9500.0000", "6.25000")
        );

        List<QuoteOption> priceFocused = new BestExecutionRanker().rank(candidates, priceFocusedPolicy("policy-versioned", "v1"), EXPIRES_AT);
        List<QuoteOption> rateFocused = new BestExecutionRanker().rank(candidates, noteRateFocusedPolicy("policy-versioned", "v2"), EXPIRES_AT);

        assertThat(priceFocused.get(0).productId()).isEqualTo("conv-30yr-opt-B");
        assertThat(rateFocused.get(0).productId()).isEqualTo("conv-30yr-opt-A");
        assertThat(trace(priceFocused)).doesNotContain("manual", "preferredInvestor", "compensation", "hiddenPreference");
        assertThat(trace(rateFocused)).doesNotContain("manual", "preferredInvestor", "compensation", "hiddenPreference");
    }

    private static RankingPolicy priceFocusedPolicy(String policyId, String version) {
        return new RankingPolicy(
            policyId,
            version,
            Duration.ofHours(4),
            Map.of(),
            Map.of(),
            Map.of(),
            new RankingPolicyRef(
                policyId,
                version,
                List.of(new RankingCriterion("lowest-base-price", "numeric_min", "basePriceBps", BigDecimal.ONE, true, "{}")),
                List.of(),
                Map.of("source", "golden-fixture")
            )
        );
    }

    private static RankingPolicy noteRateFocusedPolicy(String policyId, String version) {
        return new RankingPolicy(
            policyId,
            version,
            Duration.ofHours(4),
            Map.of(),
            Map.of(),
            Map.of(),
            new RankingPolicyRef(
                policyId,
                version,
                List.of(new RankingCriterion("lowest-note-rate", "numeric_min", "noteRatePercent", BigDecimal.ONE, true, "{}")),
                List.of(),
                Map.of("source", "golden-fixture")
            )
        );
    }

    private static QuoteCandidate candidate(String id, String investorId, String productId, String basePrice, String noteRate) {
        return new QuoteCandidate(
            id,
            "eligibility-" + id,
            productId + "-" + id,
            investorId,
            "retail",
            30,
            new BigDecimal(noteRate),
            new BigDecimal(basePrice),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of("eligibilityRef", "eligibility-" + id, "priceRef", "price-" + id)
        );
    }

    private static String trace(List<QuoteOption> options) {
        return options.stream()
            .map(option -> option.rank()
                + "|" + option.productId()
                + "|" + option.investorId()
                + "|" + option.rankScore().toPlainString()
                + "|" + option.rankReasons()
                + "|" + option.criterionScores()
                + "|" + option.tieBreakerTrace()
                + "|" + option.warnings())
            .toList()
            .toString();
    }
}
