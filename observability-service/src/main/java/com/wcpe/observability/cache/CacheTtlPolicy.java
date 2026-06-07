package com.wcpe.observability.cache;

import java.util.Map;
import java.util.Objects;

public final class CacheTtlPolicy {
  private final Map<TenantCacheNamespace, CacheTtl> ttlByNamespace;

  public CacheTtlPolicy(Map<TenantCacheNamespace, CacheTtl> ttlByNamespace) {
    if (ttlByNamespace == null || ttlByNamespace.isEmpty()) {
      throw new IllegalArgumentException("tenant-scoped namespace ttl configuration is required");
    }
    this.ttlByNamespace = Map.copyOf(ttlByNamespace);
  }

  public CacheTtl ttlFor(TenantCacheNamespace namespace) {
    Objects.requireNonNull(namespace, "namespace is required");
    CacheTtl ttl = ttlByNamespace.get(namespace);
    if (ttl == null) {
      throw new IllegalStateException(
          "POLICY_NOT_SATISFIED: ttl configuration missing for namespace " + namespace.value());
    }
    return ttl;
  }

  public Map<TenantCacheNamespace, CacheTtl> configuredTtls() {
    return ttlByNamespace;
  }
}
