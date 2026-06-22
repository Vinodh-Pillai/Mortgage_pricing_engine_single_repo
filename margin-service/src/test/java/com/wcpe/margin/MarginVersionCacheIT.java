package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wcpe.margin.MarginVersioningService.GovernanceChangePublishedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarginVersionCacheIT {
  @Test
  void invalidatesOnGovernanceChange() {
    MarginVersioningService service = MarginServiceTestStores.marginVersioningService(
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        MarginVersionResolverTest.version("COMPANY", "company-policy", "company-v1", 1,
            "2026-01-01T00:00:00Z", null, MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 10));

    service.resolveManifest("tenant-a", "scope-a", Instant.parse("2026-02-01T12:00:00Z"),
        MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), List.of("COMPANY"));
    service.resolveManifest("tenant-a", "scope-a", Instant.parse("2026-02-01T12:00:20Z"),
        MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), List.of("COMPANY"));
    assertEquals(1, service.marginVersionCacheHitTotal.get());

    service.onGovernanceChangePublished(new GovernanceChangePublishedEvent("tenant-a", "COMPANY", "company-policy",
        "company-v1", "admin-a", "corr-governance", Instant.parse("2026-02-01T12:00:30Z")));
    service.resolveManifest("tenant-a", "scope-a", Instant.parse("2026-02-01T12:00:40Z"),
        MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), List.of("COMPANY"));

    assertEquals(1, service.marginVersionCacheHitTotal.get());
    assertEquals("MARGIN_VERSION_MANIFEST_CACHE_INVALIDATED",
        service.auditRecords().get(service.auditRecords().size() - 2).action());
  }
}
