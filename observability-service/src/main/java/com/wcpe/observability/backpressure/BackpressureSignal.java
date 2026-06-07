package com.wcpe.observability.backpressure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record BackpressureSignal(
    String metricName,
    BigDecimal observedValue,
    int consecutiveBreachedWindows,
    Instant observedAt) {
  public BackpressureSignal {
    metricName = SafeBackpressureText.requireSafeToken(metricName, "metricName", 120);
    Objects.requireNonNull(observedValue, "observedValue is required");
    Objects.requireNonNull(observedAt, "observedAt is required");
    if (observedValue.signum() < 0 || consecutiveBreachedWindows < 0) {
      throw new IllegalArgumentException("signal values must be non-negative");
    }
  }
}
