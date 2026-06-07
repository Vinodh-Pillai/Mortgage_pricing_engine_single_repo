package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class PricingAdvisoryCacheIT {
  @Test
  void shouldInvalidateWhenPricingResultOrModelVersionChanges() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.pricingServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();
    PricingAdvisoryEvaluation first =
        service
            .evaluatePricingAdvisory(
                AdvisoryTestFixtures.pricingEvaluationCommand("idem-cache-one", 0.82, snapshotId, false, false, false),
                new FakeLocalModelAdapter())
            .value()
            .orElseThrow();
    EvaluatePricingAdvisoryCommand modelChanged =
        new EvaluatePricingAdvisoryCommand(
            AdvisoryTestFixtures.TENANT,
            "idem-cache-model-two",
            "pricing-analyst-1",
            "scenario-123",
            "pricing-result-456",
            snapshotId,
            new ModelArtifactRef(
                "model-version-approved-2",
                "local://models/ml-advisory/model-version-approved-2.fake",
                "sha256:local-approved-model",
                "sha256:local-approved-model",
                "APPROVED_FOR_ADVISORY",
                "ml-advisory-feature-schema-v1"),
            0.82,
            AdvisoryDisplayPolicy.configured(0.50),
            first.reasons(),
            AdvisoryTestFixtures.NOW,
            AdvisoryTestFixtures.NOW.plusSeconds(3600),
            "corr-pricing-advisory-PII-14-S05-model-two",
            250,
            false,
            false,
            false);

    PricingAdvisoryEvaluation second = service.evaluatePricingAdvisory(modelChanged, new FakeLocalModelAdapter()).value().orElseThrow();

    assertNotEquals(first.advisoryId(), second.advisoryId());
  }
}
