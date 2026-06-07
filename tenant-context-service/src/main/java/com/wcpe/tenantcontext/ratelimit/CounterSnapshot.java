package com.wcpe.tenantcontext.ratelimit;

import java.time.Instant;

public record CounterSnapshot(int used, Instant resetAt) {
    public CounterSnapshot {
        if (used < 0) {
            throw new RateLimitPolicyException("RATE_LIMIT_COUNTER_INVALID", "used count cannot be negative");
        }
        if (resetAt == null) {
            throw new RateLimitPolicyException("RATE_LIMIT_COUNTER_INVALID", "resetAt is required");
        }
    }
}
