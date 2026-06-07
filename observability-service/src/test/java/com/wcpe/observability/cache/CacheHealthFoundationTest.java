package com.wcpe.observability.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheHealthFoundationTest {
  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");

  @Test
  void degradedCacheDisabledIsObservableWithoutFailingTheService() {
    CacheHealthSnapshot snapshot = new CacheHealthSnapshot(
        TENANT_ID,
        new TenantCacheNamespace("reference-data"),
        RedisCacheHealthStatus.DEGRADED_CACHE_DISABLED,
        0,
        0,
        1,
        0,
        0,
        null,
        CacheFallbackReason.CACHE_DISABLED);

    assertTrue(snapshot.degraded());
  }

  @Test
  void healthySnapshotWithNoFallbacksIsNotDegraded() {
    CacheHealthSnapshot snapshot = new CacheHealthSnapshot(
        TENANT_ID,
        new TenantCacheNamespace("reference-data"),
        RedisCacheHealthStatus.HEALTHY,
        10,
        2,
        0,
        0,
        0,
        Instant.parse("2026-06-07T14:40:00Z"),
        null);

    assertFalse(snapshot.degraded());
  }

  @Test
  void auditRecordRejectsSensitiveKeyPattern() {
    assertThrows(IllegalArgumentException.class, () -> new CacheOperationAudit(
        UUID.randomUUID(),
        TENANT_ID,
        new TenantCacheNamespace("reference-data"),
        CacheOperation.FLUSH_NAMESPACE,
        "wcp:local:tenant:aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb:borrower:email",
        "ops-user",
        null,
        "corr-123",
        "FAILED",
        "UNSAFE_KEY_PATTERN",
        Instant.parse("2026-06-07T14:45:00Z")));
  }
}
