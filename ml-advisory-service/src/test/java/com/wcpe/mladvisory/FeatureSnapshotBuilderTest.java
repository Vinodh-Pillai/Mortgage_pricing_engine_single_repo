package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureSnapshotBuilderTest {
  @Test
  void shouldCreateStableHashForSameCanonicalInput() {
    MlAdvisoryControlService service = service();

    FeatureSnapshotResponse first = service.captureFeatureSnapshot(command("idem-1")).value().orElseThrow();
    FeatureSnapshotResponse replay = service.captureFeatureSnapshot(command("idem-1")).value().orElseThrow();

    assertEquals(first, replay);
    assertEquals(first.featureHash(), service.captureFeatureSnapshot(command("idem-2")).value().orElseThrow().featureHash());
    assertEquals(1, service.featureSnapshotsForTenant(command("idem-1").tenantId()).size());
  }

  static MlAdvisoryControlService service() {
    return new MlAdvisoryControlService(Clock.fixed(Instant.parse("2026-06-04T01:00:00Z"), ZoneOffset.UTC));
  }

  static CaptureFeatureSnapshotCommand command(String idempotencyKey) {
    return new CaptureFeatureSnapshotCommand(
        "11111111-1111-1111-1111-111111111111",
        idempotencyKey,
        "pricing-api",
        "scenario-1001",
        "pricing-result-v3",
        "eligibility-result-v2",
        "schema-2026-06",
        CaptureMode.SHADOW_ONLY,
        "tenant-approved-advisory-capture",
        "AUDIT_EVIDENCE",
        "corr-PII-14-S02",
        List.of(
            new FeatureInput(
                "loanPurpose",
                "STRING",
                "PURCHASE",
                "PUBLIC",
                "pricing-api",
                "loan.purpose",
                "Feature inventory item used for advisory calibration"),
            new FeatureInput(
                "creditScoreBand",
                "STRING",
                "720-739",
                "SENSITIVE",
                "pricing-api",
                "borrower.creditScoreBand",
                "Feature inventory item used for model drift grouping")),
        Map.of("pricingResult", "pricing-result-v3", "eligibilityResult", "eligibility-result-v2"));
  }
}
