package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MlAdvisoryExplanationViewedEventTest {
  @Test
  void shouldRecordViewWithoutDriverValues() {
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
                "corr-explanation-viewed-event")
            .value()
            .orElseThrow();

    MlAdvisoryOutboxEvent event =
        service.outboxEvents().stream()
            .filter(candidate -> candidate.eventType().equals(MlAdvisoryControlService.ML_ADVISORY_EXPLANATION_VIEWED_EVENT))
            .findFirst()
            .orElseThrow();

    assertEquals(explanation.explanationId(), event.payload().get("explanationId"));
    assertEquals("false", event.payload().get("authoritative"));
    assertFalse(event.payload().toString().contains("720"));
    assertFalse(event.payload().toString().contains("raw credit score"));
  }
}
