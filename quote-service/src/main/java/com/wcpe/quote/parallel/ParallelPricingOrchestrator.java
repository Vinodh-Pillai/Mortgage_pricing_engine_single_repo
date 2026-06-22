package com.wcpe.quote.parallel;

import com.wcpe.quote.QuoteCandidate;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import jakarta.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class ParallelPricingOrchestrator implements AutoCloseable {
    private final ParallelPricingSettings settings;
    private final ExecutorService pricingExecutor;
    private final CircuitBreaker pricingCircuitBreaker;
    private final Bulkhead pricingBulkhead;

    public ParallelPricingOrchestrator() {
        this(ParallelPricingSettings.defaults());
    }

    public ParallelPricingOrchestrator(ParallelPricingSettings settings) {
        this(settings, newPricingExecutor(settings == null ? ParallelPricingSettings.defaults() : settings));
    }

    public ParallelPricingOrchestrator(ParallelPricingSettings settings, ExecutorService pricingExecutor) {
        this.settings = settings == null ? ParallelPricingSettings.defaults() : settings;
        this.pricingExecutor = Objects.requireNonNull(pricingExecutor, "pricingExecutor is required");
        this.pricingCircuitBreaker = CircuitBreaker.of("quote-service-parallel-pricing", circuitBreakerConfig(this.settings));
        this.pricingBulkhead = Bulkhead.of("quote-service-parallel-pricing", bulkheadConfig(this.settings));
    }

    public PricingBatchResult priceCandidatesParallel(
        List<QuoteCandidate> candidates,
        Function<QuoteCandidate, QuoteCandidate> candidatePricer
    ) {
        Objects.requireNonNull(candidatePricer, "candidatePricer is required");
        List<QuoteCandidate> safeCandidates = List.copyOf(candidates == null ? List.of() : candidates);
        if (safeCandidates.isEmpty()) {
            return new PricingBatchResult(List.of(), List.of(), Duration.ZERO, 0, settings.minimumSuccessfulResults());
        }

        Instant startedAt = Instant.now();
        List<Callable<QuoteCandidate>> tasks = safeCandidates.stream()
            .map(candidate -> resilientTask(candidate, candidatePricer))
            .toList();

        List<QuoteCandidate> successful = new ArrayList<>();
        List<FailedCandidate> failed = new ArrayList<>();
        List<Future<QuoteCandidate>> futures;
        try {
            futures = pricingExecutor.invokeAll(
                tasks,
                settings.timeoutPerCandidate().toMillis(),
                TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return interruptedResult(safeCandidates, startedAt, ex);
        }

        for (int i = 0; i < futures.size(); i++) {
            Future<QuoteCandidate> future = futures.get(i);
            QuoteCandidate candidate = safeCandidates.get(i);
            if (future.isCancelled()) {
                failed.add(FailedCandidate.timeout(candidate));
                continue;
            }
            try {
                QuoteCandidate priced = future.get();
                if (priced == null) {
                    failed.add(new FailedCandidate(candidate, "CANDIDATE_PRICING_FALLBACK", "candidate pricer returned fallback/null result"));
                } else {
                    successful.add(priced);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failed.add(FailedCandidate.failed(candidate, ex));
            } catch (ExecutionException ex) {
                failed.add(FailedCandidate.failed(candidate, ex.getCause() == null ? ex : ex.getCause()));
            }
        }

        return new PricingBatchResult(successful, failed, Duration.between(startedAt, Instant.now()), safeCandidates.size(), settings.minimumSuccessfulResults());
    }

    public boolean hasMinimumSuccessfulResults(PricingBatchResult result) {
        return result != null && result.hasMinimumResults();
    }

    public String circuitBreakerState() {
        return pricingCircuitBreaker.getState().name();
    }

    public ParallelPricingSettings settings() {
        return settings;
    }

    private Callable<QuoteCandidate> resilientTask(QuoteCandidate candidate, Function<QuoteCandidate, QuoteCandidate> candidatePricer) {
        Callable<QuoteCandidate> callable = () -> candidatePricer.apply(candidate);
        Callable<QuoteCandidate> circuitBreakerWrapped = CircuitBreaker.decorateCallable(pricingCircuitBreaker, callable);
        return Bulkhead.decorateCallable(pricingBulkhead, circuitBreakerWrapped);
    }

    private PricingBatchResult interruptedResult(List<QuoteCandidate> candidates, Instant startedAt, InterruptedException ex) {
        List<FailedCandidate> failures = candidates.stream()
            .map(candidate -> FailedCandidate.failed(candidate, ex))
            .toList();
        return new PricingBatchResult(List.of(), failures, Duration.between(startedAt, Instant.now()), candidates.size(), settings.minimumSuccessfulResults());
    }

    private static CircuitBreakerConfig circuitBreakerConfig(ParallelPricingSettings settings) {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(settings.failureRateThreshold())
            .slidingWindowSize(settings.slidingWindowSize())
            .minimumNumberOfCalls(settings.minimumNumberOfCalls())
            .waitDurationInOpenState(settings.waitDurationInOpenState())
            .build();
    }

    private static BulkheadConfig bulkheadConfig(ParallelPricingSettings settings) {
        return BulkheadConfig.custom()
            .maxConcurrentCalls(settings.maxConcurrentCalls())
            .maxWaitDuration(settings.maxBulkheadWait())
            .build();
    }

    private static ExecutorService newPricingExecutor(ParallelPricingSettings settings) {
        try {
            Method factory = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            return (ExecutorService) factory.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            ThreadFactory threadFactory = runnable -> {
                Thread thread = new Thread(runnable, "quote-pricing-worker");
                thread.setDaemon(true);
                return thread;
            };
            return Executors.newFixedThreadPool(settings.maxConcurrentCalls(), threadFactory);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        pricingExecutor.shutdownNow();
        try {
            if (!pricingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                pricingExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            pricingExecutor.shutdownNow();
        }
    }
}
