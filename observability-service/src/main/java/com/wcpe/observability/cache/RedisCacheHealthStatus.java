package com.wcpe.observability.cache;

public enum RedisCacheHealthStatus {
  HEALTHY,
  DEGRADED_CACHE_DISABLED,
  UNAVAILABLE,
  CIRCUIT_OPEN
}
