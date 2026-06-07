package com.wcpe.observability.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPricingResultCacheStore implements PricingResultCacheStore {
  private final Map<CacheKey, CachedPricingResult> results = new ConcurrentHashMap<>();

  @Override
  public Optional<CachedPricingResult> get(CacheKey key) {
    return Optional.ofNullable(results.get(key));
  }

  @Override
  public void put(CacheKey key, CachedPricingResult result) {
    results.put(key, result);
  }
}
