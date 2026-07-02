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
    String quoteOpenApi = Files.readString(Path.of(
        "../quote-service/src/main/resources/openapi/loanpass-quote-api.yml"));

    assertTrue(fixture.contains("\"ownerStory\": \"PII-17-S07\""));
    assertTrue(fixture.contains("11111111-1111-1111-1111-111111111111"));
    assertTrue(fixture.contains("\"pathTemplate\": \"/api/v1/tenants/{tenantId}/quotes\""));
    assertTrue(fixture.contains("\"defaultApiPrefix\": \"/api/v1\""));
    assertTrue(fixture.contains("\"defaultQuotePath\": \"/quotes\""));
    assertTrue(fixture.contains("\"openApiServerUrl\": \"/api/v1\""));
    assertTrue(fixture.contains("\"expectedResponseStatuses\""));
    assertTrue(fixture.contains("\"contractRejections\": [400, 422]"));
    assertTrue(fixture.contains("\"scenarioMix\""));
    assertTrue(fixture.contains("\"syntheticFixtureMetadata\""));
    assertTrue(fixture.contains("PII-17-S07-pricing-load-test-seed-v1"));
    assertTrue(fixture.contains("\"productionValidity\": false"));
    assertTrue(fixture.contains("\"seededQuoteRequestRefs\""));
    assertTrue(script.contains("__ENV.PRICING_BASE_URL"));
    assertTrue(script.contains("fixture.quoteApi.defaultApiPrefix"));
    assertTrue(script.contains("fixture.quoteApi.defaultQuotePath"));
    assertTrue(script.contains("pricing-load-test"));
    assertTrue(script.contains("${baseUrl}${apiPrefix}/tenants/${tenantId}${quotePath}"));
    assertTrue(script.contains("PRICING_QUOTE_PATH"));
    assertTrue(script.contains("const refs = fixture.seededQuoteRequestRefs"));
    assertTrue(script.contains("quote_unexpected_response_rate"));
    assertTrue(script.contains("isExpectedResponseStatus(response.status)"));
    assertTrue(script.contains("syntheticFixtureMetadata: fixture.syntheticFixtureMetadata"));
    assertTrue(script.contains("endpoint: 'quote-create'"));
    assertTrue(script.contains("referenceDataVersionRef"));
    assertTrue(runbook.contains("Do not use real borrower PII"));
    assertTrue(runbook.contains("/api/v1/tenants/{tenantId}/quotes"));
    assertTrue(runbook.contains("PRICING_API_PREFIX=/api/v1"));
    assertTrue(runbook.contains("PRICING_QUOTE_PATH=/quotes"));
    assertTrue(quoteOpenApi.contains("url: /api/v1"));
    assertTrue(quoteOpenApi.contains("byte-for-byte public LoanPass swagger diff"));
  }
}
