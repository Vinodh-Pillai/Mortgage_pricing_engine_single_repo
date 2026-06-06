package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wcpe.margin.MarginVersioningService.MarginVersioningException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MarginVersionRepositoryIT {
  @Test
  void publishedVersionsImmutable() {
    MarginVersioningService service = new MarginVersioningService(
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        MarginVersionResolverTest.version("COMPANY", "company-policy", "company-v1", 1,
            "2026-01-01T00:00:00Z", null, MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 10));

    MarginVersioningException exception = assertThrows(MarginVersioningException.class,
        () -> service.publishPolicyVersion("tenant-a", "admin-a", "corr-2",
            new MarginVersioningService.PolicyVersionRef("tenant-a", "COMPANY", "company-policy", "company-v1", 1,
                new MarginVersioningService.EffectiveWindow(Instant.parse("2026-01-01T00:00:00Z"), null),
                MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 10, "cfg-company-v1-changed",
                "immutable-company-v1-changed")));

    assertEquals("VERSION_STALE", exception.getMessage());
  }
}
