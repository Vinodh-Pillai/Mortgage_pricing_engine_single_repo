package com.wcpe.observability.cache;

import java.util.Optional;

public interface PricingResultCacheStore {
  Optional<CachedPricingResult> get(CacheKey key);

  void put(CacheKey key, CachedPricingResult result);
}
