package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PricingUiAdvisoryPanelTest {
  @Test
  void shouldRenderAdvisorySeparateFromDeterministicPrice() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();

    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-ui-panel", 0.88)).value().orElseThrow();

    assertEquals("READY_EXPANDED", card.panelState());
    assertTrue(card.accessibilityLabel().startsWith("ML Advisory"));
    assertEquals("Advisory only — does not change final pricing or eligibility.", card.disclaimer());
  }
}
