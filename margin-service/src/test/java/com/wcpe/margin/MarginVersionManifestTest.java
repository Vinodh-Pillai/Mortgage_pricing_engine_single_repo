package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.margin.MarginVersioningService.MarginCompVersionManifest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarginVersionManifestTest {
  @Test
  void hashStableAcrossFieldOrder() {
    MarginVersioningService first = service();
    first.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        MarginVersionResolverTest.version("LO", "lo-plan", "lo-v1", 1,
            "2026-01-01T00:00:00Z", null, MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 20));
    first.publishPolicyVersion("tenant-a", "admin-a", "corr-2",
        MarginVersionResolverTest.version("COMPANY", "company-policy", "company-v1", 1,
            "2026-01-01T00:00:00Z", null, MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 10));

    MarginVersioningService second = service();
    second.publishPolicyVersion("tenant-a", "admin-a", "corr-2",
        MarginVersionResolverTest.version("COMPANY", "company-policy", "company-v1", 1,
            "2026-01-01T00:00:00Z", null, MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 10));
    second.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        MarginVersionResolverTest.version("LO", "lo-plan", "lo-v1", 1,
            "2026-01-01T00:00:00Z", null, MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 20));

    MarginCompVersionManifest firstManifest = first.resolveManifest("tenant-a", "scope-a",
        Instant.parse("2026-02-01T12:00:00Z"), MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"),
        List.of("LO", "COMPANY"));
    MarginCompVersionManifest secondManifest = second.resolveManifest("tenant-a", "scope-a",
        Instant.parse("2026-02-01T12:00:00Z"), MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"),
        List.of("COMPANY", "LO"));

    assertEquals(firstManifest.resultHash(), secondManifest.resultHash());
    assertEquals(List.of("COMPANY", "LO"), firstManifest.policyVersions().keySet().stream().sorted().toList());
    assertTrue(firstManifest.replayRef().startsWith("replay:"));
  }

  private static MarginVersioningService service() {
    return MarginServiceTestStores.marginVersioningService(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }
}
