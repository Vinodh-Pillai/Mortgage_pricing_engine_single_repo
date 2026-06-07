package com.wcpe.auditreplay.application;

import com.wcpe.auditreplay.config.OutboxProperties;
import java.time.Duration;

public class OutboxRetryPolicy {

    private final OutboxProperties.Publisher properties;

    public OutboxRetryPolicy(OutboxProperties.Publisher properties) {
        this.properties = properties;
    }

    public int maxAttempts() {
        return Math.max(1, properties.getMaxAttempts());
    }

    public Duration nextBackoff(int failedAttemptCount, String stableEventKey) {
        long base = Math.max(1, properties.getInitialBackoffMs());
        long max = Math.max(base, properties.getMaxBackoffMs());
        double multiplier = Math.max(1.0, properties.getBackoffMultiplier());
        double exponential = base * Math.pow(multiplier, Math.max(0, failedAttemptCount - 1));
        long bounded = Math.min(max, Math.round(exponential));
        long jitter = Math.round(bounded * Math.max(0.0, properties.getJitterFactor()) * stableJitter(stableEventKey));
        return Duration.ofMillis(Math.min(max, bounded + jitter));
    }

    private double stableJitter(String key) {
        if (key == null || key.isBlank()) {
            return 0.0;
        }
        return Math.floorMod(key.hashCode(), 1000) / 1000.0;
    }
}
