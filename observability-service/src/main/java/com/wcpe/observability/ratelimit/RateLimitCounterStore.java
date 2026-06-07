package com.wcpe.observability.ratelimit;

import java.time.Instant;

public interface RateLimitCounterStore {
  RateLimitCounterSnapshot increment(RateLimitCounterKey key, int capacity, Instant resetAt);
}
