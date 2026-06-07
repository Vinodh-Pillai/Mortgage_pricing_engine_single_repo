package com.wcpe.observability.cache;

public enum CacheInvalidationStatus {
  REQUESTED,
  PROCESSING,
  SUCCEEDED,
  PARTIAL,
  FAILED,
  DEAD_LETTERED,
  REPLAYED
}
