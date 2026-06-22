package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarginReplayApiContractTest {
  @Test
  void exactManifestReplay() {
    var service = MarginServiceTestStores.marginReplayService(MarginReplayTestFixtures.CLOCK);
    service.registerFixture(MarginReplayTestFixtures.TENANT, MarginReplayTestFixtures.fullStackFixture());

    var result = service.runFixtureReplay(
        MarginReplayTestFixtures.fullStackCommand(MarginReplayTestFixtures.fullStackManifest()));

    assertEquals("MATCH", result.matchStatus());
    assertEquals("corr-replay", result.correlationId());
    assertEquals("DecisionReplayed.v1", service.outboxEvents().isEmpty() ? "" : "DecisionReplayed.v1");
    assertEquals(MarginReplayService.DOMAIN, service.outboxEvents().get(0).domain());
  }

  @Test
  void unauthorizedReplayFailsClosed() {
    var service = MarginServiceTestStores.marginReplayService(MarginReplayTestFixtures.CLOCK);
    service.registerFixture(MarginReplayTestFixtures.TENANT, MarginReplayTestFixtures.fullStackFixture());
    var denied = new MarginReplayService.ReplayCommand(MarginReplayTestFixtures.TENANT, "viewer", "quote-a",
        MarginReplayTestFixtures.FIXTURE_ID, MarginReplayService.EXACT_MARGIN_COMP_MANIFEST, List.of(),
        MarginReplayTestFixtures.fullStackManifest(), "corr-denied");

    var exception = assertThrows(MarginReplayService.MarginReplayException.class,
        () -> service.runFixtureReplay(denied));

    assertEquals("REPLAY_UNAUTHORIZED", exception.getMessage());
    assertEquals(1, service.marginReplayUnauthorizedTotal.get());
  }

  @Test
  void roleFilteredReplayResponseRedactsSensitiveFields() {
    var service = MarginServiceTestStores.marginReplayService(MarginReplayTestFixtures.CLOCK);
    service.registerFixture(MarginReplayTestFixtures.TENANT, MarginReplayTestFixtures.fullStackFixture());
    var result = service.runFixtureReplay(
        MarginReplayTestFixtures.fullStackCommand(MarginReplayTestFixtures.fullStackManifest()));

    var filtered = service.applyVisibility("pricing.replay.margin_comp.read", result);

    assertTrue(filtered.roleFilteredQuoteResponse().redactedStepTypes().contains("COMPANY_MARGIN"));
    assertFalse(filtered.roleFilteredQuoteResponse().visibleStepTypes().contains("COMPANY_MARGIN"));
    assertNull(filtered.versionManifest().waterfallSteps().get(2).hashContribution());
  }
}
