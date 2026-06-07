package com.wcpe.observability.cache;

import java.time.LocalDate;
import java.util.Objects;

public record EffectiveDateWindow(LocalDate effectiveFrom, LocalDate effectiveTo) {
  public EffectiveDateWindow {
    effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
    if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must not be before effectiveFrom");
    }
  }

  public boolean includes(LocalDate asOfDate) {
    Objects.requireNonNull(asOfDate, "asOfDate is required");
    return !asOfDate.isBefore(effectiveFrom)
        && (effectiveTo == null || !asOfDate.isAfter(effectiveTo));
  }
}
