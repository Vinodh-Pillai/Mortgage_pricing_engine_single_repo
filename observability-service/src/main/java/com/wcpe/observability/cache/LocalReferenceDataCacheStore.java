package com.wcpe.observability.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalReferenceDataCacheStore implements ReferenceDataCacheStore {
  private final Map<CacheKey, ReferenceCacheSnapshot> snapshots = new ConcurrentHashMap<>();

  @Override
  public Optional<ReferenceCacheSnapshot> get(CacheKey key) {
    return Optional.ofNullable(snapshots.get(key));
  }

  @Override
  public void put(CacheKey key, ReferenceCacheSnapshot snapshot) {
    snapshots.put(key, snapshot);
  }
}
