package com.wcpe.observability.backpressure;

public enum BackpressureState {
  NORMAL,
  WATCH,
  THROTTLING,
  DEGRADED,
  SHEDDING,
  RECOVERY;

  public boolean atLeast(BackpressureState other) {
    return ordinal() >= other.ordinal();
  }
}
