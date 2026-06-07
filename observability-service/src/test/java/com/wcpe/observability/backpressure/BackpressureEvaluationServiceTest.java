package com.wcpe.observability.backpressure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BackpressureEvaluationServiceTest {
  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-1717-0009-3333-bbbbbbbbbbbb");
  private static final UUID OTHER_TENANT_ID = UUID.fromString("cccccccc-1717-0009-3333-dddddddddddd");
  private static final Instant NOW = Instant.parse("2026-06-08T04:00:00Z");

  @Test
  void missingTenantPolicyFailsClosedWithoutInventedThresholds() {
    BackpressureDecision result = new BackpressureEvaluationService().evaluate(
        List.of(),
        List.of(signal("db.pool.active", "91.0", 4)),
        request(TENANT_ID, BackpressureResource.DB_POOL, BackpressurePriorityClass.PARTNER_API_QUOTES, false));

    assertEquals(BackpressureState.SHEDDING, result.state());
    assertEquals(BackpressureAction.SHED_REQUEST, result.action());
    assertEquals(422, result.httpStatus());
    assertEquals("POLICY_NOT_SATISFIED", result.reasonCode());
    assertEquals("SHEDDING", result.responseMetadata().get("backpressureState"));
    assertEquals("missing-policy", result.stateSnapshot().triggerMetric());
    assertTrue(result.events().stream().anyMatch(event -> event.eventType().equals("BackpressureRequestRejected.v1")));
  }

  @Test
  void configuredThresholdsDriveHysteresisPriorityAndResponseMapping() {
    BackpressureEvaluationService service = new BackpressureEvaluationService();
    BackpressurePolicy policy = policy(BackpressureResource.DB_POOL,
        List.of(new BackpressureTrigger("db.pool.active", new BigDecimal("85.0"), 3,
            BackpressureState.DEGRADED, BackpressureAction.REDUCE_CONCURRENCY)));

    BackpressureDecision normal = service.evaluate(List.of(policy),
        List.of(signal("db.pool.active", "86.0", 2)),
        request(TENANT_ID, BackpressureResource.DB_POOL, BackpressurePriorityClass.PARTNER_API_QUOTES, false));

    BackpressureDecision batch = service.evaluate(List.of(policy),
        List.of(signal("db.pool.active", "86.0", 3)),
        request(TENANT_ID, BackpressureResource.DB_POOL, BackpressurePriorityClass.BATCH_REPLAY, true));

    BackpressureDecision diagnostic = service.evaluate(List.of(policy),
        List.of(signal("db.pool.active", "86.0", 3)),
        request(TENANT_ID, BackpressureResource.DB_POOL, BackpressurePriorityClass.DIAGNOSTICS, false));

    assertEquals(BackpressureState.NORMAL, normal.state());
    assertEquals(BackpressureAction.ALLOW, normal.action());
    assertEquals(202, batch.httpStatus());
    assertEquals(BackpressureAction.DEFER_ASYNC, batch.action());
    assertEquals(429, diagnostic.httpStatus());
    assertEquals(BackpressureAction.REJECT_LOW_PRIORITY, diagnostic.action());
    assertEquals("30", diagnostic.responseMetadata().get("retryAfter"));
    assertTrue(diagnostic.metricNames().contains("backpressure.rejected.count"));
  }

  @Test
  void sheddingPublishesEventsAuditSnapshotAndSafeRunbookEvidence() {
    BackpressurePolicy policy = policy(BackpressureResource.EVENT_LAG,
        List.of(new BackpressureTrigger("event.lag.depth", new BigDecimal("1000"), 1,
            BackpressureState.SHEDDING, BackpressureAction.SHED_REQUEST)));

    BackpressureDecision decision = new BackpressureEvaluationService().evaluate(List.of(policy),
        List.of(signal("event.lag.depth", "1200", 1)),
        request(TENANT_ID, BackpressureResource.EVENT_LAG, BackpressurePriorityClass.CACHE_WARM, false));

    assertEquals(BackpressureState.SHEDDING, decision.stateSnapshot().state());
    assertEquals("event.lag.depth", decision.stateSnapshot().triggerMetric());
    assertEquals("BACKPRESSURE_POLICY_COMPLETED", decision.auditRecord().action());
    assertTrue(decision.auditRecord().replayHash().startsWith("sha256:"));
    assertEquals("BackpressureStateChanged.v1", decision.events().get(0).eventType());
    assertTrue(decision.traceNames().contains("backpressure.evaluate"));
    assertTrue(decision.runbookSteps().stream().anyMatch(step -> step.contains("active resource trigger")));
    assertFalse(decision.events().stream()
        .flatMap(event -> event.payload().values().stream())
        .anyMatch(value -> value.toLowerCase().contains("borrower")));
  }

  @Test
  void policyValidationRequiresPublishedApprovalSeparationSafeMetadataAndTenantIsolation() {
    assertThrows(IllegalArgumentException.class, () -> policy(BackpressureResource.DB_POOL,
        List.of(new BackpressureTrigger("db.pool.active", new BigDecimal("85.0"), 3,
            BackpressureState.DEGRADED, BackpressureAction.REDUCE_CONCURRENCY)),
        "same-user", "same-user"));

    assertThrows(IllegalArgumentException.class, () -> new BackpressureRequest(
        TENANT_ID, BackpressureResource.DB_POOL, BackpressurePriorityClass.PARTNER_API_QUOTES,
        "pricing-read", "raw-token", "corr-PII17S09", false, NOW));

    BackpressurePolicy otherTenantPolicy = new BackpressurePolicy(
        UUID.fromString("99999999-1717-0009-3333-bbbbbbbbbbbb"), OTHER_TENANT_ID,
        BackpressureResource.DB_POOL, 1, BackpressurePolicyStatus.PUBLISHED, NOW.minusSeconds(60), null,
        List.of(new BackpressureTrigger("db.pool.active", new BigDecimal("1"), 1,
            BackpressureState.SHEDDING, BackpressureAction.SHED_REQUEST)),
        2, Duration.ofSeconds(60), Duration.ofSeconds(30), "tenant-admin-17", "risk-approver-17");

    BackpressureDecision decision = new BackpressureEvaluationService().evaluate(
        List.of(otherTenantPolicy), List.of(signal("db.pool.active", "2", 1)),
        request(TENANT_ID, BackpressureResource.DB_POOL, BackpressurePriorityClass.PARTNER_API_QUOTES, false));

    assertEquals("POLICY_NOT_SATISFIED", decision.reasonCode());
  }

  @Test
  void goldenFixtureDocumentsProblemMetadataEventsAndSyntheticIdentifiers() throws Exception {
    String fixture = Files.readString(Path.of(
        "src/test/resources/backpressure/PII-17-S09/backpressure-policy-fixture.json"));

    assertTrue(fixture.contains("\"ownerStory\": \"PII-17-S09\""));
    assertTrue(fixture.contains("BackpressureStateChanged.v1"));
    assertTrue(fixture.contains("retryAfter"));
    assertTrue(fixture.contains("All thresholds are synthetic tenant-governed test fixture values"));
    assertFalse(fixture.toLowerCase().contains("borrower"));
    assertFalse(fixture.toLowerCase().contains("api key"));
  }

  private static BackpressurePolicy policy(BackpressureResource resource, List<BackpressureTrigger> triggers) {
    return policy(resource, triggers, "tenant-admin-17", "risk-approver-17");
  }

  private static BackpressurePolicy policy(
      BackpressureResource resource,
      List<BackpressureTrigger> triggers,
      String createdBy,
      String approvedBy) {
    return new BackpressurePolicy(
        UUID.fromString("11111111-1717-0009-3333-bbbbbbbbbbbb"), TENANT_ID,
        resource, 3, BackpressurePolicyStatus.PUBLISHED, NOW.minusSeconds(60), null, triggers,
        2, Duration.ofSeconds(60), Duration.ofSeconds(30), createdBy, approvedBy);
  }

  private static BackpressureSignal signal(String metricName, String value, int windows) {
    return new BackpressureSignal(metricName, new BigDecimal(value), windows, NOW);
  }

  private static BackpressureRequest request(
      UUID tenantId,
      BackpressureResource resource,
      BackpressurePriorityClass priorityClass,
      boolean asyncDeferralSupported) {
    return new BackpressureRequest(tenantId, resource, priorityClass, "pricing-read", "operator-17",
        "corr-PII17S09", asyncDeferralSupported, NOW);
  }
}
