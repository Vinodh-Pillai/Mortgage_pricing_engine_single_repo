package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class AdvisoryFeedbackControllerContractTest {
  @Test
  void shouldPersistStructuredReasonCode() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-feedback-contract-base", 0.77)).value().orElseThrow();
    AdvisoryFeedbackController controller = new AdvisoryFeedbackController(service);

    ResponseEntity<?> response =
        controller.capture(
            card.tenantId(),
            card.advisoryId(),
            new AdvisoryFeedbackController.AdvisoryFeedbackRequest(
                "idem-feedback-contract",
                "pricing-analyst-1",
                Set.of(MlAdvisoryControlService.FEEDBACK_WRITE_ROLE),
                "USEFUL",
                "ACTIONABLE_EXPLANATION",
                "Useful advisory for pricing desk review.",
                "PRICING_WORKBENCH_CARD",
                "",
                "corr-feedback-contract-PII-14-S07"));

    assertEquals(201, response.getStatusCode().value());
    AdvisoryFeedback feedback = (AdvisoryFeedback) response.getBody();
    assertEquals("ACTIONABLE_EXPLANATION", feedback.reasonCode());
    assertEquals(MlAdvisoryControlService.CAPTURE_ADVISORY_FEEDBACK_ENDPOINT,
        "POST /api/v1/tenants/{tenantId}/ml-advisory/advisories/{advisoryId}/feedback");
    assertTrue(service.feedbackForTenant(card.tenantId()).stream().anyMatch(saved -> saved.reasonCode().equals("ACTIONABLE_EXPLANATION")));
  }
}
