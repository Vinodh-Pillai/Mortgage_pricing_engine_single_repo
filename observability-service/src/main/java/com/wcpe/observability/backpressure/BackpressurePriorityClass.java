package com.wcpe.observability.backpressure;

public enum BackpressurePriorityClass {
  INTERACTIVE_UI_QUOTES(100),
  PARTNER_API_QUOTES(90),
  BATCH_REPLAY(40),
  CACHE_WARM(20),
  TELEMETRY_EXPORT(10),
  DIAGNOSTICS(5);

  private final int rank;

  BackpressurePriorityClass(int rank) {
    this.rank = rank;
  }

  public int rank() {
    return rank;
  }
}
