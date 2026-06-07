package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FeedbackCaptureServiceTest {
  @Test
  void shouldRejectDuplicateActiveFeedback() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-feedback-base", 0.81)).value().orElseThrow();

    MlAdvisoryResult<AdvisoryFeedback> first = service.captureAdvisoryFeedback(command(card, "idem-feedback-1"));
    MlAdvisoryResult<AdvisoryFeedback> duplicate = service.captureAdvisoryFeedback(command(card, "idem-feedback-2"));

    assertTrue(first.valid());
    assertEquals("ML_FEEDBACK_DUPLICATE", duplicate.errorCode().orElseThrow());
  }

  static CaptureAdvisoryFeedbackCommand command(AdvisoryCard card, String idempotencyKey) {
    return new CaptureAdvisoryFeedbackCommand(
        card.tenantId(),
        idempotencyKey,
        "pricing-analyst-1",
        Set.of(MlAdvisoryControlService.FEEDBACK_WRITE_ROLE),
        card.advisoryId(),
        FeedbackOutcome.NOT_USEFUL,
        "MISSING_CONTEXT",
        "Needs more context before I can use this advisory.",
        "PRICING_WORKBENCH_CARD",
        "",
        "corr-feedback-PII-14-S07");
  }
}
