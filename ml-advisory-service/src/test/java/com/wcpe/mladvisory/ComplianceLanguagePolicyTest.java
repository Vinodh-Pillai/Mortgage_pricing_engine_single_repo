package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceLanguagePolicyTest {
  @Test
  void shouldRequireNotAdverseActionDisclaimer() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.eligibilityRiskServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();
    EvaluateEligibilityRiskAdvisoryCommand command =
        new EvaluateEligibilityRiskAdvisoryCommand(
            AdvisoryTestFixtures.TENANT,
            "idem-missing-disclaimer",
            "eligibility-analyst-1",
            "scenario-123",
            "eligibility-result-001",
            "v1",
            snapshotId,
            AdvisoryTestFixtures.approvedArtifact(),
            "REVIEW",
            0.76,
            new AdvisoryDisplayPolicy(0.50, "Eligibility review advisory only.", List.of(AllowedAction.VIEW)),
            List.of(
                new AdvisoryReason(
                    "DOCUMENTATION_REVIEW_PROMPT",
                    1,
                    "Review configured documentation consistency before relying on final eligibility output",
                    "REVIEW_PROMPT",
                    "feature-hash:income-stability",
                    "NON_PUBLIC")),
            AdvisoryTestFixtures.NOW,
            AdvisoryTestFixtures.NOW.plusSeconds(3600),
            "corr-missing-disclaimer",
            250,
            false,
            false,
            false);

    MlAdvisoryResult<EligibilityRiskAdvisoryEvaluation> result =
        service.evaluateEligibilityRiskAdvisory(command, new FakeLocalModelAdapter());

    assertEquals("POLICY_NOT_SATISFIED", result.errorCode().orElseThrow());
  }
}
