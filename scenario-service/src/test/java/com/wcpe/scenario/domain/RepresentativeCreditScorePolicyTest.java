package com.wcpe.scenario.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepresentativeCreditScorePolicyTest {

  @Test
  void usesLowestOccupyingBorrowerScore() {
    RepresentativeCreditScorePolicy.RepresentativeCreditResult result = RepresentativeCreditScorePolicy.derive(List.of(
        new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 742, "TRI_MERGE", LocalDate.now()),
        new BorrowerCredit("B2", "CO_BORROWER", true, "AVAILABLE", 718, "TRI_MERGE", LocalDate.now()),
        new BorrowerCredit("B3", "NON_OCCUPANT_CO_BORROWER", false, "AVAILABLE", 650, "TRI_MERGE", LocalDate.now())));

    assertThat(result.score()).isEqualTo(718);
    assertThat(result.rule()).isEqualTo("LOWEST_REPRESENTATIVE_SCORE");
    assertThat(result.qualityStatus()).isEqualTo("COMPLETE");
  }

  @Test
  void excludesFrozenOrNoScoreBorrowersWithTrace() {
    RepresentativeCreditScorePolicy.RepresentativeCreditResult result = RepresentativeCreditScorePolicy.derive(List.of(
        new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 742, "TRI_MERGE", LocalDate.now()),
        new BorrowerCredit("B2", "CO_BORROWER", true, "FROZEN", 610, "TRI_MERGE", LocalDate.now()),
        new BorrowerCredit("B3", "CO_BORROWER", true, "NO_SCORE", null, null, null)));

    assertThat(result.score()).isEqualTo(742);
    assertThat(result.trace()).containsKeys("included", "excluded");
    assertThat(result.trace().get("excluded").toString()).contains("FROZEN_EXCLUDED", "NO_SCORE_EXCLUDED");
  }
}
