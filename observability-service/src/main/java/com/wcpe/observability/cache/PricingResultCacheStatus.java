package com.wcpe.observability.cache;

public enum PricingResultCacheStatus {
  HIT,
  MISS,
  BYPASS,
  STALE,
  FALLBACK
}
