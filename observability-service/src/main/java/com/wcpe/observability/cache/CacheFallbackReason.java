package com.wcpe.observability.cache;

public enum CacheFallbackReason {
  TIMEOUT,
  UNAVAILABLE,
  DESERIALIZATION_MISMATCH,
  CIRCUIT_OPEN,
  CACHE_DISABLED
}
