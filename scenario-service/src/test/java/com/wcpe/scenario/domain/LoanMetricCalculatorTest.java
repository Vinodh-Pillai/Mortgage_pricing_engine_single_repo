package com.wcpe.scenario.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoanMetricCalculatorTest {
  private final LoanMetricCalculator calculator = new LoanMetricCalculator();

  @Test
  void calculatesPurchaseLtvCltvHcltv() {
    LoanMetricResult result = calculator.calculate(new LoanStructureRequest(1, "PURCHASE", new BigDecimal("400000.00"),
        "FIRST", 360, "FIXED", new BigDecimal("50000.00"), new BigDecimal("10000.00"),
        new BigDecimal("25000.00"), 30, new BigDecimal("500000.00")));

    assertThat(result.qualityStatus()).isEqualTo("COMPLETE");
    assertThat(result.metrics()).extracting(LoanMetric::metricCode).containsExactly("LTV", "CLTV", "HCLTV");
    assertThat(metric(result, "LTV").ratioValue()).isEqualByComparingTo("0.80000");
    assertThat(metric(result, "CLTV").ratioValue()).isEqualByComparingTo("0.92000");
    assertThat(metric(result, "HCLTV").ratioValue()).isEqualByComparingTo("0.95000");
    assertThat(metric(result, "LTV").bpsValue()).isEqualByComparingTo("8000.0000");
  }

  @Test
  void rejectsZeroDenominator() {
    LoanMetricResult result = calculator.calculate(new LoanStructureRequest(1, "PURCHASE", new BigDecimal("400000.00"),
        "FIRST", 360, "FIXED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 30, BigDecimal.ZERO));

    assertThat(result.qualityStatus()).isEqualTo("BLOCKED");
    assertThat(result.issues()).extracting(ValidationIssue::code).contains("MISSING_LTV_DENOMINATOR");
    assertThat(metric(result, "LTV").ratioValue()).isNull();
  }

  @Test
  void rejectsCltvLowerThanLtvWithoutExplanation() {
    LoanMetricResult result = calculator.calculate(new LoanStructureRequest(1, "PURCHASE", new BigDecimal("400000.00"),
        "FIRST", 360, "FIXED", BigDecimal.ZERO, new BigDecimal("20000.00"), new BigDecimal("10000.00"),
        30, new BigDecimal("500000.00")));

    assertThat(result.qualityStatus()).isEqualTo("BLOCKED");
    assertThat(result.issues()).extracting(ValidationIssue::code).contains("INVALID_COMBINED_LTV");
  }

  private static LoanMetric metric(LoanMetricResult result, String code) {
    return result.metrics().stream().filter(m -> code.equals(m.metricCode())).findFirst().orElseThrow();
  }
}
