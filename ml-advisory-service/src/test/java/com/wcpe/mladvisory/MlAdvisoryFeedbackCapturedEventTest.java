package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MlAdvisoryFeedbackCapturedEventTest {
  @Test
  void shouldExcludeFreeTextComment() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-feedback-event-base", 0.86)).value().orElseThrow();

    service.captureAdvisoryFeedback(
        new CaptureAdvisoryFeedbackCommand(
            card.tenantId(),
            "idem-feedback-event",
            "pricing-analyst-1",
            Set.of(MlAdvisoryControlService.FEEDBACK_WRITE_ROLE),
            card.advisoryId(),
            FeedbackOutcome.REPORT_CONCERN,
            "POSSIBLE_MODEL_BIAS",
            "Concern includes borrower phone 212-555-1212 and must stay out of events.",
            "PRICING_WORKBENCH_CARD",
            "",
            "corr-feedback-event-PII-14-S07"));

    MlAdvisoryOutboxEvent event =
        service.outboxEvents().stream()
            .filter(item -> MlAdvisoryControlService.ML_ADVISORY_FEEDBACK_CAPTURED_EVENT.equals(item.eventType()))
            .findFirst()
            .orElseThrow();
    Map<String, String> payload = event.payload();

    assertTrue(payload.containsKey("reasonCode"));
    assertFalse(payload.containsKey("comment"));
    assertFalse(payload.values().stream().anyMatch(value -> value.contains("212-555-1212")));
  }
}
