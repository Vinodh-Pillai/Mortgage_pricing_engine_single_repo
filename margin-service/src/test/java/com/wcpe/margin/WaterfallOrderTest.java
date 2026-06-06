package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class WaterfallOrderTest {
  @Test
  void marginCompStepsInRequiredOrder() {
    MarginReplayService.assertRequiredWaterfallOrder(MarginReplayTestFixtures.fullStackManifest().waterfallSteps());
  }

  @Test
  void rejectsMissingRequiredMarginCompStep() {
    var steps = new ArrayList<>(MarginReplayTestFixtures.fullStackManifest().waterfallSteps());
    steps.removeIf(step -> "BRANCH_OVERLAY".equals(step.stepType()));

    assertThrows(MarginReplayService.MarginReplayException.class,
        () -> MarginReplayService.assertRequiredWaterfallOrder(steps));
  }
}
