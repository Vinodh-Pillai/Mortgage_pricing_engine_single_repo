package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.quote.parallel.ParallelPricingOrchestrator;
import com.wcpe.quote.parallel.ParallelPricingSettings;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ParallelPricingBenchmark {
    @Test
    void twentyCandidatesUnder500ms() {
        ParallelPricingOrchestrator orchestrator = new ParallelPricingOrchestrator(
            new ParallelPricingSettings(Duration.ofSeconds(2), 32, Duration.ofSeconds(1), 50.0f, 100, 10, Duration.ofSeconds(30), 1)
        );
        QuoteApplicationService service = new QuoteApplicationService(
            new InMemoryQuoteRepository(),
            new InMemoryQuoteJobRepository(),
            new InMemoryQuoteSnapshotRepository(),
            new BenchmarkDependencies(20, 50),
            new InMemoryQuoteCache(),
            new BestExecutionRanker(),
            QuoteTestSupport.CLOCK,
            orchestrator
        );

        List<Long> elapsedMillis = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long started = System.nanoTime();
            Quote quote = service.createQuote(QuoteTestSupport.request("parallel-benchmark-" + i));
            elapsedMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            assertThat(quote.status()).isEqualTo(QuoteStatus.READY);
            assertThat(quote.options()).hasSize(20);
        }

        elapsedMillis.sort(Comparator.naturalOrder());
        long p99Approximation = elapsedMillis.get(elapsedMillis.size() - 1);
        assertThat(p99Approximation).isLessThan(500L);
        orchestrator.close();
    }

    private record BenchmarkDependencies(int count, long delayMillis) implements QuoteDependencies {
        @Override
        public Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request) {
            return QuoteTestSupport.dependenciesWithPolicy().rankingPolicyFor(request);
        }

        @Override
        public List<QuoteCandidate> candidatesFor(QuoteCreateRequest request) {
            List<QuoteCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                BigDecimal base = new BigDecimal("10000.0000").subtract(BigDecimal.valueOf(i));
                candidates.add(new QuoteCandidate(
                    "bench-" + i,
                    "eligibility-bench-" + i,
                    "product-bench-" + i,
                    "investor-bench-" + i,
                    "retail",
                    30,
                    new BigDecimal("6.12500"),
                    base,
                    new BigDecimal("25.2500"),
                    new BigDecimal("10.0000"),
                    Map.of("eligibilityRef", "eligibility-bench-" + i, "priceRef", "price-bench-" + i)
                ));
            }
            return candidates;
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
            return request -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(delayMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return new AdjustmentCalculationResult(
                    request.basePriceDecision().scenarioId(),
                    request.basePriceDecision().basePriceId(),
                    List.of(),
                    0.0d,
                    "rulebook-v1",
                    "benchmark-rule-book",
                    List.of("audit:benchmark"),
                    "benchmark-hash-" + request.basePriceDecision().basePriceId(),
                    Map.of("POINTS_DELTA", BigDecimal.ZERO),
                    false
                );
            };
        }

        @Override
        public String marginVersion() {
            return "margin-v1";
        }
    }
}
