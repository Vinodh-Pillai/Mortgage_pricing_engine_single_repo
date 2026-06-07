package com.wcpe.observability.cache;

import com.wcpe.observability.scenariohash.ScenarioHash;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public record CachedPricingResult(
    int schemaVersion,
    UUID tenantId,
    ScenarioHash scenarioHash,
    PricingResultHash pricingResultHash,
    Map<String, String> sanitizedResultSummary,
    Map<String, String> versionGraph,
    Instant createdAt,
    Instant expiresAt,
    String replayRef) {
  public CachedPricingResult {
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    scenarioHash = Objects.requireNonNull(scenarioHash, "scenarioHash is required");
    pricingResultHash = Objects.requireNonNull(pricingResultHash, "pricingResultHash is required");
    sanitizedResultSummary = sanitizeMap(sanitizedResultSummary, "sanitizedResultSummary", 96, 160);
    versionGraph = sanitizeMap(versionGraph, "versionGraph", 96, 128);
    createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt must be after createdAt");
    }
    replayRef = SafeCacheText.requireSafeToken(replayRef, "replayRef", 128);
  }

  public boolean expiredAt(Instant instant) {
    return !expiresAt.isAfter(Objects.requireNonNull(instant, "instant is required"));
  }

  private static Map<String, String> sanitizeMap(
      Map<String, String> raw,
      String fieldName,
      int keyMaxLength,
      int valueMaxLength) {
    Objects.requireNonNull(raw, fieldName + " is required");
    TreeMap<String, String> sanitized = new TreeMap<>();
    for (Map.Entry<String, String> entry : raw.entrySet()) {
      String key = SafeCacheText.requireSafeToken(entry.getKey(), fieldName + " key", keyMaxLength);
      String value = SafeCacheText.requireSafeToken(entry.getValue(), fieldName + " value", valueMaxLength);
      sanitized.put(key, value);
    }
    return Map.copyOf(sanitized);
  }
}
