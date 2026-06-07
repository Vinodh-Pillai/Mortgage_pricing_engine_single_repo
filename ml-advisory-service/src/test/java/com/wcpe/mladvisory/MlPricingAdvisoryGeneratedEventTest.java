package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class MlPricingAdvisoryGeneratedEventTest {
  @Test
  void shouldExcludeRawBorrowerData() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.pricingServiceWithSnapshot();
    String snapshotId = service.featureSnapshotsForTenant(AdvisoryTestFixtures.TENANT).get(0).snapshotId();
    PricingAdvisoryEvaluation evaluation =
        service
            .evaluatePricingAdvisory(
                AdvisoryTestFixtures.pricingEvaluationCommand("idem-pricing-event", 0.82, snapshotId, false, false, false),
                new FakeLocalModelAdapter())
            .value()
            .orElseThrow();

    MlAdvisoryOutboxEvent event =
        service.outboxEvents().stream()
            .filter(candidate -> candidate.eventType().equals(MlAdvisoryControlService.ML_PRICING_ADVISORY_GENERATED_EVENT))
            .findFirst()
            .orElseThrow();

    assertEquals(evaluation.eventRef(), event.eventId());
    assertEquals("false", event.payload().get("authoritative"));
    assertEquals("true", event.payload().get("deterministicPricingUnchanged"));
    assertFalse(event.payload().toString().contains("raw-borrower-feature-value"));
  }
}
