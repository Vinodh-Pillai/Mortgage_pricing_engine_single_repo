package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.BatchSensitivityGridService.AxisType;
import com.wcpe.scenarioanalysis.BatchSensitivityGridService.BatchGridAxis;
import com.wcpe.scenarioanalysis.BatchSensitivityGridService.BatchGridCommand;
import com.wcpe.scenarioanalysis.BatchSensitivityGridService.CellLimitExceededException;
import com.wcpe.scenarioanalysis.BatchSensitivityGridService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.BatchSensitivityGridService.InMemoryBatchGridRepository;
import com.wcpe.scenarioanalysis.BatchSensitivityGridService.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchSensitivityGridServiceTest {
  private InMemoryBatchGridRepository repository;
  private BatchSensitivityGridService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryBatchGridRepository();
    service = new BatchSensitivityGridService(
        repository,
        Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void batchGridCommandTestRejectsDuplicateAxisValues() {
    var command = validCommand("idem-duplicate-axis", List.of(
        new BatchGridAxis(AxisType.FICO, List.of("680", "700")),
        new BatchGridAxis(AxisType.FICO, List.of("720"))));

    assertThatThrownBy(() -> service.createGrid(command))
        .isInstanceOf(ValidationException.class)
        .hasMessage("duplicate axis type FICO is not allowed");
  }

  @Test
  void batchCellVariantFactoryTestAppliesAxesInCanonicalOrder() {
    var response = service.createGrid(validCommand("idem-canonical", List.of(
        new BatchGridAxis(AxisType.FICO, List.of("680")),
        new BatchGridAxis(AxisType.LTV, List.of("80")),
        new BatchGridAxis(AxisType.LOCK_PERIOD, List.of("30")))));

    assertThat(response.status()).isEqualTo("QUEUED");
    assertThat(response.cells()).hasSize(1);
    assertThat(response.cells().get(0).variantOverrides().keySet())
        .containsExactly("LOCK_PERIOD", "LTV", "FICO");
    assertThat(response.resultSummary().queuedCount()).isEqualTo(1);
  }

  @Test
  void batchGridWorkerITPricesCellsWithTenantRateLimitBlockedByMissingDependencyConfig() {
    var created = service.createGrid(validCommand("idem-worker", List.of(
        new BatchGridAxis(AxisType.FICO, List.of("680", "700")),
        new BatchGridAxis(AxisType.LTV, List.of("75", "80")))));

    var completed = service.runQueuedCells("tenant-001", created.gridId());

    assertThat(completed.status()).isEqualTo("FAILED");
    assertThat(completed.cells()).hasSize(4);
    assertThat(completed.cells()).allSatisfy(cell -> {
      assertThat(cell.status()).isEqualTo("FAILED");
      assertThat(cell.errorCode()).isEqualTo("PRICING_DEPENDENCY_UNAVAILABLE");
      assertThat(cell.ruleHits()).contains("pricing_dependency_unavailable");
      assertThat(cell.resultHash()).startsWith("sha256:");
    });
    assertThat(completed.validationMessages()).contains("pricing dependency unavailable; cells failed closed without invented pricing economics");
  }

  @Test
  void batchGridPauseResumeITDoesNotStartPausedCells() {
    var created = service.createGrid(validCommand("idem-pause", List.of(
        new BatchGridAxis(AxisType.FICO, List.of("680")),
        new BatchGridAxis(AxisType.LTV, List.of("75")))));

    var paused = service.pauseGrid("tenant-001", created.gridId());
    var resumed = service.resumeGrid("tenant-001", created.gridId());

    assertThat(paused.status()).isEqualTo("PAUSED");
    assertThat(paused.cells()).allSatisfy(cell -> assertThat(cell.status()).isEqualTo("PAUSED"));
    assertThat(resumed.status()).isEqualTo("QUEUED");
    assertThat(resumed.cells()).allSatisfy(cell -> assertThat(cell.status()).isEqualTo("QUEUED"));
  }

  @Test
  void batchGridRetryITRetriesOnlyFailedCells() {
    var created = service.createGrid(validCommand("idem-retry", List.of(
        new BatchGridAxis(AxisType.FICO, List.of("680")),
        new BatchGridAxis(AxisType.LTV, List.of("75")))));
    service.runQueuedCells("tenant-001", created.gridId());

    var retried = service.retryFailed("tenant-001", created.gridId());

    assertThat(retried.status()).isEqualTo("QUEUED");
    assertThat(retried.cells()).allSatisfy(cell -> assertThat(cell.status()).isEqualTo("QUEUED"));
    assertThat(retried.resultSummary().queuedCount()).isEqualTo(1);
  }

  @Test
  void gridDeltaMatrixTestComputesBaselineCellDeltasAsUnavailableUntilPricingExists() {
    var created = service.createGrid(validCommand("idem-delta", List.of(
        new BatchGridAxis(AxisType.FICO, List.of("680")),
        new BatchGridAxis(AxisType.LTV, List.of("75")))));

    assertThat(created.cells().get(0).ruleHits()).isEmpty();
    assertThat(service.runQueuedCells("tenant-001", created.gridId()).cells().get(0).errorCode())
        .isEqualTo("PRICING_DEPENDENCY_UNAVAILABLE");
  }

  @Test
  void gridExceedingRequestedMaxCellsFailsClosedWithoutInventingTenantLimit() {
    var command = new BatchGridCommand(
        "tenant-001",
        "quote-123",
        3,
        "too-large",
        List.of(new BatchGridAxis(AxisType.FICO, List.of("680", "700")), new BatchGridAxis(AxisType.LTV, List.of("75", "80"))),
        true,
        3,
        Instant.parse("2026-06-01T12:00:00Z"),
        "idem-too-large",
        "actor-001",
        "corr-001",
        "cause-001");

    assertThatThrownBy(() -> service.createGrid(command))
        .isInstanceOf(CellLimitExceededException.class)
        .hasMessage("grid cell count 4 exceeds requested maxCells 3");
  }

  @Test
  void duplicateIdempotencyKeyWithDifferentGridIsConflict() {
    service.createGrid(validCommand("idem-conflict", List.of(
        new BatchGridAxis(AxisType.FICO, List.of("680")),
        new BatchGridAxis(AxisType.LTV, List.of("75")))));

    assertThatThrownBy(() -> service.createGrid(validCommand("idem-conflict", List.of(
        new BatchGridAxis(AxisType.FICO, List.of("700")),
        new BatchGridAxis(AxisType.LTV, List.of("75"))))))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("idempotency key was already used with a different batch sensitivity grid request");
    assertThat(repository.size()).isEqualTo(1);
  }

  private static BatchGridCommand validCommand(String idempotencyKey, List<BatchGridAxis> axes) {
    return new BatchGridCommand(
        "tenant-001",
        "quote-123",
        3,
        "fico-ltv-grid",
        axes,
        true,
        25,
        Instant.parse("2026-06-01T12:00:00Z"),
        idempotencyKey,
        "actor-001",
        "corr-001",
        "cause-001");
  }
}
