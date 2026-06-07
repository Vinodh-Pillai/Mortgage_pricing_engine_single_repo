package com.wcpe.observability.cache;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ReferenceDataRecord(
    UUID tenantId,
    ReferenceDataset dataset,
    ReferenceDataVersion version,
    EffectiveDateWindow effectiveWindow,
    ReferenceDataStatus status,
    Instant publishedAt,
    String payloadDigest,
    Map<String, String> metadata) {
  public ReferenceDataRecord {
    tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
    dataset = Objects.requireNonNull(dataset, "dataset is required");
    version = Objects.requireNonNull(version, "version is required");
    effectiveWindow = Objects.requireNonNull(effectiveWindow, "effectiveWindow is required");
    status = Objects.requireNonNull(status, "status is required");
    publishedAt = Objects.requireNonNull(publishedAt, "publishedAt is required");
    payloadDigest = SafeCacheText.requireSafeToken(payloadDigest, "payloadDigest", 128);
    metadata = metadata == null ? Map.of() : sanitize(metadata);
  }

  public boolean cacheableFor(LocalDate asOfDate) {
    return status == ReferenceDataStatus.PUBLISHED && effectiveWindow.includes(asOfDate);
  }

  private static Map<String, String> sanitize(Map<String, String> metadata) {
    return metadata.entrySet().stream().collect(
        java.util.stream.Collectors.toUnmodifiableMap(
            entry -> SafeCacheText.requireSafeToken(entry.getKey(), "metadata key", 80),
            entry -> SafeCacheText.requireSafeToken(entry.getValue(), "metadata value", 160)));
  }
}
