package com.wcpe.tenantcontext.cache;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

class CacheAsideServiceTest {
    private final CacheAsideService service = new CacheAsideService();
    private final TenantCacheKeyBuilder keyBuilder = new TenantCacheKeyBuilder();

    @Test
    void returnsValidatedCacheHitWithoutSourceOfTruthFallback() {
        TenantContext context = context("tenant-alpha");
        CacheKey key = keyBuilder.keyFor(context, "tenant-context", "profile", "profile-1", 1);
        CachedValue cached = value("tenant-alpha", 1);

        CacheAsideService.CacheAsideResult result = service.read(context, key, 1, Duration.ofMinutes(5), new CacheAsideService.CachePort() {
            @Override public Optional<CachedValue> get(CacheKey key) { return Optional.of(cached); }
            @Override public void put(CacheKey key, CachedValue value, Duration ttl) { throw new AssertionError("put not expected"); }
        }, () -> { throw new AssertionError("source fallback not expected"); });

        assertThat(result.fromCache()).isTrue();
        assertThat(result.fallbackUsed()).isFalse();
    }

    @Test
    void fallsBackToSourceOfTruthWhenRedisUnavailableAndRepopulationFailureDoesNotBlockRead() {
        TenantContext context = context("tenant-alpha");
        CacheKey key = keyBuilder.keyFor(context, "tenant-context", "profile", "profile-1", 1);

        CacheAsideService.CacheAsideResult result = service.read(context, key, 1, Duration.ofMinutes(5), new CacheAsideService.CachePort() {
            @Override public Optional<CachedValue> get(CacheKey key) { throw new RuntimeException("redis unavailable"); }
            @Override public void put(CacheKey key, CachedValue value, Duration ttl) { throw new RuntimeException("redis unavailable"); }
        }, () -> value("tenant-alpha", 1));

        assertThat(result.fromCache()).isFalse();
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.reason()).isEqualTo("REDIS_UNAVAILABLE");
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, new RequestContext("request-1", "trace-1"));
    }

    private static CachedValue value(String tenantId, int version) {
        return new CachedValue(tenantId, version, Instant.parse("2026-06-07T18:30:00Z"), "hash-1", "{\"status\":\"ok\"}");
    }
}
