package com.wcpe.observability.cache;

import java.util.Objects;

public record CacheVersion(int schemaVersion, String versionRef) {
  public CacheVersion {
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
    versionRef = SafeCacheText.requireSafeToken(versionRef, "versionRef", 96);
  }
}
