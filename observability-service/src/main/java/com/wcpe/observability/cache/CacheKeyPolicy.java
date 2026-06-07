package com.wcpe.observability.cache;

import java.util.Objects;
import java.util.UUID;
import java.time.LocalDate;

public final class CacheKeyPolicy {
  private static final int MAX_KEY_LENGTH = 300;
  private final String environment;

  public CacheKeyPolicy(String environment) {
    this.environment = SafeCacheText.requireSafeToken(environment, "environment", 32);
  }

  public CacheKey buildKey(
      UUID tenantId,
      TenantCacheNamespace namespace,
      CacheVersion version,
      String hashOrId) {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(namespace, "namespace is required");
    Objects.requireNonNull(version, "version is required");
    String safeHashOrId = SafeCacheText.requireSafeToken(hashOrId, "hashOrId", 128);

    String key = "wcp:"
        + environment
        + ":tenant:"
        + tenantId
        + ":"
        + namespace.value()
        + ":v"
        + version.schemaVersion()
        + ":"
        + version.versionRef()
        + ":"
        + safeHashOrId;
    if (key.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException("cache key exceeds " + MAX_KEY_LENGTH + " characters");
    }
    return new CacheKey(key);
  }

  public CacheKey buildReferenceDataKey(
      UUID tenantId,
      ReferenceDataset dataset,
      CacheVersion version,
      LocalDate asOfDate) {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(dataset, "dataset is required");
    Objects.requireNonNull(version, "version is required");
    Objects.requireNonNull(asOfDate, "asOfDate is required");

    String key = "wcp:"
        + environment
        + ":tenant:"
        + tenantId
        + ":reference:"
        + dataset.value()
        + ":v"
        + version.schemaVersion()
        + ":"
        + version.versionRef()
        + ":"
        + asOfDate;
    if (key.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException("cache key exceeds " + MAX_KEY_LENGTH + " characters");
    }
    return new CacheKey(key);
  }
}
