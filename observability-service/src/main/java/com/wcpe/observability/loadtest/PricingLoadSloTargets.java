package com.wcpe.observability.loadtest;

public record PricingLoadSloTargets(
    int warmCacheP95Millis,
    int coldCacheP95Millis,
    int p99Millis,
    double maxErrorRate,
    double minRepeatCacheHitRatio) {
  public PricingLoadSloTargets {
    if (warmCacheP95Millis <= 0 || coldCacheP95Millis <= 0 || p99Millis <= 0) {
      throw new IllegalArgumentException("latency SLO targets must be positive");
    }
    if (maxErrorRate < 0 || maxErrorRate >= 1) {
      throw new IllegalArgumentException("max error rate must be between 0 and 1");
    }
    if (minRepeatCacheHitRatio <= 0 || minRepeatCacheHitRatio > 1) {
      throw new IllegalArgumentException("minimum cache hit ratio must be between 0 and 1");
    }
  }

  public static PricingLoadSloTargets fromStoryTargets() {
    return new PricingLoadSloTargets(500, 1500, 2500, 0.005, 0.80);
  }
}
