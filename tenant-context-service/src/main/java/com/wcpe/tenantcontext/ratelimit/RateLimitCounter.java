package com.wcpe.tenantcontext.ratelimit;

import java.time.Instant;

public interface RateLimitCounter {
    CounterSnapshot increment(String key, int windowSeconds, Instant now);
}
