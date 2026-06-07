package com.wcpe.observability.cache;

import com.wcpe.observability.scenariohash.ScenarioHash;
import java.time.Instant;
import java.util.List;

public record PricingResultCacheOutcome(
    PricingResultCacheStatus status,
    CacheBypassReason bypassReason,
    CacheKey cacheKey,
    ScenarioHash scenarioHash,
    PricingResultHash pricingResultHash,
    Instant expiresAt,
    CachedPricingResult result,
    List<String> diagnostics) {
  public PricingResultCacheOutcome {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }
}
