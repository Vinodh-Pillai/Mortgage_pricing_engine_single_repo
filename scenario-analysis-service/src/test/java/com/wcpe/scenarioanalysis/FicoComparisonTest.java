package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.wcpe.scenarioanalysis.FicoSensitivityService.FicoSensitivityDeltas;
import com.wcpe.scenarioanalysis.FicoSensitivityService.FicoSensitivityRow;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FicoComparisonTest {
  @Test
  void computesDeltaFromBaseline() {
    var rows = List.of(
        new FicoSensitivityRow(UUID.randomUUID(), 680, "FICO_680", "NOT_PRICED", deltas(-40), List.of(), "hash-680"),
        new FicoSensitivityRow(UUID.randomUUID(), 740, "FICO_740", "NOT_PRICED", deltas(20), List.of(), "hash-740"));

    var summary = FicoSensitivityService.summarize(720, rows);

    assertThat(rows).extracting(row -> row.deltas().ficoDelta()).containsExactly(-40, 20);
    assertThat(summary.firstEligibleScoreAboveBaseline()).isEqualTo(740);
    assertThat(summary.disclaimer()).contains("Pricing deltas are unavailable");
  }

  private static FicoSensitivityDeltas deltas(int ficoDelta) {
    return new FicoSensitivityDeltas(ficoDelta, null, null, null, null, null);
  }
}
