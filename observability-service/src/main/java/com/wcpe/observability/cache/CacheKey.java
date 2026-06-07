package com.wcpe.observability.cache;

public record CacheKey(String value) {
  public CacheKey {
    value = SafeCacheText.requireSafeToken(value, "cacheKey", 300);
  }
}
