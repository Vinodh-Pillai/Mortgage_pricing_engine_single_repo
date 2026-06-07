package com.wcpe.observability.scenariohash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioHashServiceTest {
  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");
  private final ScenarioHashService service = new ScenarioHashService();

  @Test
  void producesSameHashForEquivalentInputsWithReorderedKeys() {
    ScenarioHash first = service.hash(new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(1),
        versions("rules-2026-06", "engine-0.1.0"),
        Map.of(
            "loan", Map.of(
                "rate", new BigDecimal("5.500000"),
                "loanAmount", new BigDecimal("425000.00"),
                "occupancy", "PRIMARY"),
            "productId", "CONV-30")));

    ScenarioHash second = service.hash(new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(1),
        versions("rules-2026-06", "engine-0.1.0"),
        Map.of(
            "productId", "CONV-30",
            "loan", Map.of(
                "occupancy", "PRIMARY",
                "loanAmount", new BigDecimal("425000.0"),
                "rate", new BigDecimal("5.500000")))));

    assertEquals(first, second);
    assertEquals(service.canonicalize(scenarioWithReorderedFields()), service.canonicalize(scenarioWithStableFields()));
  }

  @Test
  void changesHashWhenPricingTenantConfigOrSchemaChanges() {
    CanonicalPricingScenario baseline = scenarioWithStableFields();

    assertNotEquals(service.hash(baseline), service.hash(new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(1),
        versions("rules-2026-07", "engine-0.1.0"),
        baseline.pricingInput())));
    assertNotEquals(service.hash(baseline), service.hash(new CanonicalPricingScenario(
        UUID.fromString("cccccccc-1111-2222-3333-bbbbbbbbbbbb"),
        new HashSchemaVersion(1),
        baseline.versionGraph(),
        baseline.pricingInput())));
    assertNotEquals(service.hash(baseline), service.hash(new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(2),
        baseline.versionGraph(),
        baseline.pricingInput())));
    assertNotEquals(service.hash(baseline), service.hash(new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(1),
        baseline.versionGraph(),
        Map.of("loan", Map.of("borrowerCreditScore", 741, "loanAmount", new BigDecimal("425000.00"))))));
  }

  @Test
  void excludesPiiAndVolatileMetadataButPreservesPricingAffectingBorrowerFacts() {
    CanonicalPricingScenario baseline = scenarioWithSensitiveMetadata(
        "Jane Borrower",
        "jane@example.test",
        "555-1212",
        "corr-1",
        "en-US");
    CanonicalPricingScenario changedPiiOnly = scenarioWithSensitiveMetadata(
        "Different Name",
        "other@example.test",
        "555-3434",
        "corr-2",
        "fr-CA");

    String canonical = service.canonicalize(baseline);
    ScenarioHashExplanation explanation = service.explain(baseline);

    assertEquals(service.hash(baseline), service.hash(changedPiiOnly));
    assertFalse(canonical.contains("Jane"));
    assertFalse(canonical.contains("example.test"));
    assertFalse(canonical.contains("corr-1"));
    assertFalse(canonical.contains("en-US"));
    assertTrue(canonical.contains("borrowerCreditScore"));
    assertTrue(explanation.exclusions().stream().anyMatch(item -> item.jsonPath().endsWith("borrowerName")));
    assertTrue(explanation.exclusions().stream().anyMatch(item -> item.jsonPath().endsWith("correlationId")));
  }

  @Test
  void explanationEmitsSafeDiagnosticsWithoutRawCanonicalJson() {
    ScenarioHashExplanation explanation = service.explain(scenarioWithStableFields());

    assertEquals(1, explanation.hashSchemaVersion().value());
    assertEquals(64, explanation.scenarioHash().value().length());
    assertEquals(64, explanation.canonicalPayloadSha256().length());
    assertTrue(explanation.cacheEligible());
    assertTrue(explanation.versionGraph().containsKey("ruleSetVersion"));
    assertTrue(explanation.safeTelemetryFields().contains("metrics:scenario.hash.compute.latency"));
    assertTrue(explanation.safeTelemetryFields().contains("trace:pricing.scenario.hash"));
    assertTrue(explanation.runbookNote().contains("never log raw canonical JSON"));
    assertFalse(explanation.toString().contains("425000"));
  }

  @Test
  void rejectsBinaryFloatingPointForPricingValues() {
    CanonicalPricingScenario scenario = new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(1),
        versions("rules-2026-06", "engine-0.1.0"),
        Map.of("loan", Map.of("rate", 5.5d)));

    assertThrows(IllegalArgumentException.class, () -> service.hash(scenario));
  }

  private static CanonicalPricingScenario scenarioWithStableFields() {
    return new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(1),
        versions("rules-2026-06", "engine-0.1.0"),
        Map.of(
            "effectiveDate", "2026-06-07",
            "loan", Map.of(
                "borrowerCreditScore", 742,
                "loanAmount", new BigDecimal("425000.00"),
                "rate", new BigDecimal("5.500000"),
                "lockRequestedAt", Instant.parse("2026-06-07T12:00:00Z")),
            "productId", "CONV-30"));
  }

  private static CanonicalPricingScenario scenarioWithReorderedFields() {
    return new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(1),
        versions("rules-2026-06", "engine-0.1.0"),
        Map.of(
            "productId", "CONV-30",
            "loan", Map.of(
                "lockRequestedAt", Instant.parse("2026-06-07T12:00:00Z"),
                "rate", new BigDecimal("5.500000"),
                "loanAmount", new BigDecimal("425000.0"),
                "borrowerCreditScore", 742),
            "effectiveDate", "2026-06-07"));
  }

  private static CanonicalPricingScenario scenarioWithSensitiveMetadata(
      String borrowerName,
      String email,
      String phone,
      String correlationId,
      String uiLocale) {
    return new CanonicalPricingScenario(
        TENANT_ID,
        new HashSchemaVersion(1),
        versions("rules-2026-06", "engine-0.1.0"),
        Map.of(
            "loan", Map.of(
                "borrowerCreditScore", 742,
                "loanAmount", new BigDecimal("425000.00"),
                "borrowerName", borrowerName,
                "email", email,
                "phone", phone),
            "metadata", Map.of(
                "requestTimestamp", "2026-06-07T16:00:00Z",
                "correlationId", correlationId,
                "uiLocale", uiLocale)));
  }

  private static VersionGraph versions(String ruleSetVersion, String pricingEngineVersion) {
    return new VersionGraph(Map.of(
        "referenceDataVersion", "ref-2026-06",
        "ruleSetVersion", ruleSetVersion,
        "pricingEngineVersion", pricingEngineVersion));
  }
}
