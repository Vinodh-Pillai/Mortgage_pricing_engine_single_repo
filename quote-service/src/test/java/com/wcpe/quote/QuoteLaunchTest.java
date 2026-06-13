package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class QuoteLaunchTest {
    @Test
    void adjustmentCalculationPerCandidate() {
        CountingAdjustmentPort adjustmentPort = new CountingAdjustmentPort();
        QuoteApplicationService service = QuoteTestSupport.service(new AdjustmentAwareDependencies(adjustmentPort), new InMemoryQuoteCache());

        Quote quote = service.createQuote(QuoteTestSupport.request("idem-adjustment-launch"));

        assertThat(adjustmentPort.candidateIds).containsExactlyInAnyOrder("A", "B");
        assertThat(quote.options()).hasSize(2);
        QuoteOption top = quote.options().get(0);
        assertThat(top.productId()).isEqualTo("product-B");
        assertThat(top.totalAdjustmentBps()).isEqualByComparingTo("-25.0000");
        assertThat(top.upstreamRefs()).containsEntry("adjustmentResultHash", "adjustment-hash-B");
        assertThat(top.waterfall().sections().get(1).lines())
            .extracting(PriceWaterfall.WaterfallLine::reasonCode)
            .containsExactly("REAL_ADJ_B");
    }

    private static final class CountingAdjustmentPort implements AdjustmentCalculationPort {
        private final java.util.List<String> candidateIds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        @Override
        public AdjustmentCalculationResult calculate(AdjustmentCalculationRequest request) {
            String candidateId = request.basePriceDecision().basePriceId();
            candidateIds.add(candidateId);
            double points = "B".equals(candidateId) ? -0.25 : 0.50;
            return new AdjustmentCalculationResult(
                request.basePriceDecision().scenarioId(),
                candidateId,
                List.of(new AdjustmentLine("LLPA-" + candidateId, points, "REAL_ADJ_" + candidateId, "adjustment-service",
                    "POINTS_DELTA", "rule-" + candidateId, "rulebook-v1", true, "Adjustment " + candidateId,
                    "audit:adj-" + candidateId, List.of("candidateId=" + candidateId))),
                points,
                "rulebook-v1",
                "real-rule-book",
                List.of("audit:adj-" + candidateId),
                "adjustment-hash-" + candidateId,
                Map.of("POINTS_DELTA", BigDecimal.valueOf(points)),
                false);
        }
    }

    private record AdjustmentAwareDependencies(AdjustmentCalculationPort adjustmentPort) implements QuoteDependencies {
        @Override
        public Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request) {
            return Optional.of(new RankingPolicy(
                "adjustment-aware-policy",
                "rank-v1",
                Duration.ofHours(4),
                Map.of(),
                Map.of(),
                Map.of(),
                new RankingPolicyRef(
                    "adjustment-aware-policy",
                    "rank-v1",
                    List.of(new RankingCriterion("lowest-adjustment", "numeric_min", "adjustmentBps", BigDecimal.ONE, true, "{}")),
                    List.of(),
                    Map.of("source", "unit-test"))));
        }

        @Override
        public List<QuoteCandidate> candidatesFor(QuoteCreateRequest request) {
            return List.of(QuoteTestSupport.candidate("A"), QuoteTestSupport.candidate("B"));
        }

        @Override
        public String eligibilityVersion() {
            return "eligibility-v1";
        }

        @Override
        public String pricingVersion() {
            return "pricing-v1";
        }

        @Override
        public String adjustmentVersion() {
            return "rulebook-v1";
        }

        @Override
        public AdjustmentCalculationPort adjustmentCalculationPort() {
            return adjustmentPort;
        }

        @Override
        public String marginVersion() {
            return "margin-v1";
        }
    }
}
