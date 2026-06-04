package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FicoOverrideValidatorTest {
  @Test
  void rejectsScoreBelow300Above850() {
    assertThatThrownBy(() -> FicoSensitivityService.validateFico(299))
        .isInstanceOf(FicoSensitivityService.ValidationException.class)
        .hasMessage("FICO score must be between 300 and 850");
    assertThatThrownBy(() -> FicoSensitivityService.validateFico(851))
        .isInstanceOf(FicoSensitivityService.ValidationException.class)
        .hasMessage("FICO score must be between 300 and 850");
    assertThatCode(() -> FicoSensitivityService.validateFico(300)).doesNotThrowAnyException();
    assertThatCode(() -> FicoSensitivityService.validateFico(850)).doesNotThrowAnyException();
  }
}
