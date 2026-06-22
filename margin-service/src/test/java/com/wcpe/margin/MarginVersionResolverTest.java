package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wcpe.margin.MarginVersioningService.EffectiveWindow;
import com.wcpe.margin.MarginVersioningService.MarginCompVersionManifest;
import com.wcpe.margin.MarginVersioningService.MarginResolutionScope;
import com.wcpe.margin.MarginVersioningService.MarginVersioningException;
import com.wcpe.margin.MarginVersioningService.PolicyVersionRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarginVersionResolverTest {
  private final MarginVersioningService service = MarginServiceTestStores.marginVersioningService(
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void selectsActiveUtcVersion() {
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        version("COMPANY", "company-policy", "company-v1", 1,
            "2026-01-01T00:00:00Z", "2026-03-01T00:00:00Z", scope("RETAIL", "CONVENTIONAL"), 10));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-2",
        version("COMPANY", "company-policy", "company-v2", 2,
            "2026-03-01T00:00:00Z", null, scope("RETAIL", "CONVENTIONAL"), 10));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-3",
        version("LO", "lo-plan", "lo-v1", 1,
            "2026-01-01T00:00:00Z", null, scope("*", "CONVENTIONAL"), 20));

    MarginCompVersionManifest current = service.resolveManifest("tenant-a", "scope-current",
        Instant.parse("2026-04-15T16:30:00Z"), scope("RETAIL", "CONVENTIONAL"), List.of("LO", "COMPANY"));
    MarginCompVersionManifest historical = service.resolveManifest("tenant-a", "scope-history",
        Instant.parse("2026-02-15T16:30:00Z"), scope("RETAIL", "CONVENTIONAL"), List.of("COMPANY"));

    assertEquals("company-v2", current.policyVersions().get("COMPANY").versionId());
    assertEquals("lo-v1", current.policyVersions().get("LO").versionId());
    assertEquals("company-v1", historical.policyVersions().get("COMPANY").versionId());
    assertEquals("margin-version-resolver-v1", current.resolverEngineVersion());
    assertEquals(2, service.marginVersionResolveTotal.get());
  }

  @Test
  void selectsHighestPriorityBeforeVersionNumberWhenSpecificityTies() {
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        version("COMPANY", "company-policy-a", "company-v99", 99,
            "2026-01-01T00:00:00Z", null, sameSpecificityScope("*", "broker-a"), 10));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-2",
        version("COMPANY", "company-policy-b", "company-v1", 1,
            "2026-01-01T00:00:00Z", null, sameSpecificityScope("branch-a", "*"), 20));

    MarginCompVersionManifest manifest = service.resolveManifest("tenant-a", "scope-priority",
        Instant.parse("2026-02-15T16:30:00Z"), scope("RETAIL", "CONVENTIONAL"), List.of("COMPANY"));

    assertEquals("company-v1", manifest.policyVersions().get("COMPANY").versionId());
  }

  @Test
  void selectsHighestVersionNumberWhenSpecificityAndPriorityTie() {
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        version("COMPANY", "company-policy-a", "company-v1", 1,
            "2026-01-01T00:00:00Z", null, sameSpecificityScope("*", "broker-a"), 20));
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-2",
        version("COMPANY", "company-policy-b", "company-v2", 2,
            "2026-01-01T00:00:00Z", null, sameSpecificityScope("branch-a", "*"), 20));

    MarginCompVersionManifest manifest = service.resolveManifest("tenant-a", "scope-version",
        Instant.parse("2026-02-15T16:30:00Z"), scope("RETAIL", "CONVENTIONAL"), List.of("COMPANY"));

    assertEquals("company-v2", manifest.policyVersions().get("COMPANY").versionId());
  }

  @Test
  void failsOnOverlap() {
    service.publishPolicyVersion("tenant-a", "admin-a", "corr-1",
        version("COMPANY", "company-policy-a", "company-v1", 1,
            "2026-01-01T00:00:00Z", null, scope("RETAIL", "CONVENTIONAL"), 10));

    MarginVersioningException exception = assertThrows(MarginVersioningException.class,
        () -> service.publishPolicyVersion("tenant-a", "admin-a", "corr-2",
            version("COMPANY", "company-policy-b", "company-v2", 1,
                "2026-02-01T00:00:00Z", null, scope("RETAIL", "CONVENTIONAL"), 10)));

    assertEquals("VERSION_EFFECTIVE_OVERLAP", exception.getMessage());
    assertEquals(1, service.marginVersionOverlapDetectedTotal.get());
  }

  static PolicyVersionRef version(String type, String policyId, String versionId, int versionNumber,
      String from, String to, MarginResolutionScope scope, int priority) {
    return new PolicyVersionRef("tenant-a", type, policyId, versionId, versionNumber,
        new EffectiveWindow(Instant.parse(from), to == null ? null : Instant.parse(to)), scope, priority,
        "cfg-" + versionId, "immutable-" + versionId);
  }

  static MarginResolutionScope scope(String channel, String productFamily) {
    return new MarginResolutionScope(Map.of(
        "channel", channel,
        "productFamily", productFamily,
        "branchId", "branch-a",
        "brokerId", "broker-a"));
  }

  static MarginResolutionScope sameSpecificityScope(String branchId, String brokerId) {
    return new MarginResolutionScope(Map.of(
        "channel", "RETAIL",
        "productFamily", "CONVENTIONAL",
        "branchId", branchId,
        "brokerId", brokerId));
  }
}
