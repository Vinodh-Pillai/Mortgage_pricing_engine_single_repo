package com.wcpe.tenantcontext.cache;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

class CacheTtlPolicyTest {
    @Test
    void returnsConfiguredTtlWithoutInventingDomainDefaults() {
        CacheTtlPolicy policy = new CacheTtlPolicy(Map.of("tenant-context", Duration.ofMinutes(15)));

        assertThat(policy.ttlFor("tenant-context")).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void failsClosedWhenDomainTtlConfigurationIsMissing() {
        CacheTtlPolicy policy = new CacheTtlPolicy(Map.of("tenant-context", Duration.ofMinutes(15)));

        assertThatThrownBy(() -> policy.ttlFor("pricing-config"))
            .isInstanceOf(CacheException.class)
            .extracting(error -> ((CacheException) error).code())
            .isEqualTo("CACHE_TTL_POLICY_MISSING");
    }
}
