package com.wcpe.observability.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ReferenceDataCacheService {
  public static final TenantCacheNamespace REFERENCE_DATA_NAMESPACE = new TenantCacheNamespace("reference-data");
  public static final String INVALIDATION_EVENT_TYPE = "CacheInvalidationRequested.v1";

  private final Clock clock;
  private final CacheKeyPolicy cacheKeyPolicy;
  private final CacheTtlPolicy ttlPolicy;
  private final ReferenceDataCacheStore cacheStore;
  private final ReferenceDataSource referenceDataSource;

  public ReferenceDataCacheService(
      Clock clock,
      CacheKeyPolicy cacheKeyPolicy,
      CacheTtlPolicy ttlPolicy,
      ReferenceDataCacheStore cacheStore,
      ReferenceDataSource referenceDataSource) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.cacheKeyPolicy = Objects.requireNonNull(cacheKeyPolicy, "cacheKeyPolicy is required");
    this.ttlPolicy = Objects.requireNonNull(ttlPolicy, "ttlPolicy is required");
    this.cacheStore = Objects.requireNonNull(cacheStore, "cacheStore is required");
    this.referenceDataSource = Objects.requireNonNull(referenceDataSource, "referenceDataSource is required");
  }

  public ReferenceCacheSnapshot resolve(
      UUID tenantId,
      ReferenceDataset dataset,
      ReferenceDataVersion requestedVersion,
      LocalDate asOfDate,
      int schemaVersion) {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(dataset, "dataset is required");
    Objects.requireNonNull(requestedVersion, "requestedVersion is required");
    Objects.requireNonNull(asOfDate, "asOfDate is required");

    List<String> diagnostics = new ArrayList<>();
    ReferenceDataRecord sourceRecord = resolvePublishedSourceVersion(
        tenantId,
        dataset,
        requestedVersion,
        asOfDate,
        diagnostics);

    CacheVersion cacheVersion = new CacheVersion(schemaVersion, sourceRecord.version().value());
    CacheKey cacheKey = cacheKeyPolicy.buildReferenceDataKey(
        tenantId,
        dataset,
        cacheVersion,
        asOfDate);
    Duration ttl = ttlPolicy.ttlFor(REFERENCE_DATA_NAMESPACE).value();

    try {
      var cached = cacheStore.get(cacheKey);
      if (cached.isPresent() && cached.get().expiresAt().isAfter(clock.instant())) {
        diagnostics.add("reference cache hit: published source version still resolves");
        return withDiagnostics(cached.get(), ReferenceCacheStatus.HIT, diagnostics);
      }
      diagnostics.add("reference cache miss: deterministic key absent or expired");
    } catch (RuntimeException exception) {
      diagnostics.add("reference cache fallback: cache read unavailable; source data remains authoritative");
      return snapshot(sourceRecord, asOfDate, cacheKey, ReferenceCacheStatus.FALLBACK, ttl, diagnostics);
    }

    ReferenceCacheSnapshot snapshot = snapshot(
        sourceRecord,
        asOfDate,
        cacheKey,
        ReferenceCacheStatus.MISS,
        ttl,
        diagnostics);
    try {
      cacheStore.put(cacheKey, snapshot);
    } catch (RuntimeException exception) {
      diagnostics.add("reference cache fallback: cache write unavailable; source data remains authoritative");
      return snapshot(sourceRecord, asOfDate, cacheKey, ReferenceCacheStatus.FALLBACK, ttl, diagnostics);
    }
    return snapshot;
  }

  public CacheInvalidationRequestedV1 invalidationFor(ReferenceDataChangeEvent event) {
    Objects.requireNonNull(event, "event is required");
    String keyPattern = "wcp:*:tenant:"
        + event.tenantId()
        + ":reference:"
        + event.dataset().value()
        + ":v*:"
        + event.version().value()
        + ":*";
    return new CacheInvalidationRequestedV1(
        event.tenantId(),
        event.dataset(),
        event.version(),
        INVALIDATION_EVENT_TYPE,
        event.sourceEventType(),
        keyPattern,
        event.correlationId(),
        event.occurredAt(),
        List.of("tenant/dataset/version invalidation requested from " + event.sourceEventType()));
  }

  private ReferenceDataRecord resolvePublishedSourceVersion(
      UUID tenantId,
      ReferenceDataset dataset,
      ReferenceDataVersion requestedVersion,
      LocalDate asOfDate,
      List<String> diagnostics) {
    List<ReferenceDataRecord> matches = referenceDataSource.findVersions(tenantId, dataset, asOfDate).stream()
        .filter(record -> record.tenantId().equals(tenantId))
        .filter(record -> record.dataset().equals(dataset))
        .filter(record -> record.version().equals(requestedVersion))
        .filter(record -> record.effectiveWindow().includes(asOfDate))
        .filter(record -> record.status() == ReferenceDataStatus.PUBLISHED)
        .toList();

    if (matches.isEmpty()) {
      throw new IllegalStateException(
          "POLICY_NOT_SATISFIED: no published reference data version for tenant/dataset/asOfDate");
    }
    if (matches.size() > 1) {
      throw new IllegalStateException(
          "POLICY_NOT_SATISFIED: ambiguous published reference data version for tenant/dataset/asOfDate");
    }
    diagnostics.add("source resolution: exactly one published version selected; draft/suspended data excluded");
    return matches.get(0);
  }

  private ReferenceCacheSnapshot snapshot(
      ReferenceDataRecord record,
      LocalDate asOfDate,
      CacheKey cacheKey,
      ReferenceCacheStatus status,
      Duration ttl,
      List<String> diagnostics) {
    return new ReferenceCacheSnapshot(
        record.tenantId(),
        record.dataset(),
        record.version(),
        asOfDate,
        cacheKey,
        status,
        record.effectiveWindow(),
        record.publishedAt(),
        clock.instant().plus(ttl),
        record.payloadDigest(),
        diagnostics);
  }

  private static ReferenceCacheSnapshot withDiagnostics(
      ReferenceCacheSnapshot snapshot,
      ReferenceCacheStatus status,
      List<String> diagnostics) {
    return new ReferenceCacheSnapshot(
        snapshot.tenantId(),
        snapshot.dataset(),
        snapshot.referenceDataVersion(),
        snapshot.asOfDate(),
        snapshot.cacheKey(),
        status,
        snapshot.effectiveWindow(),
        snapshot.publishedAt(),
        snapshot.expiresAt(),
        snapshot.payloadDigest(),
        diagnostics);
  }
}
