package com.wcpe.observability.backpressure;

public enum BackpressureAction {
  ALLOW,
  DISABLE_CACHE_WARM,
  REDUCE_CONCURRENCY,
  DEFER_ASYNC,
  TIGHTEN_RATE_LIMIT,
  REJECT_LOW_PRIORITY,
  SHED_REQUEST,
  MAINTENANCE_DEGRADED
}
