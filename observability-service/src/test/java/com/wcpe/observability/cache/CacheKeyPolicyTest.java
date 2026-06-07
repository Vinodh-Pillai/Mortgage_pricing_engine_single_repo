package com.wcpe.observability.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheKeyPolicyTest {
  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");

  @Test
  void buildsCanonicalTenantScopedCacheKeyWithoutRawLoanOrBorrowerData() {
    CacheKey key = new CacheKeyPolicy("local")
        .buildKey(
            TENANT_ID,
            new TenantCacheNamespace("pricing-results"),
            new CacheVersion(2, "config-202606"),
            "hash-abc123");

    assertEquals(
        "wcp:local:tenant:aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb:pricing-results:v2:config-202606:hash-abc123",
        key.value());
  }

  @Test
  void rejectsSensitiveKeyMaterialBeforeItCanBeLoggedOrStored() {
    CacheKeyPolicy policy = new CacheKeyPolicy("local");

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> policy.buildKey(
            TENANT_ID,
            new TenantCacheNamespace("reference-data"),
            new CacheVersion(1, "v1"),
            "borrower-email-jane@example.com"));

    assertTrue(exception.getMessage().contains("unsafe cache metadata"));
  }

  @Test
  void rejectsUnsafeNamespaceCharacters() {
    assertThrows(IllegalArgumentException.class, () -> new TenantCacheNamespace("Pricing Results"));
  }
}
