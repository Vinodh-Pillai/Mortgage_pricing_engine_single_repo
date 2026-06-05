package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class FeatureGovernancePolicyTest {
  @Test
  void shouldExcludeProtectedAndProhibitedProxyFeatures() {
    CaptureFeatureSnapshotCommand base = FeatureSnapshotBuilderTest.command("idem-governance");
    ArrayList<FeatureInput> features = new ArrayList<>(base.features());
    features.add(
        new FeatureInput(
            "protectedClassSignal",
            "STRING",
            "never-expose",
            "PROTECTED",
            "pricing-api",
            "borrower.protectedClass",
            "Inventory record documents why the feature is excluded"));
    features.add(
        new FeatureInput(
            "proxyGeoFeature",
            "STRING",
            "never-expose",
            "PROHIBITED_PROXY",
            "pricing-api",
            "property.proxyGeo",
            "Inventory record documents why the feature is excluded"));

    FeatureSnapshotResponse response =
        FeatureSnapshotBuilderTest.service()
            .captureFeatureSnapshot(
                new CaptureFeatureSnapshotCommand(
                    base.tenantId(),
                    base.idempotencyKey(),
                    base.actorId(),
                    base.scenarioId(),
                    base.pricingResultId(),
                    base.eligibilityResultId(),
                    base.featureSchemaVersion(),
                    base.captureMode(),
                    base.legalBasis(),
                    base.retentionClass(),
                    base.correlationId(),
                    features,
                    base.sourceRefs()))
            .value()
            .orElseThrow();

    assertEquals("REDACTED_WITH_EXCLUSIONS", response.governanceStatus());
    assertTrue(response.features().stream().filter(value -> !value.included()).allMatch(value -> value.redactedValue().equals("[EXCLUDED]")));
    assertTrue(response.features().stream().anyMatch(value -> value.exclusionReason().equals("GOVERNANCE_EXCLUDED_PROTECTED")));
    assertTrue(response.features().stream().anyMatch(value -> value.exclusionReason().equals("GOVERNANCE_EXCLUDED_PROHIBITED_PROXY")));
    assertFalse(response.features().toString().contains("never-expose"));
  }
}
