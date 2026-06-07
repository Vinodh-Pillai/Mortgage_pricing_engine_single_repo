package com.wcpe.observability.cache;

import com.wcpe.observability.scenariohash.CanonicalPricingScenario;
import java.util.Objects;
import java.util.UUID;

public record PricingResultCacheRequest(
    UUID tenantId,
    CanonicalPricingScenario scenario,
    CacheEligibilityPolicy eligibilityPolicy,
    String correlationId,
    String replayRef,
    int cacheSchemaVersion) {
  public PricingResultCacheRequest {
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    scenario = Objects.requireNonNull(scenario, "scenario is required");
    if (!tenantId.equals(scenario.tenantId())) {
      throw new IllegalArgumentException("tenantId must match scenario tenantId");
    }
    eligibilityPolicy = Objects.requireNonNull(eligibilityPolicy, "eligibilityPolicy is required");
    correlationId = SafeCacheText.requireSafeToken(correlationId, "correlationId", 96);
    replayRef = SafeCacheText.requireSafeToken(replayRef, "replayRef", 128);
    if (cacheSchemaVersion < 1) {
      throw new IllegalArgumentException("cacheSchemaVersion must be positive");
    }
  }
}
