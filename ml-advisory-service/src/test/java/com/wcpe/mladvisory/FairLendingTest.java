package com.wcpe.mladvisory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.mladvisory.FairLendingAnalysisService.AIRTable;
import com.wcpe.mladvisory.FairLendingAnalysisService.ControlVariable;
import com.wcpe.mladvisory.FairLendingAnalysisService.FairLendingAnalysisRequest;
import com.wcpe.mladvisory.FairLendingAnalysisService.FairLendingReport;
import com.wcpe.mladvisory.FairLendingAnalysisService.OutcomeMeasure;
import com.wcpe.mladvisory.FairLendingAnalysisService.PricingOutcome;
import com.wcpe.mladvisory.FairLendingAnalysisService.ProtectedClass;
import com.wcpe.mladvisory.FairLendingAnalysisService.RegressionResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FairLendingTest {
  private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

  @Test
  void airCalculation() {
    AIRTable table = FairLendingAnalysisService.calculateAirTable(sampleOutcomes(), OutcomeMeasure.NOTE_RATE, ProtectedClass.RACE, 5.5d);

    assertEquals("WHITE", table.referenceGroup());
    assertEquals(50, table.favorableCounts().get("WHITE"));
    assertEquals(20, table.favorableCounts().get("BLACK"));
    assertEquals(0.4d, table.airRatios().get("BLACK"), 0.01d);
  }

  @Test
  void fourFifthsRule() {
    AIRTable table = FairLendingAnalysisService.calculateAirTable(sampleOutcomes(), OutcomeMeasure.NOTE_RATE, ProtectedClass.RACE, 5.5d);

    assertTrue(table.fourFifthsViolation());
    assertTrue(table.airRatios().get("BLACK") < FairLendingAnalysisService.FOUR_FIFTHS_THRESHOLD);
  }

  @Test
  void regressionSignificance() {
    FairLendingAnalysisService service = new FairLendingAnalysisService();

    RegressionResult result = service.runProtectedClassRegression(regressionOutcomes(), OutcomeMeasure.NOTE_RATE, ProtectedClass.RACE, List.of());

    assertTrue(result.significantDisparity());
    assertTrue(result.pValues().get("BLACK") < FairLendingAnalysisService.REGRESSION_P_VALUE_THRESHOLD);
    assertTrue(result.coefficients().get("BLACK") > 0.9d);
  }

  @Test
  void marginalEffect() {
    FairLendingAnalysisService service = loadedService();

    FairLendingReport report = service.analyze(new FairLendingAnalysisRequest(TENANT, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), List.of(ProtectedClass.RACE), List.of(OutcomeMeasure.NOTE_RATE), List.of(ControlVariable.FICO), 0.50d));

    assertTrue(report.violations().stream().anyMatch(v -> "MARGINAL_EFFECT".equals(v.violationType()) && "BLACK".equals(v.group())));
  }

  @Test
  void multipleTestingCorrection() {
    FairLendingAnalysisService service = loadedService();

    FairLendingReport report = service.analyze(new FairLendingAnalysisRequest(TENANT, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), List.of(ProtectedClass.RACE, ProtectedClass.SEX), List.of(OutcomeMeasure.NOTE_RATE), List.of(ControlVariable.FICO), 0.50d));

    assertFalse(report.regressionResults().isEmpty());
    assertTrue(report.regressionResults().stream().allMatch(result -> result.pValues().values().stream().allMatch(value -> value >= 0.0d && value <= 1.0d)));
  }

  @Test
  void insufficientSampleSize() {
    AIRTable table = FairLendingAnalysisService.calculateAirTable(sampleOutcomes().subList(0, 20), OutcomeMeasure.NOTE_RATE, ProtectedClass.RACE, 5.5d);

    assertFalse(table.fourFifthsViolation());
    assertTrue(table.dataQualityFlags().stream().anyMatch(flag -> flag.contains("insufficient sample")));
  }

  @Test
  void pricingOutcomeToAnalysisToViolationAlert() {
    FairLendingAnalysisService service = loadedService();

    FairLendingReport report = service.analyze(new FairLendingAnalysisRequest(TENANT, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), List.of(ProtectedClass.RACE), List.of(OutcomeMeasure.NOTE_RATE), List.of(ControlVariable.FICO), 0.50d));

    assertFalse(report.violations().isEmpty());
    assertTrue(service.outboxEvents().stream().anyMatch(event -> FairLendingAnalysisService.PRICING_OUTCOME_RECORDED_EVENT.equals(event.eventType())));
    assertTrue(service.outboxEvents().stream().anyMatch(event -> FairLendingAnalysisService.VIOLATION_DETECTED_EVENT.equals(event.eventType())));
  }

  private static FairLendingAnalysisService loadedService() {
    FairLendingAnalysisService service = new FairLendingAnalysisService();
    regressionOutcomes().forEach(service::recordPricingOutcome);
    return service;
  }

  private static List<PricingOutcome> sampleOutcomes() {
    java.util.ArrayList<PricingOutcome> rows = new java.util.ArrayList<>();
    for (int i = 0; i < 60; i++) rows.add(outcome("WHITE", "MALE", i < 50 ? 5.0d : 7.0d, i));
    for (int i = 0; i < 60; i++) rows.add(outcome("BLACK", "FEMALE", i < 20 ? 5.0d : 7.0d, i + 60));
    return rows;
  }

  private static List<PricingOutcome> regressionOutcomes() {
    java.util.ArrayList<PricingOutcome> rows = new java.util.ArrayList<>();
    for (int i = 0; i < 80; i++) rows.add(outcome("WHITE", "MALE", 5.0d + (i % 5) * 0.01d, i));
    for (int i = 0; i < 80; i++) rows.add(outcome("BLACK", "FEMALE", 6.1d + (i % 5) * 0.01d, i + 80));
    return rows;
  }

  private static PricingOutcome outcome(String race, String sex, double noteRate, int index) {
    return new PricingOutcome(UUID.nameUUIDFromBytes((race + sex + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)), TENANT, UUID.nameUUIDFromBytes(("run" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)), null, null, race, "NON_HISPANIC", sex, 40 + (index % 35), null, null, null, 660 + (index % 80), 75.0d + (index % 10), 32.0d + (index % 6), 250000.0d + index, "PURCHASE", "SFR", "PRIMARY", "CA", "RETAIL", "CONVENTIONAL", "INVESTOR_A", noteRate, 101.0d - noteRate, (int) Math.round((noteRate - 5.0d) * 100.0d), (int) Math.round((noteRate - 4.0d) * 100.0d), 30, Instant.parse("2026-06-15T00:00:00Z"), Instant.parse("2026-06-15T00:00:00Z"));
  }
}
