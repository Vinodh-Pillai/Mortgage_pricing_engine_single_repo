package com.wcpe.observability.cache;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CacheInvalidationSourceEvent(
    String eventId,
    UUID tenantId,
    String eventType,
    int schemaVersion,
    TenantCacheNamespace namespace,
    CacheInvalidationScopeType scopeType,
    String scopeRef,
    String versionGraphDigest,
    String correlationId,
    String causationId,
    String producer,
    Instant occurredAt,
    Map<String, String> sourceRefs) {
  static final int SUPPORTED_SCHEMA_VERSION = 1;
  private static final Set<String> CONSUMED_EVENT_TYPES = Set.of(
      "ReferenceDataPublished.v1",
      "PricingRulePublished.v1",
      "ProductConfigPublished.v1",
      "InvestorConfigPublished.v1",
      "TenantCacheFlushRequested.v1");

  public CacheInvalidationSourceEvent {
    eventId = SafeCacheText.requireSafeToken(eventId, "eventId", 120);
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    eventType = SafeCacheText.requireSafeToken(eventType, "eventType", 120);
    namespace = Objects.requireNonNull(namespace, "namespace is required");
    scopeType = Objects.requireNonNull(scopeType, "scopeType is required");
    scopeRef = SafeCacheText.requireSafeToken(scopeRef, "scopeRef", 160);
    versionGraphDigest = SafeCacheText.requireSafeToken(versionGraphDigest, "versionGraphDigest", 160);
    correlationId = SafeCacheText.requireSafeToken(correlationId, "correlationId", 80);
    causationId = SafeCacheText.requireSafeToken(causationId, "causationId", 120);
    producer = SafeCacheText.requireSafeToken(producer, "producer", 80);
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
    sourceRefs = sourceRefs == null ? Map.of() : Map.copyOf(sourceRefs);
    if (!CONSUMED_EVENT_TYPES.contains(eventType)) {
      throw new IllegalArgumentException("unsupported cache invalidation source event type");
    }
    sourceRefs.forEach((key, value) -> {
      SafeCacheText.requireSafeToken(key, "sourceRef key", 80);
      SafeCacheText.requireSafeToken(value, "sourceRef value", 160);
    });
  }

  boolean hasSupportedSchemaVersion() {
    return schemaVersion == SUPPORTED_SCHEMA_VERSION;
  }
}
