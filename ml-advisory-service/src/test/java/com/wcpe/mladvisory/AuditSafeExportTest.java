package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AuditSafeExportTest {
  @Test
  void shouldExcludeRawSensitiveFeatureValues() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(ExplanationSafetyPolicyTest.protectedFeatureCommand()).value().orElseThrow();
    AdvisoryExplanation explanation =
        service
            .getAdvisoryExplanation(
                AdvisoryTestFixtures.TENANT,
                card.advisoryId(),
                "model-risk-reviewer-1",
                Set.of(MlAdvisoryControlService.EXPLANATION_READ_ROLE),
                "pricing-workbench",
                "corr-explanation-export-view")
            .value()
            .orElseThrow();

    AuditSafeExport export =
        service
            .auditSafeExplanationExport(
                AdvisoryTestFixtures.TENANT,
                explanation.explanationId(),
                "model-risk-reviewer-1",
                Set.of(MlAdvisoryControlService.EXPLANATION_EXPORT_ROLE),
                "corr-explanation-export")
            .value()
            .orElseThrow();

    assertTrue(export.notAdverseAction());
    assertFalse(export.toString().contains("720"));
    assertFalse(export.toString().contains("raw credit score"));
  }

  @Test
  void shouldExcludeUnregisteredReasonCodeAndDirection() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(ExplanationSafetyPolicyTest.unsafeUnsuppressedFeatureCommand()).value().orElseThrow();
    AdvisoryExplanation explanation =
        service
            .getAdvisoryExplanation(
                AdvisoryTestFixtures.TENANT,
                card.advisoryId(),
                "model-risk-reviewer-1",
                Set.of(MlAdvisoryControlService.EXPLANATION_READ_ROLE),
                "pricing-workbench",
                "corr-explanation-unsafe-export-view")
            .value()
            .orElseThrow();

    AuditSafeExport export =
        service
            .auditSafeExplanationExport(
                AdvisoryTestFixtures.TENANT,
                explanation.explanationId(),
                "model-risk-reviewer-1",
                Set.of(MlAdvisoryControlService.EXPLANATION_EXPORT_ROLE),
                "corr-explanation-unsafe-export")
            .value()
            .orElseThrow();

    assertFalse(export.toString().contains("credit_score_720"));
    assertFalse(export.toString().contains("raw upward impact 720"));
    assertFalse(export.replayHash().contains("credit_score_720"));
  }
}
