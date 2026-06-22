package com.wcpe.scenarioanalysis;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ScenarioCloneService {
  private final ScenarioLineageRepository lineageRepository;
  private final Clock clock;

  public ScenarioCloneService(ScenarioLineageRepository lineageRepository, Clock clock) {
    this.lineageRepository = Objects.requireNonNull(lineageRepository, "lineageRepository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public ScenarioCloneResult cloneScenario(CloneScenarioRequest request) {
    CloneScenarioRequest validRequest = validate(request);
    ScenarioLineageRecord lineage = new ScenarioLineageRecord(
        validRequest.variantScenarioId(),
        validRequest.sourceScenarioId(),
        "SCENARIO_VARIANT_CLONE",
        Instant.now(clock));
    lineageRepository.save(lineage);
    return new ScenarioCloneResult(validRequest.variantScenarioId(), lineage);
  }

  public Optional<ScenarioLineageRecord> findLineageByVariantId(String variantScenarioId) {
    String normalizedVariantId = requireText(variantScenarioId, "variantScenarioId is required");
    return lineageRepository.findByVariantScenarioId(normalizedVariantId);
  }

  private CloneScenarioRequest validate(CloneScenarioRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("clone request is required");
    }
    String sourceScenarioId = requireText(request.sourceScenarioId(), "sourceScenarioId is required");
    String variantScenarioId = requireText(request.variantScenarioId(), "variantScenarioId is required");
    if (sourceScenarioId.equals(variantScenarioId)) {
      throw new IllegalArgumentException("variantScenarioId must differ from sourceScenarioId");
    }
    return new CloneScenarioRequest(sourceScenarioId, variantScenarioId);
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  public record CloneScenarioRequest(String sourceScenarioId, String variantScenarioId) {}

  public record ScenarioCloneResult(String variantScenarioId, ScenarioLineageRecord lineage) {}

  public record ScenarioLineageRecord(
      String variantScenarioId,
      String sourceScenarioId,
      String operationType,
      Instant createdAt) {}

  public interface ScenarioLineageRepository {
    void save(ScenarioLineageRecord lineage);

    Optional<ScenarioLineageRecord> findByVariantScenarioId(String variantScenarioId);
  }

}
