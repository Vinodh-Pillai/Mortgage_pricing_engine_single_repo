package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.WhatIfVariantService.CreateVariantCommand;
import com.wcpe.scenarioanalysis.WhatIfVariantService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.WhatIfVariantService.UnsupportedFieldException;
import com.wcpe.scenarioanalysis.WhatIfVariantService.VariantChange;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatIfVariantServiceTest {
  private TestOnlyInMemoryWhatIfVariantRepository repository;
  private WhatIfVariantService service;

  @BeforeEach
  void setUp() {
    repository = new TestOnlyInMemoryWhatIfVariantRepository();
    service = new WhatIfVariantService(
        repository,
        Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void createsDraftFromQuoteSnapshotReference() {
    var response = service.createVariant(validCommand("idem-001"));

    assertThat(response.status()).isEqualTo("DRAFT");
    assertThat(response.variantVersion()).isEqualTo(1);
    assertThat(response.sourceQuoteSnapshotId()).isEqualTo("quote-snapshot:quote-123:v3");
    assertThat(response.changedFields()).containsExactly("fico", "loanAmount");
    assertThat(response.inputHash()).startsWith("sha256:");
    assertThat(response.links().price()).contains(response.variantId().toString());
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void sameInputProducesSameDeterministicHash() {
    var first = service.createVariant(validCommand("idem-001"));
    var second = service.createVariant(validCommand("idem-002"));

    assertThat(second.inputHash()).isEqualTo(first.inputHash());
    assertThat(second.variantId()).isNotEqualTo(first.variantId());
  }

  @Test
  void replaysSameCreateResponseForDuplicateIdempotencyKey() {
    var first = service.createVariant(validCommand("idem-001"));
    var replay = service.createVariant(validCommand("idem-001"));

    assertThat(replay).isEqualTo(first);
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void duplicateIdempotencyKeyWithDifferentRequestIsConflict() {
    service.createVariant(validCommand("idem-001"));

    assertThatThrownBy(() -> service.createVariant(new CreateVariantCommand(
        "tenant-001",
        "quote-123",
        "Lower payment option",
        "COUNTER_OFFER",
        3,
        Instant.parse("2026-06-01T12:00:00Z"),
        List.of(new VariantChange("fico", "720", "735", "INTEGER")),
        "idem-001",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("idempotency key was already used with a different request");
  }

  @Test
  void rejectsUnsupportedFieldPath() {
    assertThatThrownBy(() -> service.createVariant(new CreateVariantCommand(
        "tenant-001",
        "quote-123",
        "Lower payment option",
        "BORROWER_REQUEST",
        3,
        Instant.parse("2026-06-01T12:00:00Z"),
        List.of(new VariantChange("borrower.ssn", null, "123-45-6789", "STRING")),
        "idem-001",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(UnsupportedFieldException.class)
        .hasMessage("fieldPath is not editable: borrower.ssn");
  }

  private static CreateVariantCommand validCommand(String idempotencyKey) {
    return new CreateVariantCommand(
        "tenant-001",
        "quote-123",
        "Lower payment option",
        "BORROWER_REQUEST",
        3,
        Instant.parse("2026-06-01T12:00:00Z"),
        List.of(
            new VariantChange("fico", "720", "740", "INTEGER"),
            new VariantChange("loanAmount", "400000", "390000", "MONEY")),
        idempotencyKey,
        "actor-001",
        "corr-001",
        "cause-001");
  }
}
