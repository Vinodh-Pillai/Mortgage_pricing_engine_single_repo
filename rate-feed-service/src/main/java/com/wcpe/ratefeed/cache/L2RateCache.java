package com.wcpe.ratefeed.cache;

import com.wcpe.ratefeed.resolution.RateResolver.ResolvedSheet;
import java.util.Optional;

public interface L2RateCache {
  Optional<ResolvedSheet> get(RateCacheKey key);
  void put(RateCacheKey key, ResolvedSheet value);
  void invalidateCoverage(RateCoverage coverage);
}
