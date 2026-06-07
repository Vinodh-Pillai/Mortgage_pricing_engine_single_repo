package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdvisoryBoundaryEnforcerTest {
  @Test
  void shouldAlwaysSetAuthoritativeFalse() {
    MlAdvisoryControlService service = AdvisoryTestFixtures.visibleService();

    AdvisoryCard card = service.generateAdvisory(AdvisoryTestFixtures.command("idem-boundary", 0.82)).value().orElseThrow();

    assertFalse(card.authoritative());
    assertFalse(AdvisoryBoundaryEnforcer.authoritative());
    assertTrue(card.allowedActions().contains(AllowedAction.VIEW));
  }
}
