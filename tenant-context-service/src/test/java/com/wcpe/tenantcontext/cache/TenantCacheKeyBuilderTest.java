package com.wcpe.tenantcontext.cache;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import org.junit.jupiter.api.Test;

class TenantCacheKeyBuilderTest {
    private final TenantCacheKeyBuilder builder = new TenantCacheKeyBuilder();

    @Test
    void buildsDeterministicTenantScopedVersionedKey() {
        CacheKey key = builder.keyFor(new TenantContext("Tenant-Alpha", new RequestContext("request-1", "trace-1")),
            "PricingConfig", "Policy", "Config-42", 7);

        assertThat(key.value()).isEqualTo("tenant:tenant-alpha:pricingconfig:policy:config-42:v7");
        assertThat(key.platformStaticApproved()).isFalse();
    }

    @Test
    void rejectsSegmentsThatCouldEscapeTenantNamespace() {
        assertThatThrownBy(() -> builder.keyFor(new TenantContext("tenant-alpha", new RequestContext("request-1", "trace-1")),
                "pricing:config", "Policy", "Config-42", 1))
            .isInstanceOf(CacheException.class)
            .extracting(error -> ((CacheException) error).code())
            .isEqualTo("CACHE_KEY_VALIDATION_FAILED");
    }

    @Test
    void platformStaticKeysRequireExplicitApprovedPrefix() {
        CacheKey key = builder.approvedPlatformStaticKey("reference", "county-list");

        assertThat(key.value()).isEqualTo("platform-static:reference:county-list");
        assertThat(key.platformStaticApproved()).isTrue();
        assertThatThrownBy(() -> new CacheKey("reference:county-list", true))
            .isInstanceOf(CacheException.class)
            .extracting(error -> ((CacheException) error).code())
            .isEqualTo("CACHE_KEY_PLATFORM_STATIC_UNAPPROVED");
    }
}
