package com.wcpe.observability.cache;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReferenceCacheSnapshot(
    UUID tenantId,
    ReferenceDataset dataset,
    ReferenceDataVersion referenceDataVersion,
    LocalDate asOfDate,
    CacheKey cacheKey,
    ReferenceCacheStatus cacheStatus,
    EffectiveDateWindow effectiveWindow,
    Instant publishedAt,
    Instant expiresAt,
    String payloadDigest,
    List<String> diagnostics) {
  public ReferenceCacheSnapshot {
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    dataset = Objects.requireNonNull(dataset, "dataset is required");
    referenceDataVersion = Objects.requireNonNull(referenceDataVersion, "referenceDataVersion is required");
    asOfDate = Objects.requireNonNull(asOfDate, "asOfDate is required");
    cacheKey = Objects.requireNonNull(cacheKey, "cacheKey is required");
    cacheStatus = Objects.requireNonNull(cacheStatus, "cacheStatus is required");
    effectiveWindow = Objects.requireNonNull(effectiveWindow, "effectiveWindow is required");
    publishedAt = Objects.requireNonNull(publishedAt, "publishedAt is required");
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    payloadDigest = SafeCacheText.requireSafeToken(payloadDigest, "payloadDigest", 128);
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }
}
