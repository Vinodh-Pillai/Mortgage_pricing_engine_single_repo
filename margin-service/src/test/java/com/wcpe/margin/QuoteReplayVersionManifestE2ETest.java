package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuoteReplayVersionManifestE2ETest {
  @Test
  void usesHistoricalVersions() {
    MarginVersioningService service = new MarginVersioningService(
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        MarginVersionResolverTest.version("COMPANY", "company-policy", "company-v1", 1,
            "2026-01-01T00:00:00Z", "2026-03-01T00:00:00Z",
            MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 10));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-2",
        MarginVersionResolverTest.version("COMPANY", "company-policy", "company-v2", 2,
            "2026-03-01T00:00:00Z", null, MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 10));

    var historical = service.resolveManifest("tenant-a", "scope-a", Instant.parse("2026-02-01T12:00:00Z"),
        MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), List.of("COMPANY"));
    var current = service.resolveManifest("tenant-a", "scope-a", Instant.parse("2026-04-01T12:00:00Z"),
        MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), List.of("COMPANY"));
    var replay = service.loadReplayManifest("tenant-a", historical.replayRef()).orElseThrow();

    assertEquals("company-v1", replay.policyVersions().get("COMPANY").versionId());
    assertEquals("company-v2", current.policyVersions().get("COMPANY").versionId());
    assertEquals(historical.resultHash(), replay.resultHash());
  }
}
