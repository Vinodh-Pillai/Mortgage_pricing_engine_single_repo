package com.wcpe.observability.backpressure;

import java.math.BigDecimal;
import java.util.Objects;

public record BackpressureTrigger(
    String metricName,
    BigDecimal threshold,
    int consecutiveWindows,
    BackpressureState targetState,
    BackpressureAction action) {
  public BackpressureTrigger {
    metricName = SafeBackpressureText.requireSafeToken(metricName, "metricName", 120);
    Objects.requireNonNull(threshold, "threshold is required");
    Objects.requireNonNull(targetState, "targetState is required");
    Objects.requireNonNull(action, "action is required");
    if (threshold.signum() < 0) {
      throw new IllegalArgumentException("threshold must come from non-negative tenant configuration");
    }
    if (consecutiveWindows <= 0) {
      throw new IllegalArgumentException("consecutiveWindows must be configured");
    }
  }
}
