package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.WhatIfReplayService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.WhatIfReplayService.ReplayDiff;
import com.wcpe.scenarioanalysis.WhatIfReplayService.ReplayPackage;
import com.wcpe.scenarioanalysis.WhatIfReplayService.ReplayPackageIncompleteException;
import com.wcpe.scenarioanalysis.WhatIfReplayService.ReplayVersionUnavailableException;
import com.wcpe.scenarioanalysis.WhatIfReplayService.VersionRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatIfReplayServiceTest {
  private TestOnlyInMemoryReplayRepository repository;
  private WhatIfReplayService service;

  @BeforeEach
  void setUp() {
    repository = new TestOnlyInMemoryReplayRepository();
    service = new WhatIfReplayService(repository, Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void replayPackageValidatorTestRequiresAllVersionRefs() {
    ReplayPackage incomplete = new ReplayPackage(
        WhatIfReplayService.PACKAGE_SCHEMA_VERSION,
        "sha256:input",
        "sha256:changes",
        List.of(new VersionRef("pricing", "pricing-v1")),
        "sha256:ledger",
        List.of("event-001"),
        "sha256:original");

    assertThatThrownBy(() -> service.runReplay(validCommand("idem-incomplete", incomplete)))
        .isInstanceOf(ReplayPackageIncompleteException.class)
        .hasMessageContaining("missing required version refs");
  }

  @Test
  void replayHashComparatorTestClassifiesExactMatch() {
    ReplayPackage replayPackage = replayPackageWithOriginalHash(null);
    replayPackage = replayPackageWithOriginalHash(WhatIfReplayService.replayHash(replayPackage));

    var response = service.runReplay(validCommand("idem-match", replayPackage));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.mismatchCategory()).isEqualTo("MATCH");
    assertThat(response.originalHash()).isEqualTo(response.replayHash());
    assertThat(response.eventTypes()).containsExactly("whatif.replay.requested.v1", "whatif.replay.completed.v1");
    assertThat(response.auditRef()).contains("WHAT_IF_REPLAY_COMPLETED");
    assertThat(response.evidenceJson()).contains("what-if-replay-evidence-v1");
    assertThat(response.evidenceJson()).doesNotContain("borrowerSsn");
  }

  @Test
  void replayMismatchClassifierTestDetectsConfigChanged() {
    var response = service.runReplay(new WhatIfReplayService.CreateReplayCommand(
        "tenant-001",
        "saved-analysis",
        "analysis-001",
        "current versions comparison",
        "audit-support",
        true,
        "standard",
        replayPackageWithOriginalHash("sha256:original"),
        List.of(
            new VersionRef("pricing", "pricing-v2"),
            new VersionRef("eligibility", "eligibility-v1"),
            new VersionRef("product", "product-v1"),
            new VersionRef("mi", "mi-v1"),
            new VersionRef("lock", "lock-v1"),
            new VersionRef("engine", "engine-v1")),
        List.of(),
        "idem-config",
        "auditor-001",
        "corr-001",
        "cause-001"));

    assertThat(response.mismatchCategory()).isEqualTo("CONFIG_CHANGED");
    assertThat(response.eventTypes()).contains("whatif.replay.mismatch_detected.v1");
  }

  @Test
  void replayToleranceBehaviorClassifiesRoundingDrift() {
    var response = service.runReplay(new WhatIfReplayService.CreateReplayCommand(
        "tenant-001",
        "saved-analysis",
        "analysis-001",
        "same versions",
        "audit-support",
        true,
        "standard",
        replayPackageWithOriginalHash("sha256:original"),
        List.of(),
        List.of(new ReplayDiff("/apr", "rate", new BigDecimal("6.12500"), new BigDecimal("6.12501"), "0.00001", "ROUNDING_DRIFT")),
        "idem-rounding",
        "auditor-001",
        "corr-001",
        "cause-001"));

    assertThat(response.mismatchCategory()).isEqualTo("ROUNDING_DRIFT");
    assertThat(response.diffs()).hasSize(1);
  }

  @Test
  void replayMissingVersionITFailsClosed() {
    ReplayPackage missingPricingArchive = new ReplayPackage(
        WhatIfReplayService.PACKAGE_SCHEMA_VERSION,
        "sha256:input",
        "sha256:changes",
        List.of(
            new VersionRef("pricing", "unavailable"),
            new VersionRef("eligibility", "eligibility-v1"),
            new VersionRef("product", "product-v1"),
            new VersionRef("mi", "mi-v1"),
            new VersionRef("lock", "lock-v1"),
            new VersionRef("engine", "engine-v1")),
        "sha256:ledger",
        List.of("event-001"),
        "sha256:original");

    assertThatThrownBy(() -> service.runReplay(validCommand("idem-missing", missingPricingArchive)))
        .isInstanceOf(ReplayVersionUnavailableException.class)
        .hasMessage("archived pricing version is unavailable");
  }

  @Test
  void replayReusesSameIdempotencyKeyAndConflictsOnDifferentRequest() {
    var first = service.runReplay(validCommand("idem-replay", replayPackageWithOriginalHash("sha256:original")));
    var replay = service.runReplay(validCommand("idem-replay", replayPackageWithOriginalHash("sha256:original")));

    assertThat(replay).isEqualTo(first);
    assertThat(repository.size()).isEqualTo(1);
    assertThatThrownBy(() -> service.runReplay(new WhatIfReplayService.CreateReplayCommand(
        "tenant-001",
        "export",
        "export-001",
        "same versions",
        "audit-support",
        true,
        "standard",
        replayPackageWithOriginalHash("sha256:original"),
        List.of(),
        List.of(),
        "idem-replay",
        "auditor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("idempotency key was already used with a different replay request");
  }

  private static WhatIfReplayService.CreateReplayCommand validCommand(String idempotencyKey, ReplayPackage replayPackage) {
    return new WhatIfReplayService.CreateReplayCommand(
        "tenant-001",
        "saved-analysis",
        "analysis-001",
        "same versions",
        "audit-support",
        true,
        "standard",
        replayPackage,
        List.of(),
        List.of(),
        idempotencyKey,
        "auditor-001",
        "corr-001",
        "cause-001");
  }

  private static ReplayPackage replayPackageWithOriginalHash(String originalHash) {
    return new ReplayPackage(
        WhatIfReplayService.PACKAGE_SCHEMA_VERSION,
        "sha256:input",
        "sha256:changes",
        List.of(
            new VersionRef("pricing", "pricing-v1"),
            new VersionRef("eligibility", "eligibility-v1"),
            new VersionRef("product", "product-v1"),
            new VersionRef("mi", "mi-v1"),
            new VersionRef("lock", "lock-v1"),
            new VersionRef("engine", "engine-v1")),
        "sha256:ledger",
        List.of("event-001", "event-002"),
        originalHash == null ? "sha256:placeholder" : originalHash);
  }
}
