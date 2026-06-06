package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wcpe.margin.MarginVersioningService.InternalMarginVersionResolutionRequest;
import com.wcpe.margin.MarginVersioningService.MarginCompVersionManifest;
import com.wcpe.margin.MarginVersioningService.MarginVersionManifestRequest;
import com.wcpe.margin.MarginVersioningService.MarginVersioningException;
import com.wcpe.margin.MarginVersioningService.PolicyVersionView;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarginVersionApiContractTest {
  @Test
  void activeAtPreview() {
    MarginVersioningService service = new MarginVersioningService(
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        MarginVersionResolverTest.version("VISIBILITY", "visibility-policy", "visibility-v1", 1,
            "2026-01-01T00:00:00Z", null, MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 30));

    var manifest = service.getMarginVersionManifest("tenant-a", new MarginVersionManifestRequest("scope-a",
        Instant.parse("2026-02-01T12:00:00Z"), MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"),
        List.of("VISIBILITY")));
    PolicyVersionView redacted = service.getMarginPolicyVersion("tenant-a", "visibility-policy", "visibility-v1",
        "pricing.margin.version.read");

    assertEquals("visibility-v1", manifest.policyVersions().get("VISIBILITY").versionId());
    assertEquals("visibility-v1", redacted.versionId());
    assertEquals(null, redacted.configHash());
    assertFalse(service.outboxEvents().isEmpty());
  }

  @Test
  void exposesMarginServiceApiEntryPointsAndInternalResolution() {
    MarginVersioningService service = new MarginVersioningService(
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        MarginVersionResolverTest.version("COMPANY", "company-policy", "company-v1", 1,
            "2026-01-01T00:00:00Z", null, MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), 30));

    MarginCompVersionManifest manifest = service.resolveMarginVersionForQuoteService(
        new InternalMarginVersionResolutionRequest("tenant-a", "quote-service", true, "scope-internal",
            Instant.parse("2026-02-01T12:00:00Z"), MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"),
            List.of("COMPANY")));
    MarginVersioningException denied = assertThrows(MarginVersioningException.class,
        () -> service.resolveMarginVersionForQuoteService(new InternalMarginVersionResolutionRequest("tenant-a",
            "browser-user", false, "scope-internal", Instant.parse("2026-02-01T12:00:00Z"),
            MarginVersionResolverTest.scope("RETAIL", "CONVENTIONAL"), List.of("COMPANY"))));

    assertEquals("GET /api/v1/tenants/{tenantId}/margin-version-manifest",
        MarginVersioningService.MARGIN_VERSION_MANIFEST_API);
    assertEquals("GET /margin-policies/{policyId}/versions/{versionId}",
        MarginVersioningService.MARGIN_POLICY_VERSION_API);
    assertEquals("POST /internal/v1/margin-version-resolution",
        MarginVersioningService.INTERNAL_MARGIN_VERSION_RESOLUTION_API);
    assertEquals("company-v1", manifest.policyVersions().get("COMPANY").versionId());
    assertEquals("TENANT_ACCESS_DENIED", denied.getMessage());
  }
}
