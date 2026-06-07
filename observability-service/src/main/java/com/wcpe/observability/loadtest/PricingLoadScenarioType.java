package com.wcpe.observability.loadtest;

public enum PricingLoadScenarioType {
  WARM_CACHE_REPEAT_QUOTES("warm-cache-repeat-quotes", 40),
  COLD_CACHE_UNIQUE_QUOTES("cold-cache-unique-quotes", 25),
  REFERENCE_DATA_VERSION_CHANGES("reference-data-version-changes", 10),
  INTENTIONAL_INVALID_REQUESTS("intentional-invalid-requests", 10),
  MULTI_TENANT_PARALLEL("multi-tenant-parallel", 10),
  REDIS_DEGRADED_FALLBACK("redis-degraded-fallback", 5);

  private final String code;
  private final int storyPercentage;

  PricingLoadScenarioType(String code, int storyPercentage) {
    this.code = code;
    this.storyPercentage = storyPercentage;
  }

  public String code() {
    return code;
  }

  public int storyPercentage() {
    return storyPercentage;
  }
}
