package com.wcpe.observability.cache;

import java.util.Optional;

public interface ReferenceDataCacheStore {
  Optional<ReferenceCacheSnapshot> get(CacheKey key);

  void put(CacheKey key, ReferenceCacheSnapshot snapshot);
}
