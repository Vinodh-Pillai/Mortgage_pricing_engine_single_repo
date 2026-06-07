package com.wcpe.tenantcontext.ratelimit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class InMemoryRateLimitCounter implements RateLimitCounter {
    private final Map<String, Integer> counters = new HashMap<>();

    @Override
    public synchronized CounterSnapshot increment(String key, int windowSeconds, Instant now) {
        if (key == null || key.isBlank()) {
            throw new RateLimitPolicyException("RATE_LIMIT_COUNTER_INVALID", "counter key is required");
        }
        if (windowSeconds < 1) {
            throw new RateLimitPolicyException("RATE_LIMIT_COUNTER_INVALID", "windowSeconds must be positive");
        }
        Instant currentTime = now == null ? Instant.now() : now;
        int used = counters.merge(key, 1, Integer::sum);
        return new CounterSnapshot(used, currentTime.plusSeconds(windowSeconds));
    }
}
