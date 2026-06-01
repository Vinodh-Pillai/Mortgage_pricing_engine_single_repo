package com.wcpe.observability.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CacheObservationServiceTest {
  private final Instant now = Instant.parse("2026-05-31T12:00:00Z");
  private final CacheObservationService service =
      new CacheObservationService(Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void healthyObservationPreservesIdentifierTimestampFreshnessAndCorrelation() {
    CacheObservation observation = service.observe(new CacheObservationInput(
        "obs-1",
        "corr-1",
        "rate-cache",
        "rate:30y",
        CacheObservationStatus.HEALTHY,
        now,
        new FreshnessMetadata(FreshnessSource.INPUT, "source supplied heartbeat", 125L),
        List.of("cache responded")));

    assertTrue(observation.healthy());
    assertEquals("obs-1", observation.observationId());
    assertEquals("corr-1", observation.correlationId());
    assertEquals("rate-cache", observation.cacheId());
    assertEquals(now, observation.observedAt());
    assertEquals(FreshnessSource.INPUT, observation.freshness().source());
    assertEquals(125L, observation.freshness().observedAgeMillis());
  }

  @Test
  void nonHealthyStatesExposeDiagnosticsWithoutPricingMutation() {
    List<CacheObservation> observations = service.observeBatch(List.of(
        inputFor(CacheObservationStatus.STALE, "fixture age exceeded"),
        inputFor(CacheObservationStatus.DEGRADED, "partial cache response"),
        inputFor(CacheObservationStatus.UNAVAILABLE, "cache endpoint not reachable")));

    assertEquals(List.of(
        CacheObservationStatus.STALE,
        CacheObservationStatus.DEGRADED,
        CacheObservationStatus.UNAVAILABLE),
        observations.stream().map(CacheObservation::status).toList());
    assertTrue(observations.stream().noneMatch(CacheObservation::healthy));
    assertTrue(observations.stream().allMatch(observation -> !observation.diagnostics().isEmpty()));
  }

  @Test
  void absentFreshnessIsRepresentedExplicitlyWithoutBusinessThresholds() {
    CacheObservation observation = service.observe(new CacheObservationInput(
        "obs-absent",
        "corr-absent",
        "unknown-source-cache",
        null,
        CacheObservationStatus.DEGRADED,
        null,
        null,
        List.of()));

    assertEquals(now, observation.observedAt());
    assertEquals(FreshnessSource.ABSENT, observation.freshness().source());
    assertTrue(observation.diagnostics().contains("freshness metadata absent"));
    assertTrue(observation.diagnostics().contains("observedAt missing; normalized from service clock"));
  }

  @Test
  void malformedInputIsNormalizedDeterministically() {
    CacheObservation observation = service.observe(new CacheObservationInput(
        " ",
        null,
        "",
        "  cache-key  ",
        null,
        null,
        null,
        null));

    assertEquals("UNKNOWN", observation.observationId());
    assertEquals("UNKNOWN", observation.correlationId());
    assertEquals("UNKNOWN", observation.cacheId());
    assertEquals("cache-key", observation.cacheKey());
    assertEquals(CacheObservationStatus.UNAVAILABLE, observation.status());
    assertFalse(observation.diagnostics().isEmpty());
  }

  @Test
  void sensitiveMetadataAndDiagnosticsAreRedactedBeforeObservationIsReturned() {
    CacheObservation observation = service.observe(new CacheObservationInput(
        "obs-sensitive",
        "borrower-12345",
        "customer-cache",
        "token=abc123",
        CacheObservationStatus.DEGRADED,
        now,
        FreshnessMetadata.absent(),
        List.of(
            "password=secret-value",
            "customer name Jane Doe",
            "safe diagnostic")));

    assertEquals("REDACTED", observation.correlationId());
    assertEquals("REDACTED", observation.cacheId());
    assertEquals("REDACTED", observation.cacheKey());
    assertTrue(observation.diagnostics().contains("safe diagnostic"));
    assertTrue(observation.diagnostics().contains("diagnostic redacted: sensitive value omitted"));
    assertFalse(observation.diagnostics().stream().anyMatch(value -> value.contains("secret-value")));
    assertFalse(observation.diagnostics().stream().anyMatch(value -> value.contains("Jane Doe")));
  }

  @Test
  void oversizedMetadataAndDiagnosticsAreNormalizedAtTheSafeBoundary() {
    String oversizedMetadata = "corr-" + "x".repeat(70);
    String oversizedDiagnostic = "diagnostic-" + "x".repeat(170);

    CacheObservation observation = service.observe(new CacheObservationInput(
        "obs-oversized",
        oversizedMetadata,
        "cache-safe",
        null,
        CacheObservationStatus.STALE,
        now,
        FreshnessMetadata.absent(),
        List.of(oversizedDiagnostic)));

    assertEquals("REDACTED", observation.correlationId());
    assertEquals("cache-safe", observation.cacheId());
    assertTrue(observation.diagnostics().contains("correlationId contained unsafe metadata; redacted"));
    assertTrue(observation.diagnostics().contains("diagnostic redacted: value exceeded safe length"));
    assertFalse(observation.diagnostics().contains(oversizedDiagnostic));
  }

  @Test
  void negativeFreshnessAgeIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new FreshnessMetadata(FreshnessSource.FIXTURE, "fixture", -1L));
  }

  private CacheObservationInput inputFor(CacheObservationStatus status, String diagnostic) {
    return new CacheObservationInput(
        "obs-" + status.name().toLowerCase(),
        "corr-batch",
        "cache-" + status.name().toLowerCase(),
        null,
        status,
        now,
        new FreshnessMetadata(FreshnessSource.FIXTURE, "fixture supplied", null),
        List.of(diagnostic));
  }
}
