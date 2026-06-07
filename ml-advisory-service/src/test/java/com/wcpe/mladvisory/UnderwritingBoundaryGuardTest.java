package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnderwritingBoundaryGuardTest {
  @Test
  void shouldRejectDenialLikeLanguage() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.eligibilityRiskServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();

    EligibilityRiskAdvisoryEvaluation evaluation =
        service
            .evaluateEligibilityRiskAdvisory(
                AdvisoryTestFixtures.eligibilityRiskCommand(
                    "idem-eligibility-denial-language",
                    snapshotId,
                    List.of(
                        new AdvisoryReason(
                            "DENIAL_LIKE_PROMPT",
                            1,
                            "Borrower should be denied based on this advisory signal",
                            "ADVERSE_ACTION",
                            "feature-hash:income-stability",
                            "NON_PUBLIC")),
                    false,
                    false,
                    false),
                new FakeLocalModelAdapter())
            .value()
            .orElseThrow();

    assertEquals("SUPPRESSED", evaluation.status());
    assertEquals("ML_UNDERWRITING_BOUNDARY_BLOCKED", evaluation.suppressionReason());
    assertTrue(service.advisoryCardsForTenant(AdvisoryTestFixtures.TENANT).isEmpty());
  }
}
