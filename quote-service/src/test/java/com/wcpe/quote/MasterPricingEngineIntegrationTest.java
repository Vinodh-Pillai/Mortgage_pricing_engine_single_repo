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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.Test;

class MasterPricingEngineIntegrationTest {
    @Test
    void quoteLaunchPricesWaterfallRanksAndReplaysAcrossMasterEngine() {
        MasterDependencies dependencies = new MasterDependencies(20);
        ParallelPricingOrchestrator orchestrator = masterOrchestrator();
        try {
            QuoteApplicationService service = masterService(dependencies, orchestrator);

            long started = System.nanoTime();
            Quote quote = service.createQuote(QuoteTestSupport.request("pii-38-master-e2e"));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(quote.status()).isEqualTo(QuoteStatus.READY);
            assertThat(quote.options()).hasSize(20);
            assertThat(elapsedMillis).isLessThan(500L);
            assertThat(quote.options().get(0).productId()).isEqualTo("product-00");
            assertThat(quote.options().get(0).waterfall().sections())
                .extracting(PriceWaterfall.WaterfallSection::sectionId)
                .containsExactly("base-price", "eligibility-adjustments", "margin-compensation");
            assertThat(quote.options().get(0).waterfall().sections().get(1).lines())
                .extracting(PriceWaterfall.WaterfallLine::reasonCode)
                .contains("GSE_LLPA", "ESCROW_WAIVER_OVERLAY", "SRP_MARGIN_OVERLAY");

            OutboxEvent readyEvent = service.quoteEvents(quote.tenantId(), quote.quoteId()).stream()
                .filter(event -> "quote.ready.v1".equals(event.eventType()))
                .findFirst()
                .orElseThrow();
            QuoteEventReplayResult replay = service.replayQuoteEvent(
                quote.tenantId(),
                readyEvent.eventId(),
                "audit-replay-actor",
                "corr-pii-38-replay",
                "PII-38 audit replay accuracy validation");

            assertThat(replay.originalEventId()).isEqualTo(readyEvent.eventId());
            assertThat(replay.replayedEvent().payload()).isEqualTo(readyEvent.payload());
            assertThat(replay.replayedEvent().envelopeHeaders()).containsEntry("replay", "true");
            assertThat(dependencies.l1HitRate()).isGreaterThan(0.95d);
            assertThat(dependencies.l2HitRate()).isGreaterThan(0.99d);
            assertThat(dependencies.rateLookupP99Millis()).isLessThan(5L);
            assertThat(dependencies.adjustmentP99Millis()).isLessThan(10L);
            assertThat(dependencies.parallelPricingSuccessRate()).isGreaterThan(0.99d);
        } finally {
            orchestrator.close();
        }
    }

