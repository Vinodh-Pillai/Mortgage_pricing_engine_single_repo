package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeedbackAggregationIT {
  @Test
  void shouldAggregateByModelVersionAndConfidenceBand() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();
    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-feedback-aggregate-base", 0.91)).value().orElseThrow();

    service.captureAdvisoryFeedback(FeedbackCaptureServiceTest.command(card, "idem-feedback-aggregate-1"));

    List<FeedbackAggregate> aggregates =
        service.feedbackAggregates(new FeedbackAggregateQuery(card.tenantId(), card.modelVersionId(), card.advisoryType(), null, null));

    assertEquals(1, aggregates.size());
    assertEquals(card.modelVersionId(), aggregates.get(0).modelVersionId());
    assertEquals(card.confidenceBand(), aggregates.get(0).confidenceBand());
    assertEquals(1, aggregates.get(0).notUsefulCount());
  }
}
