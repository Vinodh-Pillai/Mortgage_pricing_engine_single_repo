package com.wcpe.tenantcontext.cache;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class CacheValueTenantValidatorTest {
    private final TenantCacheKeyBuilder keyBuilder = new TenantCacheKeyBuilder();

    @Test
    void validatesTenantVersionSourceTimestampAndPayloadHashBeforeUse() {
        TenantContext context = context("tenant-alpha");
        CacheKey key = keyBuilder.keyFor(context, "tenant-context", "profile", "profile-1", 3);
        CachedValue value = new CachedValue("tenant-alpha", 3, Instant.parse("2026-06-07T18:30:00Z"), "hash-1", "{\"status\":\"ok\"}");

        assertThatCode(() -> value.validateFor(context, key, 3)).doesNotThrowAnyException();
    }

    @Test
    void rejectsCrossTenantCachedValueMetadata() {
        TenantContext context = context("tenant-alpha");
        CacheKey key = keyBuilder.keyFor(context, "tenant-context", "profile", "profile-1", 3);
        CachedValue value = new CachedValue("tenant-beta", 3, Instant.parse("2026-06-07T18:30:00Z"), "hash-1", "{}");

        assertThatThrownBy(() -> value.validateFor(context, key, 3))
            .isInstanceOf(CacheException.class)
            .extracting(error -> ((CacheException) error).code())
            .isEqualTo("CACHE_KEY_TENANT_MISMATCH");
    }

    @Test
    void rejectsStaleCachedVersion() {
        TenantContext context = context("tenant-alpha");
        CacheKey key = keyBuilder.keyFor(context, "tenant-context", "profile", "profile-1", 4);
        CachedValue value = new CachedValue("tenant-alpha", 3, Instant.parse("2026-06-07T18:30:00Z"), "hash-1", "{}");

        assertThatThrownBy(() -> value.validateFor(context, key, 4))
            .isInstanceOf(CacheException.class)
            .extracting(error -> ((CacheException) error).code())
            .isEqualTo("CACHE_STALE_VERSION");
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, new RequestContext("request-1", "trace-1"));
    }
}
