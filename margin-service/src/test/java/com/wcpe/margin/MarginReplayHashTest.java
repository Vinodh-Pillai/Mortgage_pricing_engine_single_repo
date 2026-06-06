package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MarginReplayHashTest {
  @Test
  void stableForEquivalentManifest() {
    var first = MarginReplayTestFixtures.fullStackManifest();
    var second = MarginReplayTestFixtures.fullStackManifest();

    assertEquals(MarginReplayService.manifestHash(first), MarginReplayService.manifestHash(second));
    assertEquals(MarginReplayService.resultHash(first.waterfallSteps()),
        MarginReplayService.resultHash(second.waterfallSteps()));
  }
}
