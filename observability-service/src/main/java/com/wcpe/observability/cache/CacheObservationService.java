package com.wcpe.observability.cache;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class CacheObservationService {
  private static final int MAX_METADATA_LENGTH = 64;
  private static final int MAX_DIAGNOSTIC_LENGTH = 160;
  private static final String REDACTED = "REDACTED";
  private final Clock clock;

  public CacheObservationService(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public CacheObservation observe(CacheObservationInput input) {
    Objects.requireNonNull(input, "input is required");

    List<String> diagnostics = new ArrayList<>();
    diagnostics.addAll(sanitizeDiagnostics(input.diagnostics()));

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
        sanitizeOptionalMetadata(input.cacheKey(), "cacheKey", diagnostics),
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
    if (!isSafeMetadata(normalized)) {
      diagnostics.add(fieldName + " contained unsafe metadata; redacted");
      return REDACTED;
    }
    return normalized;
  }

  private static String sanitizeOptionalMetadata(
      String value, String fieldName, List<String> diagnostics) {
    String normalized = normalizeOptional(value);
    if (normalized == null) {
      return null;
    }
    if (!isSafeMetadata(normalized)) {
      diagnostics.add(fieldName + " contained unsafe metadata; redacted");
      return REDACTED;
    }
    return normalized;
  }

  private static List<String> sanitizeDiagnostics(List<String> diagnostics) {
    if (diagnostics == null) {
      return List.of();
    }

    List<String> sanitized = new ArrayList<>();
    for (String diagnostic : diagnostics) {
      String normalized = normalizeOptional(diagnostic);
      if (normalized == null) {
        continue;
      }
      if (looksSensitive(normalized)) {
        sanitized.add("diagnostic redacted: sensitive value omitted");
      } else if (normalized.length() > MAX_DIAGNOSTIC_LENGTH) {
        sanitized.add("diagnostic redacted: value exceeded safe length");
      } else {
        sanitized.add(normalized);
      }
    }
    return sanitized;
  }

  private static boolean isSafeMetadata(String value) {
    return value.length() <= MAX_METADATA_LENGTH && !looksSensitive(value);
  }

  private static boolean looksSensitive(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    return lower.contains("password")
        || lower.contains("credential")
        || lower.contains("secret")
        || lower.contains("token")
        || lower.contains("authorization")
        || lower.contains("borrower")
        || lower.contains("customer")
        || lower.contains("ssn")
        || lower.contains("account");
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.strip();
  }
}
