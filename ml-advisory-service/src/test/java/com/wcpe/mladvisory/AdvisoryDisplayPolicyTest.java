package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdvisoryDisplayPolicyTest {
  @Test
  void shouldCollapseLowConfidenceAdvice() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();

    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-low-confidence", 0.37)).value().orElseThrow();

    assertTrue(card.collapsedByDefault());
    assertEquals("LOW_CONFIDENCE_COLLAPSED", card.panelState());
    assertEquals(MlAdvisoryControlService.NON_AUTHORITATIVE_DISCLAIMER, card.disclaimer());
  }
}
