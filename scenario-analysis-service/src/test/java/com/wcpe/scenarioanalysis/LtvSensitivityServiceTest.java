package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.LtvSensitivityService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.LtvSensitivityService.InMemoryLtvSensitivityRepository;
import com.wcpe.scenarioanalysis.LtvSensitivityService.LtvSensitivityCommand;
import com.wcpe.scenarioanalysis.LtvSensitivityService.LtvSensitivityMode;
import com.wcpe.scenarioanalysis.LtvSensitivityService.PolicyNotSatisfiedException;
import com.wcpe.scenarioanalysis.LtvSensitivityService.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LtvSensitivityServiceTest {
  private InMemoryLtvSensitivityRepository repository;
  private LtvSensitivityService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryLtvSensitivityRepository();
    service = new LtvSensitivityService(
        repository,
        Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void purchaseUsesLesserOfPriceAndValue() {
    var response = service.createRun(validCommand(
        "idem-001",
        LtvSensitivityMode.TARGET_LTV,
        List.of(new BigDecimal("80"), new BigDecimal("95"))));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.sensitivityAxis()).isEqualTo("LTV_DOWN_PAYMENT");
    assertThat(response.baselineLtv()).isEqualByComparingTo("0.81250");
    assertThat(response.resultSummary().propertyValueUsed()).isEqualByComparingTo("400000.00");
    assertThat(response.rows()).hasSize(2);
    assertThat(response.rows().get(0).loanAmount()).isEqualByComparingTo("320000.00");
    assertThat(response.rows().get(0).downPaymentAmount()).isEqualByComparingTo("80000.00");
    assertThat(response.rows().get(0).targetLtv()).isEqualByComparingTo("0.80000");
    assertThat(response.rows().get(1).loanAmount()).isEqualByComparingTo("380000.00");
    assertThat(response.rows().get(1).crossedThresholds()).extracting(crossing -> crossing.crossedThreshold())
        .contains("85%", "90%", "95%");
    assertThat(response.rows()).allSatisfy(row -> {
      assertThat(row.eligibility()).isEqualTo("NOT_PRICED");
      assertThat(row.ruleHits()).contains("pricing_client_ltv_sensitivity_unavailable");
      assertThat(row.resultHash()).startsWith("sha256:");
    });
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void downPaymentVariantRejectsNegativeCash() {
    assertThatThrownBy(() -> service.createRun(validCommand(
        "idem-001",
        LtvSensitivityMode.DOWN_PAYMENT_AMOUNT,
        List.of(new BigDecimal("-1.00")))))
        .isInstanceOf(ValidationException.class)
        .hasMessage("down payment amount cannot be negative");
  }

  @Test
  void thresholdCrossingTestFlagsEightyPercentBoundary() {
    var response = service.createRun(validCommand(
        "idem-001",
        LtvSensitivityMode.LOAN_AMOUNT,
        List.of(new BigDecimal("315000.00"), new BigDecimal("321000.00"))));

    assertThat(response.rows().get(0).targetLtv()).isEqualByComparingTo("0.78750");
    assertThat(response.rows().get(1).targetLtv()).isEqualByComparingTo("0.80250");
    assertThat(response.rows().get(0).crossedThresholds()).extracting(crossing -> crossing.crossedThreshold())
        .containsExactly("80%");
  }

  @Test
  void ltvMiUnavailableReturnsRowsWithMiUnavailableWarning() {
    var response = service.createRun(validCommand(
        "idem-001",
        LtvSensitivityMode.TARGET_LTV,
        List.of(new BigDecimal("80"))));

    assertThat(response.rows().get(0).mi().status()).isEqualTo("UNAVAILABLE");
    assertThat(response.rows().get(0).mi().warningCode()).isEqualTo("MI_UNAVAILABLE");
    assertThat(response.resultSummary().miUnavailableCount()).isEqualTo(1);
  }

  @Test
  void replaysSameCreateResponseForDuplicateIdempotencyKey() {
    var first = service.createRun(validCommand("idem-001", LtvSensitivityMode.TARGET_LTV, List.of(new BigDecimal("80"))));
    var replay = service.createRun(validCommand("idem-001", LtvSensitivityMode.TARGET_LTV, List.of(new BigDecimal("80"))));

    assertThat(replay).isEqualTo(first);
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void duplicateIdempotencyKeyWithDifferentRequestIsConflict() {
    service.createRun(validCommand("idem-001", LtvSensitivityMode.TARGET_LTV, List.of(new BigDecimal("80"))));

    assertThatThrownBy(() -> service.createRun(validCommand("idem-001", LtvSensitivityMode.TARGET_LTV, List.of(new BigDecimal("85")))))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("idempotency key was already used with a different LTV sensitivity request");
  }

  @Test
  void missingValuesFailsClosedForConfiguredTenantGrid() {
    assertThatThrownBy(() -> service.createRun(validCommand("idem-001", LtvSensitivityMode.TARGET_LTV, List.of())))
        .isInstanceOf(PolicyNotSatisfiedException.class)
        .hasMessage("tenant LTV sensitivity value configuration is required when values are not supplied");
  }

  private static LtvSensitivityCommand validCommand(
      String idempotencyKey,
      LtvSensitivityMode mode,
      List<BigDecimal> values) {
    return new LtvSensitivityCommand(
        "tenant-001",
        "quote-123",
        3,
        mode,
        values,
        new BigDecimal("410000.00"),
        new BigDecimal("400000.00"),
        new BigDecimal("325000.00"),
        new BigDecimal("10000.00"),
        true,
        Instant.parse("2026-06-01T12:00:00Z"),
        idempotencyKey,
        "actor-001",
        "corr-001",
        "cause-001");
  }
}
