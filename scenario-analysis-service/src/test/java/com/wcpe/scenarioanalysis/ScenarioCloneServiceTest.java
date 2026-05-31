package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.ScenarioCloneService.CloneScenarioRequest;
import com.wcpe.scenarioanalysis.ScenarioCloneService.InMemoryScenarioLineageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioCloneServiceTest {
  private InMemoryScenarioLineageRepository repository;
  private ScenarioCloneService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryScenarioLineageRepository();
    service = new ScenarioCloneService(
        repository,
        Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void cloneScenarioRecordsRequestedVariantIdentity() {
    var result = service.cloneScenario(new CloneScenarioRequest("source-001", "variant-001"));

    assertThat(result.variantScenarioId()).isEqualTo("variant-001");
    assertThat(result.lineage().variantScenarioId()).isEqualTo("variant-001");
    assertThat(result.lineage().sourceScenarioId()).isEqualTo("source-001");
    assertThat(result.lineage().operationType()).isEqualTo("SCENARIO_VARIANT_CLONE");
  }

  @Test
  void findLineageByVariantIdReturnsOriginalSourceScenarioReference() {
    service.cloneScenario(new CloneScenarioRequest("source-002", "variant-002"));

    assertThat(service.findLineageByVariantId("variant-002"))
        .hasValueSatisfying(lineage -> assertThat(lineage.sourceScenarioId()).isEqualTo("source-002"));
  }

  @Test
  void missingSourceReferenceDoesNotCreateLineage() {
    assertThatThrownBy(() -> service.cloneScenario(new CloneScenarioRequest(" ", "variant-003")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("sourceScenarioId is required");

    assertThat(repository.size()).isZero();
  }

  @Test
  void missingVariantIdentityDoesNotCreateLineage() {
    assertThatThrownBy(() -> service.cloneScenario(new CloneScenarioRequest("source-004", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("variantScenarioId is required");

    assertThat(repository.size()).isZero();
  }

  @Test
  void sameSourceAndVariantIdentifierDoesNotCreateLineage() {
    assertThatThrownBy(() -> service.cloneScenario(new CloneScenarioRequest("scenario-005", "scenario-005")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("variantScenarioId must differ from sourceScenarioId");

    assertThat(repository.size()).isZero();
  }
}
