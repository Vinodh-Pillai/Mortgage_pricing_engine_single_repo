package com.wcpe.observability.cache;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CacheObservationService {
  private final Clock clock;

  public CacheObservationService(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public CacheObservation observe(CacheObservationInput input) {
    Objects.requireNonNull(input, "input is required");

    List<String> diagnostics = new ArrayList<>();
    diagnostics.addAll(input.diagnostics() == null ? List.of() : input.diagnostics());

    String observationId = requireText(input.observationId(), "observationId", diagnostics);
    String correlationId = requireText(input.correlationId(), "correlationId", diagnostics);
    String cacheId = requireText(input.cacheId(), "cacheId", diagnostics);
    CacheObservationStatus status = input.status();
    if (status == null) {
      status = CacheObservationStatus.UNAVAILABLE;
      diagnostics.add("status missing; normalized to UNAVAILABLE");
    }

    Instant observedAt = input.observedAt();
    if (observedAt == null) {
      observedAt = clock.instant();
      diagnostics.add("observedAt missing; normalized from service clock");
    }

    FreshnessMetadata freshness = input.freshness();
    if (freshness == null) {
      freshness = FreshnessMetadata.absent();
      diagnostics.add("freshness metadata absent");
    }

    return new CacheObservation(
        observationId,
        correlationId,
        cacheId,
        normalizeOptional(input.cacheKey()),
        status,
        observedAt,
        freshness,
        List.copyOf(diagnostics));
  }

  public List<CacheObservation> observeBatch(List<CacheObservationInput> inputs) {
    if (inputs == null) {
      return List.of();
    }

    return inputs.stream().map(this::observe).toList();
  }

  private static String requireText(String value, String fieldName, List<String> diagnostics) {
    String normalized = normalizeOptional(value);
    if (normalized == null) {
      diagnostics.add(fieldName + " missing; normalized to UNKNOWN");
      return "UNKNOWN";
    }
    return normalized;
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.strip();
  }
}
