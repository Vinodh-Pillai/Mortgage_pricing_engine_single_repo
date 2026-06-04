package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigValidationServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final ConfigValidationPolicy POLICY =
      new ConfigValidationPolicy(
          "tenant-validation-policy",
          "2026.06",
          List.of("ruleSetName", "sourceRef"),
          List.of("2026.06"),
          Map.of("required", "governance.config.required-field"));

  private final ConfigValidationService service =
      new ConfigValidationService(Clock.fixed(Instant.parse("2026-06-04T02:00:00Z"), ZoneOffset.UTC));

  @Test
  void blockingValidationPreventsApprovalOrPublishAndStoresAuditEvidence() {
    ConfigValidationRun run = service.validate(command(Map.of("ruleSetName", "tenant-configured"), "idem-1")).value().orElseThrow();

    assertEquals("BLOCKED", run.status());
    assertFalse(run.publishEligible());
    assertFalse(service.approvalOrPublishAllowed(TENANT_ONE, run.runId()));
    assertEquals(1, run.findings().size());
    assertEquals("REQUIRED_FIELD_MISSING", run.findings().get(0).code());
    assertEquals("$.payload.sourceRef", run.findings().get(0).jsonPath());
    assertEquals(ConfigValidationService.COMPLETED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(ConfigValidationService.AUDIT_ACTION, service.auditRecords().get(0).action());
  }

  @Test
  void configurableVersionedPolicyProducesDeterministicReplayableResultHash() {
    ConfigValidationRun first =
        service
            .validate(
                command(Map.of("sourceRef", "fixture-only", "ruleSetName", "tenant-configured"), "idem-1"))
            .value()
            .orElseThrow();
    ConfigValidationRun secondTenantRun =
        service
            .validate(
                new ConfigValidationCommand(
                    TENANT_TWO,
                    "idem-2",
                    "admin-editor-1",
                    "artifact-1",
                    "version-1",
                    "pricing-rule-set",
                    "2026.06",
                    Map.of("ruleSetName", "tenant-configured", "sourceRef", "fixture-only"),
                    "",
                    List.of(POLICY),
                    "DRAFT",
                    "corr-PII-12-S02"))
            .value()
            .orElseThrow();

    assertEquals("PASSED", first.status());
    assertTrue(first.publishEligible());
    assertEquals(first.resultHash(), secondTenantRun.resultHash());
    assertEquals(first.policyVersionSetHash(), secondTenantRun.policyVersionSetHash());
    assertEquals(1, service.validationRunsForTenant(TENANT_ONE).size());
    assertEquals(1, service.validationRunsForTenant(TENANT_TWO).size());
  }

  @Test
  void sameIdempotencyKeyReplaysAndChangedRequestConflicts() {
    ConfigValidationCommand original = command(Map.of("ruleSetName", "tenant-configured", "sourceRef", "fixture-only"), "idem-1");
    ConfigValidationRun first = service.validate(original).value().orElseThrow();
    ConfigValidationRun replay = service.validate(original).value().orElseThrow();
    GovernanceValidationResult<ConfigValidationRun> conflict =
        service.validate(command(Map.of("ruleSetName", "changed", "sourceRef", "fixture-only"), "idem-1"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow());
    assertEquals(1, service.outboxEvents().size());
  }

  @Test
  void missingPolicyFailsClosedBeforeRunIsPersisted() {
    GovernanceValidationResult<ConfigValidationRun> result =
        service.validate(
            new ConfigValidationCommand(
                TENANT_ONE,
                "idem-1",
                "admin-editor-1",
                "artifact-1",
                "version-1",
                "pricing-rule-set",
                "2026.06",
                Map.of("ruleSetName", "tenant-configured"),
                "",
                List.of(),
                "DRAFT",
                "corr-PII-12-S02"));

    assertFalse(result.valid());
    assertEquals("VALIDATION_POLICY_MISSING", result.error().orElseThrow());
    assertTrue(service.auditRecords().isEmpty());
    assertTrue(service.outboxEvents().isEmpty());
  }

  @Test
  void resultHashChangesWhenPolicyVersionSetChanges() {
    ConfigValidationRun first =
        service
            .validate(command(Map.of("ruleSetName", "tenant-configured", "sourceRef", "fixture-only"), "idem-1"))
            .value()
            .orElseThrow();
    ConfigValidationRun changedPolicy =
        service
            .validate(
                new ConfigValidationCommand(
                    TENANT_ONE,
                    "idem-2",
                    "admin-editor-1",
                    "artifact-1",
                    "version-1",
                    "pricing-rule-set",
                    "2026.06",
                    Map.of("ruleSetName", "tenant-configured", "sourceRef", "fixture-only"),
                    "",
                    List.of(
                        new ConfigValidationPolicy(
                            "tenant-validation-policy",
                            "2026.07",
                            List.of("ruleSetName", "sourceRef"),
                            List.of("2026.06"),
                            Map.of("required", "governance.config.required-field"))),
                    "DRAFT",
                    "corr-PII-12-S02"))
            .value()
            .orElseThrow();

    assertNotEquals(first.policyVersionSetHash(), changedPolicy.policyVersionSetHash());
    assertNotEquals(first.resultHash(), changedPolicy.resultHash());
  }

  private ConfigValidationCommand command(Map<String, String> payload, String idempotencyKey) {
    return new ConfigValidationCommand(
        TENANT_ONE,
        idempotencyKey,
        "admin-editor-1",
        "artifact-1",
        "version-1",
        "pricing-rule-set",
        "2026.06",
        payload,
        "",
        List.of(POLICY),
        "DRAFT",
        "corr-PII-12-S02");
  }
}
