package com.wcpe.observability.ratelimit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class InMemoryRateLimitCounterStore implements RateLimitCounterStore {
  private final Map<RateLimitCounterKey, Integer> counters = new HashMap<>();

  @Override
  public synchronized RateLimitCounterSnapshot increment(RateLimitCounterKey key, int capacity, Instant resetAt) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    int used = counters.getOrDefault(key, 0) + 1;
    counters.put(key, used);
    return new RateLimitCounterSnapshot(used, Math.max(0, capacity - used), resetAt);
  }
}
