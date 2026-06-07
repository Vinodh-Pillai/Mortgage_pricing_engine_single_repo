package com.wcpe.observability.scenariohash;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CanonicalPricingScenario(
    UUID tenantId,
    HashSchemaVersion hashSchemaVersion,
    VersionGraph versionGraph,
    Map<String, Object> pricingInput) {
  public CanonicalPricingScenario {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(hashSchemaVersion, "hashSchemaVersion is required");
    Objects.requireNonNull(versionGraph, "versionGraph is required");
    Objects.requireNonNull(pricingInput, "pricingInput is required");
    pricingInput = Map.copyOf(pricingInput);
  }
}
