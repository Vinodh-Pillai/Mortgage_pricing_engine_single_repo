package com.wcpe.observability.cache;

public enum CacheOperation {
  HEALTH_CHECK,
  READ,
  WRITE,
  DELETE,
  FLUSH_NAMESPACE,
  FALLBACK
}
