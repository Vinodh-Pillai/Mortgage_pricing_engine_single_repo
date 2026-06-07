package com.wcpe.observability.ratelimit;

import java.time.Instant;

public record RateLimitCounterSnapshot(int used, int remaining, Instant resetAt) {
  public RateLimitCounterSnapshot {
    if (used < 0 || remaining < 0) {
      throw new IllegalArgumentException("counter values cannot be negative");
    }
    if (resetAt == null) {
      throw new IllegalArgumentException("resetAt is required");
    }
  }
}
