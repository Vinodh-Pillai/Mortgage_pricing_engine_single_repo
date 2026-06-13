package com.wcpe.ratefeed.cache;

import com.wcpe.ratefeed.resolution.RateResolver.ResolvedSheet;
import java.util.Optional;

final class NoOpL2RateCache implements L2RateCache {
  @Override public Optional<ResolvedSheet> get(RateCacheKey key) { return Optional.empty(); }
  @Override public void put(RateCacheKey key, ResolvedSheet value) { }
  @Override public void invalidateCoverage(RateCoverage coverage) { }
}
