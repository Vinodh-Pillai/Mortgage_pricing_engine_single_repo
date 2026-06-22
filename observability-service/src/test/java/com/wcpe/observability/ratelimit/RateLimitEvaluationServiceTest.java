package com.wcpe.observability.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RateLimitEvaluationServiceTest {
  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");
  private static final UUID OTHER_TENANT_ID = UUID.fromString("cccccccc-1111-2222-3333-dddddddddddd");
  private static final String PRINCIPAL_HASH = "sha256:0123456789abcdef0123456789abcdef";
  private static final String OTHER_PRINCIPAL_HASH = "sha256:abcdef0123456789abcdef0123456789";
  private static final Instant NOW = Instant.parse("2026-06-08T03:00:00Z");

  @Test
  void fixedWindowAllowsConfiguredCapacityThenReturnsMachineReadable429() {
    RateLimitEvaluationService service = service();
    RateLimitPolicy policy = policy(2, 1, 0, PRINCIPAL_HASH);

    RateLimitResult first = service.evaluate(List.of(policy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.ADMIN_POLICY));
    RateLimitResult second = service.evaluate(List.of(policy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.ADMIN_POLICY));
    RateLimitResult third = service.evaluate(List.of(policy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.ADMIN_POLICY));
    RateLimitResult fourth = service.evaluate(List.of(policy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.ADMIN_POLICY));

    assertTrue(first.allowed());
    assertEquals(RateLimitDecision.ALLOWED, third.decision());
    assertEquals(RateLimitDecision.THROTTLED, fourth.decision());
    assertEquals("3", fourth.responseHeaders().get("X-RateLimit-Limit"));
    assertEquals("0", fourth.responseHeaders().get("X-RateLimit-Remaining"));
    assertTrue(fourth.responseHeaders().containsKey("Retry-After"));
    assertEquals("RATE_LIMIT_EXCEEDED", fourth.problemJson().get("reasonCode"));
    assertEquals("RateLimitBreached.v1", fourth.events().get(1).eventType());
    assertFalse(fourth.events().stream()
        .flatMap(event -> event.payload().values().stream())
        .anyMatch(value -> value.toLowerCase().contains("token")));
    assertEquals(RateLimitDecision.ALLOWED, second.auditRecord().decision());
  }

  @Test
  void countersAreIsolatedByTenantPrincipalEndpointAndPolicyVersion() {
    RateLimitEvaluationService service = service();
    RateLimitPolicy tenantPolicy = policy(1, 0, 0, PRINCIPAL_HASH);
    RateLimitPolicy otherTenantPolicy = new RateLimitPolicy(
        UUID.fromString("99999999-1111-2222-3333-bbbbbbbbbbbb"),
        OTHER_TENANT_ID,
        "quote-read-policy",
        "pricing-read",
        PrincipalType.API_CLIENT,
        PRINCIPAL_HASH,
        RateLimitAlgorithm.FIXED_WINDOW,
        1,
        0,
        0,
        Duration.ofSeconds(60),
        RateLimitPolicyStatus.PUBLISHED,
        1,
        NOW.minusSeconds(60),
        null,
        "tenant-admin-17",
        "risk-approver-17");

    assertEquals(RateLimitDecision.ALLOWED,
        service.evaluate(List.of(tenantPolicy, otherTenantPolicy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.ADMIN_POLICY)).decision());
    assertEquals(RateLimitDecision.THROTTLED,
        service.evaluate(List.of(tenantPolicy, otherTenantPolicy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.ADMIN_POLICY)).decision());
    assertEquals(RateLimitDecision.ALLOWED,
        service.evaluate(List.of(tenantPolicy, otherTenantPolicy), request(OTHER_TENANT_ID, PRINCIPAL_HASH, EndpointRisk.ADMIN_POLICY)).decision());

    RateLimitPolicy wildcard = policy(1, 0, 0, null);
    RateLimitPolicy exact = policy(5, 0, 0, OTHER_PRINCIPAL_HASH);
    RateLimitResult exactResult = service.evaluate(List.of(wildcard, exact), request(TENANT_ID, OTHER_PRINCIPAL_HASH, EndpointRisk.ADMIN_POLICY));

    assertEquals("5", exactResult.responseHeaders().get("X-RateLimit-Limit"));
  }

  @Test
  void missingPolicyFailsClosedWithoutInventingTenantDefaults() {
    RateLimitEvaluationService service = service();

    RateLimitResult result = service.evaluate(List.of(), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.ADMIN_POLICY));

    assertEquals(RateLimitDecision.FAIL_CLOSED, result.decision());
    assertEquals("POLICY_NOT_SATISFIED", result.reasonCode());
    assertEquals("422", result.problemJson().get("status"));
    assertEquals("0", result.responseHeaders().get("X-RateLimit-Limit"));
    assertEquals("missing-policy", result.auditRecord().policyKey());
  }

  @Test
  void redisUnavailableFailsClosedForHighRiskButUsesConfiguredEmergencyCapForBusinessReads() {
    RateLimitCounterStore unavailable = (key, capacity, resetAt) -> {
      throw new RateLimitCounterUnavailableException("redis unavailable in local test");
    };
    RateLimitEvaluationService service = new RateLimitEvaluationService(unavailable, new LocalRateLimitCounterStore());
    RateLimitPolicy policy = policy(10, 0, 2, PRINCIPAL_HASH);

    RateLimitResult highRisk = service.evaluate(List.of(policy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.HIGH_RISK_OPS));
    RateLimitResult businessOne = service.evaluate(List.of(policy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.BUSINESS_QUOTE_READ));
    RateLimitResult businessTwo = service.evaluate(List.of(policy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.BUSINESS_QUOTE_READ));
    RateLimitResult businessThree = service.evaluate(List.of(policy), request(TENANT_ID, PRINCIPAL_HASH, EndpointRisk.BUSINESS_QUOTE_READ));

    assertEquals(RateLimitDecision.FAIL_CLOSED, highRisk.decision());
    assertEquals("RATE_LIMIT_COUNTER_UNAVAILABLE", highRisk.reasonCode());
    assertEquals("503", highRisk.problemJson().get("status"));
    assertEquals(RateLimitDecision.FAIL_OPEN_DEGRADED, businessOne.decision());
    assertEquals(RateLimitDecision.FAIL_OPEN_DEGRADED, businessTwo.decision());
    assertEquals(RateLimitDecision.THROTTLED, businessThree.decision());
    assertTrue(businessOne.metricNames().contains("rate_limit.fallback.count"));
  }

  @Test
  void policyValidationRequiresPublishedApprovalSeparationAndHashedPrincipal() {
    assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy(
        UUID.fromString("11111111-1111-2222-3333-bbbbbbbbbbbb"),
        TENANT_ID,
        "quote-read-policy",
        "pricing-read",
        PrincipalType.API_CLIENT,
        "raw-client-token-should-not-pass",
        RateLimitAlgorithm.FIXED_WINDOW,
        10,
        0,
        0,
        Duration.ofSeconds(60),
        RateLimitPolicyStatus.PUBLISHED,
        1,
        NOW.minusSeconds(60),
        null,
        "same-user",
        "same-user"));

    assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy(
        UUID.fromString("22222222-1111-2222-3333-bbbbbbbbbbbb"),
        TENANT_ID,
        "quote-read-policy",
        "pricing-read",
        PrincipalType.API_CLIENT,
        PRINCIPAL_HASH,
        RateLimitAlgorithm.FIXED_WINDOW,
        10,
        0,
        0,
        Duration.ofSeconds(60),
        RateLimitPolicyStatus.PUBLISHED,
        1,
        NOW.minusSeconds(60),
        null,
        "same-user",
        "same-user"));
  }

  @Test
  void goldenFixtureDocumentsHeadersEventsAndSyntheticIdentifiers() throws Exception {
    String fixture = Files.readString(Path.of(
        "src/test/resources/ratelimit/PII-17-S08/rate-limiting-fixture.json"));

    assertTrue(fixture.contains("\"ownerStory\": \"PII-17-S08\""));
    assertTrue(fixture.contains("X-RateLimit-Limit"));
    assertTrue(fixture.contains("RateLimitBreached.v1"));
    assertTrue(fixture.contains("All numeric limits are test fixture values"));
    assertFalse(fixture.toLowerCase().contains("borrower"));
    assertFalse(fixture.toLowerCase().contains("api key"));
  }

  private static RateLimitEvaluationService service() {
    return new RateLimitEvaluationService(new LocalRateLimitCounterStore(), new LocalRateLimitCounterStore());
  }

  private static RateLimitPolicy policy(int limit, int burst, int fallbackLimit, String principalHash) {
    return new RateLimitPolicy(
        UUID.fromString("11111111-1111-2222-3333-bbbbbbbbbbbb"),
        TENANT_ID,
        "quote-read-policy",
        "pricing-read",
        PrincipalType.API_CLIENT,
        principalHash,
        RateLimitAlgorithm.FIXED_WINDOW,
        limit,
        burst,
        fallbackLimit,
        Duration.ofSeconds(60),
        RateLimitPolicyStatus.PUBLISHED,
        1,
        NOW.minusSeconds(60),
        null,
        "tenant-admin-17",
        "risk-approver-17");
  }

  private static RateLimitRequest request(UUID tenantId, String principalHash, EndpointRisk endpointRisk) {
    return new RateLimitRequest(
        tenantId,
        principalHash,
        PrincipalType.API_CLIENT,
        "pricing-read",
        "corr-PII17S08",
        endpointRisk,
        NOW);
  }
}
