package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ExplanationAssemblerTest {
  @Test
  void shouldIncludeModelSnapshotAndPolicyLineage() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.pricingServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();
    PricingAdvisoryEvaluation evaluation =
        service
            .evaluatePricingAdvisory(
                AdvisoryTestFixtures.pricingEvaluationCommand("idem-explanation-assembler", 0.82, snapshotId, false, false, false),
                new FakeLocalModelAdapter())
            .value()
            .orElseThrow();

    AdvisoryExplanation explanation =
        service
            .getAdvisoryExplanation(
                AdvisoryTestFixtures.TENANT,
                evaluation.advisoryId(),
                "pricing-analyst-1",
                Set.of(MlAdvisoryControlService.EXPLANATION_READ_ROLE),
                "pricing-workbench",
                "corr-explanation-assembler")
            .value()
            .orElseThrow();

    assertEquals("model-version-approved-1", explanation.modelVersion());
    assertEquals(snapshotId, explanation.featureSnapshotId());
    assertFalse(explanation.policyVersion().isBlank());
    assertEquals(false, explanation.authoritative());
  }
}
