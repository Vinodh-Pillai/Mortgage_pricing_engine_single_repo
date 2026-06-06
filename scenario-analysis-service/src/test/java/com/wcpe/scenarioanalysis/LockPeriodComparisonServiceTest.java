package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.LockPeriodComparisonService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.LockPeriodComparisonService.InMemoryLockPeriodComparisonRepository;
import com.wcpe.scenarioanalysis.LockPeriodComparisonService.LockPeriodComparisonCommand;
import com.wcpe.scenarioanalysis.LockPeriodComparisonService.PolicyNotSatisfiedException;
import com.wcpe.scenarioanalysis.LockPeriodComparisonService.PricingVersionStaleException;
import com.wcpe.scenarioanalysis.LockPeriodComparisonService.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LockPeriodComparisonServiceTest {
  private InMemoryLockPeriodComparisonRepository repository;
  private LockPeriodComparisonService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryLockPeriodComparisonRepository();
    service = new LockPeriodComparisonService(
        repository,
        Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void lockExpirationCalculatorSkipsTenantHolidays() {
    LocalDate expiration = LockPeriodComparisonService.calculateExpirationDate(
        LocalDate.parse("2026-06-01"),
        4,
        Set.of(LocalDate.parse("2026-06-05")));

    assertThat(expiration).isEqualTo(LocalDate.parse("2026-06-08"));
  }

  @Test
  void createsRowsWithoutCreatingRealLockWhenDependenciesAreUnavailable() {
    var response = service.createRun(validCommand("idem-001", List.of(60, 30, 45), true));

    assertThat(response.status()).isEqualTo("COMPLETED_WITH_DEPENDENCY_GAPS");
    assertThat(response.sensitivityAxis()).isEqualTo("LOCK_PERIOD");
    assertThat(response.baselineVariantId()).isEqualTo("source-quote:quote-123:v3");
    assertThat(response.rows()).extracting(row -> row.lockPeriodDays()).containsExactly(30, 45, 60);
    assertThat(response.rows()).allSatisfy(row -> {
      assertThat(row.eligibility()).isEqualTo("INELIGIBLE");
      assertThat(row.adjustmentBps()).isNull();
      assertThat(row.price().status()).isEqualTo("UNAVAILABLE");
      assertThat(row.extensionEstimate().status()).isEqualTo("UNAVAILABLE");
      assertThat(row.ruleHits()).contains(
          "lock_policy_config_unavailable",
          "holiday_calendar_version_unavailable",
          "pricing_client_lock_adjustment_unavailable",
          "extension_fee_dependency_unavailable");
      assertThat(row.resultHash()).startsWith("sha256:");
    });
    assertThat(response.resultSummary().disclaimer()).contains("No real lock is created or committed");
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void lockComparisonPolicyTestRejectsUnavailablePeriod() {
    assertThatThrownBy(() -> service.createRun(validCommand("idem-001", List.of(0), false)))
        .isInstanceOf(ValidationException.class)
        .hasMessage("lockPeriodDays must be positive");
  }

  @Test
  void missingLockPeriodsFailsClosedForTenantConfig() {
    assertThatThrownBy(() -> service.createRun(validCommand("idem-001", List.of(), false)))
        .isInstanceOf(PolicyNotSatisfiedException.class)
        .hasMessage("tenant lock period configuration is required when lock periods are not supplied");
  }

  @Test
  void expiredPricingBusinessDateIsConflict() {
    assertThatThrownBy(() -> service.createRun(new LockPeriodComparisonCommand(
        "tenant-001",
        "quote-123",
        3,
        null,
        List.of(30),
        LocalDate.parse("2026-05-31"),
        false,
        Instant.parse("2026-06-01T12:00:00Z"),
        "idem-001",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(PricingVersionStaleException.class)
        .hasMessage("lockStartDate cannot be before pricing business date");
  }

  @Test
  void replaysSameCreateResponseForDuplicateIdempotencyKey() {
    var first = service.createRun(validCommand("idem-001", List.of(30, 45), false));
    var replay = service.createRun(validCommand("idem-001", List.of(30, 45), false));

    assertThat(replay).isEqualTo(first);
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void duplicateIdempotencyKeyWithDifferentRequestIsConflict() {
    service.createRun(validCommand("idem-001", List.of(30), false));

    assertThatThrownBy(() -> service.createRun(validCommand("idem-001", List.of(45), false)))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("idempotency key was already used with a different lock period comparison request");
  }

  @Test
  void configEndpointReturnsNoInventedLockPeriods() {
    var response = service.getConfig("tenant-001", "conv30", "investor-a", "retail");

    assertThat(response.lockPeriods()).isEmpty();
    assertThat(response.dependencyStatus()).isEqualTo("LOCK_POLICY_CONFIG_UNAVAILABLE");
    assertThat(response.message()).contains("no default lock days are assumed");
  }

  private static LockPeriodComparisonCommand validCommand(
      String idempotencyKey,
      List<Integer> lockPeriods,
      boolean includeExtensionEstimate) {
    return new LockPeriodComparisonCommand(
        "tenant-001",
        "quote-123",
        3,
        null,
        lockPeriods,
        LocalDate.parse("2026-06-01"),
        includeExtensionEstimate,
        Instant.parse("2026-06-01T12:00:00Z"),
        idempotencyKey,
        "actor-001",
        "corr-001",
        "cause-001");
  }
}
