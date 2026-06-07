package com.wcpe.tenantcontext.cache;

import java.time.Duration;
import java.util.Map;

public class CacheTtlPolicy {
    private final Map<String, Duration> ttlByDomain;

    public CacheTtlPolicy(Map<String, Duration> ttlByDomain) {
        if (ttlByDomain == null || ttlByDomain.isEmpty()) {
            throw new CacheException("CACHE_TTL_POLICY_MISSING", "tenant-governed TTL configuration is required");
        }
        this.ttlByDomain = Map.copyOf(ttlByDomain);
    }

    public Duration ttlFor(String domain) {
        String normalized = required(domain, "domain");
        Duration ttl = ttlByDomain.get(normalized);
        if (ttl == null) {
            throw new CacheException("CACHE_TTL_POLICY_MISSING", "TTL configuration is missing for domain");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new CacheException("CACHE_TTL_POLICY_INVALID", "TTL must be positive");
        }
        return ttl;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new CacheException("CACHE_TTL_POLICY_MISSING", fieldName + " is required");
        }
        return value.trim();
    }
}
