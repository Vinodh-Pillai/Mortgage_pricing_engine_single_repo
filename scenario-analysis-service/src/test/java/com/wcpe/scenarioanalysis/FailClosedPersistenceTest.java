package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FailClosedPersistenceTest {

  @Test
  void productionDefaultServicesFailClosedWithoutDurableRepositories() {
    assertFailClosed(WhatIfVariantService::new, "what-if variant store");
    assertFailClosed(FicoSensitivityService::new, "FICO sensitivity store");
    assertFailClosed(LtvSensitivityService::new, "LTV sensitivity store");
    assertFailClosed(LockPeriodComparisonService::new, "lock period comparison store");
    assertFailClosed(ProductComparisonService::new, "product comparison store");
    assertFailClosed(BatchSensitivityGridService::new, "batch sensitivity grid store");
    assertFailClosed(SavedWhatIfAnalysisService::new, "saved what-if analysis store");
    assertFailClosed(WhatIfExportService::new, "what-if export store");
    assertFailClosed(WhatIfReplayService::new, "what-if replay store");
    assertFailClosed(WhatIfGuardrailService::new, "what-if guardrail policy store");
  }

  private static void assertFailClosed(Runnable constructor, String storeName) {
    assertThatThrownBy(constructor::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(storeName)
        .hasMessageContaining("durable repository bean")
        .hasMessageContaining("volatile store-of-record is disabled");
  }
}
