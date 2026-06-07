package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CacheInvalidationRequestedV1(
    UUID tenantId,
    ReferenceDataset dataset,
    ReferenceDataVersion version,
    String eventType,
    String sourceEventType,
    String cacheKeyPattern,
    String correlationId,
    Instant occurredAt,
    List<String> diagnostics) {
  public CacheInvalidationRequestedV1 {
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    dataset = Objects.requireNonNull(dataset, "dataset is required");
    version = Objects.requireNonNull(version, "version is required");
    eventType = SafeCacheText.requireSafeToken(eventType, "eventType", 80);
    sourceEventType = SafeCacheText.requireSafeToken(sourceEventType, "sourceEventType", 80);
    cacheKeyPattern = SafeCacheText.requireSafeToken(cacheKeyPattern, "cacheKeyPattern", 300);
    correlationId = SafeCacheText.requireSafeToken(correlationId, "correlationId", 80);
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }
}
