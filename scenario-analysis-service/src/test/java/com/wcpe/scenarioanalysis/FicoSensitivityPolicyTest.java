package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FicoSensitivityPolicyTest {
  @Test
  void buildsConfiguredBucketLadder() {
    assertThat(FicoSensitivityService.collapseScores(List.of(740, 680, 740, 720)))
        .containsExactly(740, 680, 720);
  }
}
