package com.wcpe.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.quote.parallel.FailedCandidate;
import com.wcpe.quote.parallel.ParallelPricingOrchestrator;
import com.wcpe.quote.parallel.ParallelPricingSettings;
import com.wcpe.quote.parallel.PricingBatchResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ParallelPricingTest {
    @Test
    void allCandidatesPricedInParallel() {
        ParallelPricingOrchestrator orchestrator = new ParallelPricingOrchestrator(testSettings(Duration.ofSeconds(2), 8, 1));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();

        PricingBatchResult result = orchestrator.priceCandidatesParallel(candidates(8), candidate -> {
            int nowActive = active.incrementAndGet();
            maxActive.accumulateAndGet(nowActive, Math::max);
            sleepMillis(75);
            active.decrementAndGet();
            return candidate;
        });

        assertThat(result.successful()).hasSize(8);
        assertThat(result.failed()).isEmpty();
        assertThat(maxActive.get()).isGreaterThan(1);
        orchestrator.close();
    }

    @Test
    void timeoutHandledGracefully() {
        ParallelPricingOrchestrator orchestrator = new ParallelPricingOrchestrator(testSettings(Duration.ofMillis(60), 4, 1));

        PricingBatchResult result = orchestrator.priceCandidatesParallel(candidates(3), candidate -> {
            if ("C-2".equals(candidate.candidateId())) {
                sleepMillis(250);
            }
            return candidate;
        });

        assertThat(result.successful()).extracting(QuoteCandidate::candidateId).containsExactly("C-1", "C-3");
        assertThat(result.failed()).extracting(FailedCandidate::failureCode).containsExactly("CANDIDATE_PRICING_TIMEOUT");
        assertThat(result.successRate()).isBetween(0.66d, 0.67d);
        orchestrator.close();
    }

    @Test
    void particularFailureDoesNotBlockOthers() {
        ParallelPricingOrchestrator orchestrator = new ParallelPricingOrchestrator(
            new ParallelPricingSettings(Duration.ofSeconds(2), 4, Duration.ZERO, 50.0f, 10, 10, Duration.ofSeconds(30), 1)
        );

        PricingBatchResult result = orchestrator.priceCandidatesParallel(candidates(4), candidate -> {
            if ("C-3".equals(candidate.candidateId())) {
                throw new IllegalStateException("pricing dependency unavailable for C-3");
            }
            return candidate;
        });

        assertThat(result.successful()).extracting(QuoteCandidate::candidateId).containsExactly("C-1", "C-2", "C-4");
        assertThat(result.failed()).extracting(FailedCandidate::failureCode).containsExactly("CANDIDATE_PRICING_FAILED");
        orchestrator.close();
    }

    @Test
    void resultAggregationCorrect() {
        ParallelPricingOrchestrator orchestrator = new ParallelPricingOrchestrator(
            new ParallelPricingSettings(Duration.ofSeconds(2), 4, Duration.ZERO, 50.0f, 10, 10, Duration.ofSeconds(30), 3)
        );

        PricingBatchResult result = orchestrator.priceCandidatesParallel(candidates(5), candidate -> {
            if (candidate.candidateId().endsWith("4") || candidate.candidateId().endsWith("5")) {
                throw new IllegalArgumentException("candidate failed");
            }
            return candidate;
        });

        assertThat(result.totalCandidates()).isEqualTo(5);
        assertThat(result.successful()).hasSize(3);
        assertThat(result.failed()).hasSize(2);
        assertThat(result.hasMinimumResults()).isTrue();
        assertThat(result.totalDuration()).isGreaterThanOrEqualTo(Duration.ZERO);
        orchestrator.close();
    }

    @Test
    void circuitBreakerOpensOnFailures() {
        ParallelPricingOrchestrator orchestrator = new ParallelPricingOrchestrator(testSettings(Duration.ofSeconds(2), 1, 1));

        orchestrator.priceCandidatesParallel(List.of(candidate("C-1")), candidate -> { throw new IllegalStateException("fail-1"); });
        orchestrator.priceCandidatesParallel(List.of(candidate("C-2")), candidate -> { throw new IllegalStateException("fail-2"); });
        PricingBatchResult openResult = orchestrator.priceCandidatesParallel(List.of(candidate("C-3")), candidate -> candidate);

        assertThat(orchestrator.circuitBreakerState()).isEqualTo("OPEN");
        assertThat(openResult.successful()).isEmpty();
        assertThat(openResult.failed()).hasSize(1);
        orchestrator.close();
    }

    private static ParallelPricingSettings testSettings(Duration timeout, int concurrency, int minimumSuccessfulResults) {
        return new ParallelPricingSettings(timeout, concurrency, Duration.ZERO, 50.0f, 2, 2, Duration.ofSeconds(30), minimumSuccessfulResults);
    }

    private static List<QuoteCandidate> candidates(int count) {
        List<QuoteCandidate> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            result.add(candidate("C-" + i));
        }
        return result;
    }

    private static QuoteCandidate candidate(String id) {
        return new QuoteCandidate(
            id,
            "eligibility-" + id,
            "product-" + id,
            "investor-" + id,
            "retail",
            30,
            java.math.BigDecimal.valueOf(6.12500),
            java.math.BigDecimal.valueOf(10000.0000),
            java.math.BigDecimal.valueOf(25.2500),
            java.math.BigDecimal.valueOf(10.0000),
            Map.of("candidateRef", id, "pricingRef", UUID.nameUUIDFromBytes(id.getBytes()).toString())
        );
    }

    private static void sleepMillis(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
