package com.wcpe.tenantcontext.cache;

import com.wcpe.tenantcontext.TenantContext;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public class CacheAsideService {
    public CacheAsideResult read(TenantContext tenantContext, CacheKey key, int expectedVersion, Duration ttl,
                                 CachePort cache, Supplier<CachedValue> sourceOfTruth) {
        if (cache == null) {
            throw new CacheException("CACHE_PORT_MISSING", "cache port is required");
        }
        if (sourceOfTruth == null) {
            throw new CacheException("CACHE_SOURCE_OF_TRUTH_MISSING", "source-of-truth fallback is required");
        }
        try {
            Optional<CachedValue> cached = cache.get(key);
            if (cached.isPresent()) {
                cached.get().validateFor(tenantContext, key, expectedVersion);
                return new CacheAsideResult(cached.get(), true, false, "cache-hit");
            }
        } catch (CacheException error) {
            if ("CACHE_STALE_VERSION".equals(error.code()) || "CACHE_KEY_TENANT_MISMATCH".equals(error.code())) {
                throw error;
            }
            return fallback(tenantContext, key, expectedVersion, ttl, cache, sourceOfTruth, error.code());
        } catch (RuntimeException error) {
            return fallback(tenantContext, key, expectedVersion, ttl, cache, sourceOfTruth, "REDIS_UNAVAILABLE");
        }
        return fallback(tenantContext, key, expectedVersion, ttl, cache, sourceOfTruth, "cache-miss");
    }

    private CacheAsideResult fallback(TenantContext tenantContext, CacheKey key, int expectedVersion, Duration ttl,
                                      CachePort cache, Supplier<CachedValue> sourceOfTruth, String reason) {
        CachedValue sourceValue = sourceOfTruth.get();
        if (sourceValue == null) {
            throw new CacheException("CACHE_SOURCE_OF_TRUTH_MISSING", "source-of-truth fallback returned no value");
        }
        sourceValue.validateFor(tenantContext, key, expectedVersion);
        try {
            cache.put(key, sourceValue, ttl);
        } catch (RuntimeException ignored) {
            // Redis/cache is never source of truth; a failed repopulation must not block the DB-backed value.
        }
        return new CacheAsideResult(sourceValue, false, true, reason);
    }

    public interface CachePort {
        Optional<CachedValue> get(CacheKey key);
        void put(CacheKey key, CachedValue value, Duration ttl);
    }

    public record CacheAsideResult(CachedValue value, boolean fromCache, boolean fallbackUsed, String reason) { }
}