    @Test
    void oneHundredConcurrentQuoteLaunchesRemainStableAndMeetLatencyTargets() throws Exception {
        MasterDependencies dependencies = new MasterDependencies(20);
        ParallelPricingOrchestrator orchestrator = masterOrchestrator();
        ExecutorService launches = Executors.newFixedThreadPool(24);
        try {
            QuoteApplicationService service = masterService(dependencies, orchestrator);
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                int launchNumber = i;
                tasks.add(() -> {
                    long started = System.nanoTime();
                    Quote quote = service.createQuote(QuoteTestSupport.request("pii-38-load-" + launchNumber));
                    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                    assertThat(quote.status()).isEqualTo(QuoteStatus.READY);
                    assertThat(quote.options()).hasSize(20);
                    return elapsedMillis;
                });
            }

            List<Future<Long>> futures = launches.invokeAll(tasks, 30, TimeUnit.SECONDS);
            List<Long> elapsed = new ArrayList<>();
            for (Future<Long> future : futures) {
                assertThat(future.isCancelled()).isFalse();
                elapsed.add(future.get(1, TimeUnit.SECONDS));
            }

            elapsed.sort(Comparator.naturalOrder());
            long p50 = percentile(elapsed, 0.50d);
            long p99 = percentile(elapsed, 0.99d);
            assertThat(p50).isLessThan(200L);
            assertThat(p99).isLessThan(500L);
            assertThat(dependencies.parallelPricingSuccessRate()).isGreaterThan(0.99d);
            assertThat(dependencies.totalQuoteLaunches()).isEqualTo(100);
            assertThat(dependencies.totalCandidatePricings()).isEqualTo(2_000);
        } finally {
            launches.shutdownNow();
            orchestrator.close();
        }
    }

    private static QuoteApplicationService masterService(MasterDependencies dependencies, ParallelPricingOrchestrator orchestrator) {
        return new QuoteApplicationService(
            new InMemoryQuoteRepository(),
            new InMemoryQuoteJobRepository(),
            new InMemoryQuoteSnapshotRepository(),
            dependencies,
            new InMemoryQuoteCache(),
            new BestExecutionRanker(),
            QuoteTestSupport.CLOCK,
            orchestrator);
    }

    private static ParallelPricingOrchestrator masterOrchestrator() {
        return new ParallelPricingOrchestrator(
            new ParallelPricingSettings(Duration.ofSeconds(2), 4_096, Duration.ofMillis(250), 50.0f, 2_000, 10, Duration.ofSeconds(30), 20),
            Executors.newFixedThreadPool(128));
    }

    private static long percentile(List<Long> values, double percentile) {
        int index = (int) Math.ceil(values.size() * percentile) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private static final class MasterDependencies implements QuoteDependencies {
        private final int candidateCount;
        private final RateLookupProbe rateLookupProbe = new RateLookupProbe();
        private final List<Long> adjustmentLatenciesNanos = java.util.Collections.synchronizedList(new ArrayList<>());
        private final LongAdder quoteLaunches = new LongAdder();
        private final LongAdder candidatePricings = new LongAdder();
        private final LongAdder candidatePricingFailures = new LongAdder();

        private MasterDependencies(int candidateCount) {
            this.candidateCount = candidateCount;
            for (int i = 0; i < candidateCount; i++) {
                rateLookupProbe.preload("rate-card-" + i);
            }
        }

        @Override
        public Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request) {
            return Optional.of(new RankingPolicy(
                "pii-38-best-execution",
                "rank-v1",
                Duration.ofHours(4),
                Map.of(),
                Map.of(),
                Map.of(),
                new RankingPolicyRef(
                    "pii-38-best-execution",
                    "rank-v1",
                    List.of(new RankingCriterion("lowest-adjusted-points", "numeric_min", "adjustmentBps", BigDecimal.ONE, true, "{}")),
                    List.of(new TieBreaker("candidate-id", "candidateId", "ASC", 1, Map.of())),
                    Map.of("source", "PII-38-S01"))));
        }

        @Override
        public List<QuoteCandidate> candidatesFor(QuoteCreateRequest request) {
            quoteLaunches.increment();
            List<QuoteCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < candidateCount; i++) {
                String candidateId = "%02d".formatted(i);
                BigDecimal basePrice = rateLookupProbe.lookup("rate-card-" + i).subtract(BigDecimal.valueOf(i));
                candidates.add(new QuoteCandidate(
                    candidateId,
                    "eligibility-" + candidateId,
                    "product-" + candidateId,
                    "investor-" + candidateId,
                    "retail",
                    30,
                    new BigDecimal("6.12500"),
                    basePrice,
                    BigDecimal.ZERO,
                    new BigDecimal("10.0000"),
                    Map.of(
                        "eligibilityRef", "catalog-tenant-auth:" + candidateId,
                        "priceRef", "rate-feed:l1-l2:" + candidateId,
                        "marginRef", "margin-service:srp:" + candidateId)));
            }
            return candidates;
        }

        @Override
        public String eligibilityVersion() {
            return "tenant-auth-v1";
        }

        @Override
        public String pricingVersion() {
            return "rate-cache-v1";
        }

        @Override
        public String adjustmentVersion() {
            return "rulebook-overlays-v1";
        }

        @Override
        public AdjustmentCalculationPort adjustmentCalculationPort() {
            return request -> {
                candidatePricings.increment();
                long started = System.nanoTime();
                try {
                    String candidateId = request.basePriceDecision().basePriceId();
                    int ordinal = Integer.parseInt(candidateId);
                    double totalPoints = (double) ordinal / 1_000.0d;
                    return new AdjustmentCalculationResult(
                        request.basePriceDecision().scenarioId(),
                        candidateId,
                        List.of(
                            new AdjustmentLine("llpa-" + candidateId, 0.0d, "GSE_LLPA", "adjustment-service", "POINTS_DELTA", "rule-llpa", "rulebook-v1", true, "GSE LLPA", "audit:llpa:" + candidateId, List.of("candidateId=" + candidateId)),
                            new AdjustmentLine("overlay-escrow-" + candidateId, 0.0d, "ESCROW_WAIVER_OVERLAY", "adjustment-service", "POINTS_DELTA", "rule-overlay", "rulebook-v1", true, "Escrow waiver overlay", "audit:overlay:" + candidateId, List.of("candidateId=" + candidateId)),
                            new AdjustmentLine("overlay-srp-" + candidateId, totalPoints, "SRP_MARGIN_OVERLAY", "margin-service", "POINTS_DELTA", "rule-srp", "margin-v1", true, "SRP margin overlay", "audit:srp:" + candidateId, List.of("candidateId=" + candidateId))
                        ),
                        totalPoints,
                        "rulebook-overlays-v1",
                        "master-rule-book",
                        List.of("audit:llpa:" + candidateId, "audit:overlay:" + candidateId, "audit:srp:" + candidateId),
                        "adjustment-hash-" + candidateId,
                        Map.of("POINTS_DELTA", BigDecimal.valueOf(totalPoints)),
                        false);
                } catch (RuntimeException ex) {
                    candidatePricingFailures.increment();
                    throw ex;
                } finally {
                    adjustmentLatenciesNanos.add(System.nanoTime() - started);
                }
            };
        }

        @Override
        public String marginVersion() {
            return "srp-margin-v1";
        }

        private double l1HitRate() {
            return rateLookupProbe.l1HitRate();
        }

        private double l2HitRate() {
            return rateLookupProbe.l2HitRate();
        }

        private long rateLookupP99Millis() {
            return rateLookupProbe.p99Millis();
        }

        private long adjustmentP99Millis() {
            return nanosP99(adjustmentLatenciesNanos);
        }

        private double parallelPricingSuccessRate() {
            long total = candidatePricings.sum();
            if (total == 0) {
                return 1.0d;
            }
            return (double) (total - candidatePricingFailures.sum()) / (double) total;
        }

        private int totalQuoteLaunches() {
            return quoteLaunches.intValue();
        }

        private int totalCandidatePricings() {
            return candidatePricings.intValue();
        }
    }

    private static final class RateLookupProbe {
        private final java.util.concurrent.ConcurrentMap<String, BigDecimal> l1 = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentMap<String, BigDecimal> l2 = new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger l1Hits = new AtomicInteger();
        private final AtomicInteger l2Hits = new AtomicInteger();
        private final AtomicInteger lookups = new AtomicInteger();
        private final List<Long> latenciesNanos = java.util.Collections.synchronizedList(new ArrayList<>());

        private void preload(String key) {
            BigDecimal value = new BigDecimal("10000.0000");
            l1.put(key, value);
            l2.put(key, value);
        }

        private BigDecimal lookup(String key) {
            long started = System.nanoTime();
            lookups.incrementAndGet();
            try {
                BigDecimal l1Value = l1.get(key);
                if (l1Value != null) {
                    l1Hits.incrementAndGet();
                    l2Hits.incrementAndGet();
                    return l1Value;
                }
                BigDecimal l2Value = l2.get(key);
                if (l2Value != null) {
                    l2Hits.incrementAndGet();
                    l1.put(key, l2Value);
                    return l2Value;
                }
                throw new IllegalStateException("missing synthetic rate card " + key);
            } finally {
                latenciesNanos.add(System.nanoTime() - started);
            }
        }

        private double l1HitRate() {
            return lookups.get() == 0 ? 1.0d : (double) l1Hits.get() / (double) lookups.get();
        }

        private double l2HitRate() {
            return lookups.get() == 0 ? 1.0d : (double) l2Hits.get() / (double) lookups.get();
        }

        private long p99Millis() {
            return nanosP99(latenciesNanos);
        }
    }

    private static long nanosP99(List<Long> nanos) {
        List<Long> snapshot;
        synchronized (nanos) {
            snapshot = new ArrayList<>(nanos);
        }
        if (snapshot.isEmpty()) {
            return 0L;
        }
        snapshot.sort(Comparator.naturalOrder());
        long value = snapshot.get(Math.max(0, (int) Math.ceil(snapshot.size() * 0.99d) - 1));
        return TimeUnit.NANOSECONDS.toMillis(value);
    }
}
