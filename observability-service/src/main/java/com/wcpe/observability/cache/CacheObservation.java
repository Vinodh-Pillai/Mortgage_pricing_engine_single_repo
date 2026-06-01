package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.List;

public record CacheObservation(
    String observationId,
    String correlationId,
    String cacheId,
    String cacheKey,
    CacheObservationStatus status,
    Instant observedAt,
    FreshnessMetadata freshness,
    List<String> diagnostics) {
  public boolean healthy() {
    return status == CacheObservationStatus.HEALTHY;
  }
}
