package com.wcpe.observability.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CacheTtlPolicyTest {
  @Test
  void readsTenantScopedNamespaceTtlFromConfiguration() {
    TenantCacheNamespace namespace = new TenantCacheNamespace("pricing-results");
    CacheTtl ttl = new CacheTtl(Duration.ofMinutes(15));
    CacheTtlPolicy policy = new CacheTtlPolicy(Map.of(namespace, ttl));

    assertEquals(ttl, policy.ttlFor(namespace));
  }

  @Test
  void failsClosedWhenNamespaceTtlConfigurationIsMissing() {
    CacheTtlPolicy policy = new CacheTtlPolicy(Map.of(
        new TenantCacheNamespace("reference-data"),
        new CacheTtl(Duration.ofHours(6))));

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> policy.ttlFor(new TenantCacheNamespace("rate-limit-buckets")));

    assertTrue(exception.getMessage().contains("POLICY_NOT_SATISFIED"));
  }

  @Test
  void rejectsNonPositiveTtls() {
    assertThrows(IllegalArgumentException.class, () -> new CacheTtl(Duration.ZERO));
  }
}
