package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AdvisoryFeedbackTenantIsolationIT {
  @Test
  void shouldPreventCrossTenantFeedback() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-feedback-tenant-base", 0.73)).value().orElseThrow();

    MlAdvisoryResult<AdvisoryFeedback> result =
        service.captureAdvisoryFeedback(
            new CaptureAdvisoryFeedbackCommand(
                AdvisoryTestFixtures.OTHER_TENANT,
                "idem-feedback-cross-tenant",
                "pricing-analyst-1",
                Set.of(MlAdvisoryControlService.FEEDBACK_WRITE_ROLE),
                card.advisoryId(),
                FeedbackOutcome.USEFUL,
                "ACTIONABLE_EXPLANATION",
                "Useful for review.",
                "PRICING_WORKBENCH_CARD",
                "",
                "corr-feedback-cross-tenant-PII-14-S07"));

    assertEquals("ML_FEEDBACK_ADVISORY_NOT_FOUND", result.errorCode().orElseThrow());
  }
}
