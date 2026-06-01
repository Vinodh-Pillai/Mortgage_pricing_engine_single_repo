package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.List;

public record CacheObservationInput(
    String observationId,
    String correlationId,
    String cacheId,
    String cacheKey,
    CacheObservationStatus status,
    Instant observedAt,
    FreshnessMetadata freshness,
    List<String> diagnostics) {
}
