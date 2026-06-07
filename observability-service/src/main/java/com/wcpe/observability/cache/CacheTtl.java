package com.wcpe.observability.cache;

import java.time.Duration;
import java.util.Objects;

public record CacheTtl(Duration value) {
  public CacheTtl {
    value = Objects.requireNonNull(value, "ttl is required");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
  }
}
