package com.wcpe.observability.loadtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingLoadTestPlanTest {
  @Test
  void defaultScenarioMixMatchesStoryAndTotalsToOneHundred() {
    PricingLoadScenarioMix mix = PricingLoadScenarioMix.fromStoryDefaults();

    assertEquals(40, mix.percentageFor(PricingLoadScenarioType.WARM_CACHE_REPEAT_QUOTES));
    assertEquals(25, mix.percentageFor(PricingLoadScenarioType.COLD_CACHE_UNIQUE_QUOTES));
    assertEquals(10, mix.percentageFor(PricingLoadScenarioType.REFERENCE_DATA_VERSION_CHANGES));
    assertEquals(10, mix.percentageFor(PricingLoadScenarioType.INTENTIONAL_INVALID_REQUESTS));
    assertEquals(10, mix.percentageFor(PricingLoadScenarioType.MULTI_TENANT_PARALLEL));
    assertEquals(5, mix.percentageFor(PricingLoadScenarioType.REDIS_DEGRADED_FALLBACK));
    assertEquals(100, mix.percentageByType().values().stream().mapToInt(Integer::intValue).sum());
  }

  @Test
  void rejectsIncompleteScenarioMixInsteadOfInventingRemainder() {
    Map<PricingLoadScenarioType, Integer> incomplete = Map.of(
        PricingLoadScenarioType.WARM_CACHE_REPEAT_QUOTES, 40,
        PricingLoadScenarioType.COLD_CACHE_UNIQUE_QUOTES, 25);

    assertThrows(IllegalArgumentException.class, () -> new PricingLoadScenarioMix(incomplete));
  }

  @Test
  void planIsParameterizedAndCapturesSloMetricsAndArtifacts() {
    PricingLoadTestPlan plan = PricingLoadTestPlan.storyDefault(
        URI.create("https://pricing-api.localdev"),
        "localdev",
        "git-abc123",
        Instant.parse("2026-06-08T00:45:00Z"));

    assertEquals("PII-17-S07", plan.ownerStory());
    assertEquals(500, plan.sloTargets().warmCacheP95Millis());
    assertEquals(1500, plan.sloTargets().coldCacheP95Millis());
    assertEquals(2500, plan.sloTargets().p99Millis());
    assertTrue(plan.metricNames().contains("cache.hit.ratio"));
    assertTrue(plan.metricNames().contains("backpressure.activations"));
    assertTrue(plan.artifactNames().contains("explain-plans"));
    assertTrue(plan.runbookSteps().stream().anyMatch(step -> step.contains("synthetic tenants")));
  }

  @Test
  void baseUrlCannotCarryCredentials() {
    assertThrows(IllegalArgumentException.class, () -> PricingLoadTestPlan.storyDefault(
        URI.create("https://user:secret@pricing-api.localdev"),
        "localdev",
        "git-abc123",
        Instant.parse("2026-06-08T00:45:00Z")));
  }

  @Test
  void loadTestAssetsAreSyntheticParameterizedAndTargetStoryOwned() throws IOException {
    String fixture = Files.readString(Path.of(
        "src/test/resources/loadtest/PII-17-S07/pricing-load-test-fixture.json"));
    String script = Files.readString(Path.of(
        "src/test/resources/loadtest/PII-17-S07/pricing-load-test.k6.js"));
    String runbook = Files.readString(Path.of(
        "src/main/resources/runbooks/PII-17-S07-pricing-load-test.md"));

    assertTrue(fixture.contains("\"ownerStory\": \"PII-17-S07\""));
    assertTrue(fixture.contains("synthetic-tenant-alpha"));
    assertTrue(fixture.contains("\"scenarioMix\""));
    assertTrue(script.contains("__ENV.PRICING_BASE_URL"));
    assertTrue(script.contains("pricing-load-test"));
    assertTrue(runbook.contains("Do not use real borrower PII"));
  }
}
