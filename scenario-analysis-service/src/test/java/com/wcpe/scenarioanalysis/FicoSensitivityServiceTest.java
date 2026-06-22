package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.FicoSensitivityService.FicoSensitivityCommand;
import com.wcpe.scenarioanalysis.FicoSensitivityService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.FicoSensitivityService.PolicyNotSatisfiedException;
import com.wcpe.scenarioanalysis.FicoSensitivityService.SourceFicoRequiredException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FicoSensitivityServiceTest {
  private TestOnlyInMemoryFicoSensitivityRepository repository;
  private FicoSensitivityService service;

  @BeforeEach
  void setUp() {
    repository = new TestOnlyInMemoryFicoSensitivityRepository();
    service = new FicoSensitivityService(
        repository,
        Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void createsCompletedRunWithSortedRowsAndSyntheticOverrideWarnings() {
    var response = service.createRun(validCommand("idem-001", List.of(740, 680, 740)));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.sensitivityAxis()).isEqualTo("FICO");
    assertThat(response.baselineFico()).isEqualTo(720);
    assertThat(response.rows()).extracting(row -> row.fico()).containsExactly(680, 740);
    assertThat(response.rows()).extracting(row -> row.deltas().ficoDelta()).containsExactly(-40, 20);
    assertThat(response.rows()).allSatisfy(row -> {
      assertThat(row.eligibility()).isEqualTo("NOT_PRICED");
      assertThat(row.ruleHits()).contains("pricing_client_synthetic_fico_override_unavailable");
    });
    assertThat(response.validationMessages()).contains("duplicate FICO scores were collapsed");
    assertThat(response.resultHash()).startsWith("sha256:");
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void replaysSameCreateResponseForDuplicateIdempotencyKey() {
    var first = service.createRun(validCommand("idem-001", List.of(680, 740)));
    var replay = service.createRun(validCommand("idem-001", List.of(680, 740)));

    assertThat(replay).isEqualTo(first);
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void duplicateIdempotencyKeyWithDifferentRequestIsConflict() {
    service.createRun(validCommand("idem-001", List.of(680, 740)));

    assertThatThrownBy(() -> service.createRun(validCommand("idem-001", List.of(700, 740))))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("idempotency key was already used with a different FICO sensitivity request");
  }

  @Test
  void missingSourceFicoReturnsSourceFicoRequiredDomainError() {
    assertThatThrownBy(() -> service.createRun(new FicoSensitivityCommand(
        "tenant-001",
        "quote-123",
        3,
        null,
        null,
        List.of(680, 740),
        true,
        Instant.parse("2026-06-01T12:00:00Z"),
        "idem-001",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(SourceFicoRequiredException.class)
        .hasMessage("source quote representative FICO is required");
  }

  @Test
  void missingScoresFailsClosedForConfiguredLadder() {
    assertThatThrownBy(() -> service.createRun(validCommand("idem-001", List.of())))
        .isInstanceOf(PolicyNotSatisfiedException.class)
        .hasMessage("tenant FICO ladder configuration is required when scores are not supplied");
  }

  private static FicoSensitivityCommand validCommand(String idempotencyKey, List<Integer> scores) {
    return new FicoSensitivityCommand(
        "tenant-001",
        "quote-123",
        3,
        720,
        null,
        scores,
        true,
        Instant.parse("2026-06-01T12:00:00Z"),
        idempotencyKey,
        "actor-001",
        "corr-001",
        "cause-001");
  }
}
