package com.wcpe.observability.loadtest;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PricingLoadTestPlan(
    String ownerStory,
    URI baseUrl,
    String environment,
    String gitSha,
    Instant createdAt,
    PricingLoadScenarioMix scenarioMix,
    PricingLoadSloTargets sloTargets,
    List<PricingLoadTestProfile> profiles,
    List<String> metricNames,
    List<String> artifactNames) {
  public PricingLoadTestPlan {
    if (!"PII-17-S07".equals(ownerStory)) {
      throw new IllegalArgumentException("owner story must be PII-17-S07");
    }
    requireSafeToken(environment, "environment", 80);
    requireSafeToken(gitSha, "gitSha", 80);
    Objects.requireNonNull(createdAt, "createdAt is required");
    Objects.requireNonNull(scenarioMix, "scenarioMix is required");
    Objects.requireNonNull(sloTargets, "sloTargets is required");
    profiles = List.copyOf(profiles);
    metricNames = List.copyOf(metricNames);
    artifactNames = List.copyOf(artifactNames);
    validateBaseUrl(baseUrl);
    if (profiles.isEmpty() || metricNames.isEmpty() || artifactNames.isEmpty()) {
      throw new IllegalArgumentException("profiles, metrics, and artifacts are required");
    }
  }

  public static PricingLoadTestPlan storyDefault(URI baseUrl, String environment, String gitSha, Instant createdAt) {
    return new PricingLoadTestPlan(
        "PII-17-S07",
        baseUrl,
        environment,
        gitSha,
        createdAt,
        PricingLoadScenarioMix.fromStoryDefaults(),
        PricingLoadSloTargets.fromStoryTargets(),
        List.of(PricingLoadTestProfile.SMOKE, PricingLoadTestProfile.BASELINE,
            PricingLoadTestProfile.STRESS, PricingLoadTestProfile.DEGRADATION),
        List.of("quote.latency", "pricing.compute.time", "cache.hit.ratio", "db.query.latency",
            "redis.latency", "rate-limit.decisions", "backpressure.activations"),
        List.of("raw-summary-json", "html-report", "dashboard-export", "explain-plans", "environment-config"));
  }

  public List<String> runbookSteps() {
    return List.of(
        "Start a local/dev Docker stack that supplies the pricing API, PostgreSQL, Redis, broker, and metrics endpoints.",
        "Seed synthetic tenants, scenario refs, reference-data version refs, pricing config refs, and no real borrower PII.",
        "Run smoke, baseline, stress, and degradation profiles with per-virtual-user correlation IDs and idempotency keys.",
        "Attach raw summary JSON, HTML report, dashboard export, EXPLAIN plans, and environment config.",
        "Classify bottlenecks as application, DB, Redis, broker, rate-limit, or backpressure before accepting SLO evidence.");
  }

  private static void validateBaseUrl(URI baseUrl) {
    Objects.requireNonNull(baseUrl, "baseUrl is required");
    if (baseUrl.getScheme() == null || baseUrl.getHost() == null) {
      throw new IllegalArgumentException("baseUrl must include scheme and host");
    }
    if (baseUrl.getUserInfo() != null) {
      throw new IllegalArgumentException("baseUrl must not include credentials");
    }
  }

  private static String requireSafeToken(String raw, String fieldName, int maxLength) {
    String value = Objects.requireNonNull(raw, fieldName + " is required").strip();
    if (value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(fieldName + " is required and must fit safe length");
    }
    if (!value.matches("[A-Za-z0-9._:-]+")) {
      throw new IllegalArgumentException(fieldName + " contains unsafe characters");
    }
    return value;
  }
}
