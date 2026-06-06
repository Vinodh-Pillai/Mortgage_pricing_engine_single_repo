package com.wcpe.scenario.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BorrowerCreditValidatorTest {

  @Test
  void rejectsScoreOutsideMortgageRange() {
    assertThatThrownBy(() -> BorrowerCreditValidator.validateScoreRange(851))
        .isInstanceOfSatisfying(ScenarioException.class,
            ex -> assertThat(ex.code()).isEqualTo("CREDIT_SCORE_OUT_OF_RANGE"));
  }

  @Test
  void rejectsDuplicatePrimaryBorrowers() {
    assertThatThrownBy(() -> BorrowerCreditValidator.validateExactlyOnePrimary(List.of(
        new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 742, "TRI_MERGE", LocalDate.now()),
        new BorrowerCredit("B2", "PRIMARY", true, "AVAILABLE", 718, "TRI_MERGE", LocalDate.now()))))
        .isInstanceOfSatisfying(ScenarioException.class,
            ex -> assertThat(ex.code()).isEqualTo("DUPLICATE_PRIMARY_BORROWER"));
  }
}
