package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TermAmortizationProfileTest {
  @Test
  void rejectsArmWithoutAdjustmentPeriod() {
    assertThatThrownBy(() -> TermAmortizationProfilePolicy.validateDraft(new TermAmortizationDraftRequest(
        "ARM_7_6", "7/6 SOFR ARM", 360, "ARM", false, false, "SOFR_30_DAY_AVG", 84, null, 45, new BigDecimal("12.5"), Instant.parse("2026-01-01T00:00:00Z"), null), false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_ARM_STRUCTURE");
  }

  @Test
  void rejectsFixedWithArmIndex() {
    assertThatThrownBy(() -> TermAmortizationProfilePolicy.validateDraft(new TermAmortizationDraftRequest(
        "FIXED_30YR", "30 Year Fixed", 360, "FIXED", false, false, "SOFR_30_DAY_AVG", null, null, 0, BigDecimal.ZERO, Instant.parse("2026-01-01T00:00:00Z"), null), false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_FIXED_STRUCTURE");
  }

  @Test
  void acceptsStoryCitedFixedAndArmProfiles() {
    TermAmortizationProfilePolicy.validateDraft(new TermAmortizationDraftRequest(
        "FIXED_30YR", "30 Year Fixed", 360, "FIXED", false, false, null, null, null, 0, BigDecimal.ZERO, Instant.parse("2026-01-01T00:00:00Z"), null), false);
    TermAmortizationProfilePolicy.validateDraft(new TermAmortizationDraftRequest(
        "ARM_7_6", "7/6 SOFR ARM", 360, "ARM", false, false, "SOFR_30_DAY_AVG", 84, 6, 45, new BigDecimal("12.5"), Instant.parse("2026-01-01T00:00:00Z"), null), false);
  }
}
