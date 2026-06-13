package com.wcpe.quote.parallel;

import java.time.Duration;

public record ParallelPricingSettings(
    Duration timeoutPerCandidate,
    int maxConcurrentCalls,
    Duration maxBulkheadWait,
    float failureRateThreshold,
    int slidingWindowSize,
    int minimumNumberOfCalls,
    Duration waitDurationInOpenState,
    int minimumSuccessfulResults
) {
    public ParallelPricingSettings {
        timeoutPerCandidate = timeoutPerCandidate == null ? Duration.ofSeconds(2) : timeoutPerCandidate;
        if (maxConcurrentCalls <= 0) {
            maxConcurrentCalls = 32;
        }
        maxBulkheadWait = maxBulkheadWait == null ? Duration.ofSeconds(5) : maxBulkheadWait;
        if (failureRateThreshold <= 0.0f) {
            failureRateThreshold = 50.0f;
        }
        if (slidingWindowSize <= 0) {
            slidingWindowSize = 100;
        }
        if (minimumNumberOfCalls <= 0) {
            minimumNumberOfCalls = Math.min(10, slidingWindowSize);
        }
        waitDurationInOpenState = waitDurationInOpenState == null ? Duration.ofSeconds(30) : waitDurationInOpenState;
        if (minimumSuccessfulResults <= 0) {
            minimumSuccessfulResults = 1;
        }
    }

    public static ParallelPricingSettings defaults() {
        return new ParallelPricingSettings(Duration.ofSeconds(2), 32, Duration.ofSeconds(5), 50.0f, 100, 10, Duration.ofSeconds(30), 1);
    }

    public static ParallelPricingSettings productionDefaults() {
        return new ParallelPricingSettings(Duration.ofSeconds(2), 32, Duration.ofSeconds(5), 50.0f, 100, 10, Duration.ofSeconds(30), 3);
    }
}
