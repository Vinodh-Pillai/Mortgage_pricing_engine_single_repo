package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigLifecycleServiceTest {
  private static final String TENANT = "11111111-1111-1111-1111-111111111111";
  private static final Instant NOW = Instant.parse("2026-06-04T04:00:00Z");
  private static final ConfigLifecyclePolicy POLICY =
      new ConfigLifecyclePolicy(
          "tenant-lifecycle-policy-2026.06",
          false,
          false,
          false,
          false,
          Duration.ofHours(2),
          Map.of("risk", "one approval required"));
  private static final ConfigLifecyclePolicy REPLACEMENT_POLICY =
      new ConfigLifecyclePolicy(
          "tenant-lifecycle-policy-2026.06-replacement",
          false,
          false,
          false,
          true,
          Duration.ofHours(2),
          Map.of("risk", "one approval required"));

  private final ConfigLifecycleService service = new ConfigLifecycleService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void validatesSubmitsApprovesAndPublishesWithAuditAndEvents() {
    ConfigLifecycleVersion draft = draft("version-1", "editor-1");
    ConfigLifecycleResult validated = service.handle(validate(draft, "idem-validate")).value().orElseThrow();
    ConfigLifecycleResult submitted = service.handle(command(validated.version(), ConfigLifecycleAction.SUBMIT, "editor-1", "editors", "idem-submit")).value().orElseThrow();
    ConfigLifecycleResult approved = service.handle(command(submitted.version(), ConfigLifecycleAction.APPROVE, "approver-1", "risk", "idem-approve")).value().orElseThrow();
    ConfigLifecycleResult published =
        service
            .handle(
                command(approved.version(), ConfigLifecycleAction.PUBLISH, "publisher-1", "publishers", "idem-publish"))
            .value()
            .orElseThrow();

    assertEquals(ConfigLifecycleState.PUBLISHED, published.version().status());
    assertEquals(ConfigLifecycleService.AUDIT_ACTION, service.auditRecords().get(3).action());
    assertEquals("ConfigSubmittedForApproval.v1", service.outboxEvents().get(1).eventType());
    assertEquals("ConfigApprovalDecisionRecorded.v1", service.outboxEvents().get(2).eventType());
    assertEquals("ConfigVersionPublished.v1", service.outboxEvents().get(3).eventType());
    assertEquals("required", published.outboxEvent().payload().get("cacheInvalidation"));
    assertEquals(1, service.approvalRequests().size());
    assertEquals(1, service.approvalDecisions().size());
  }

  @Test
  void missingPolicyOrStaleValidationFailsClosedBeforeStateChange() {
    ConfigLifecycleVersion draft = draft("version-1", "editor-1");
    GovernanceValidationResult<ConfigLifecycleResult> missingPolicy =
        service.handle(
            new ConfigLifecycleCommand(
                TENANT,
                "idem-submit",
                "editor-1",
                "editors",
                "artifact-1",
                "version-1",
                ConfigLifecycleAction.SUBMIT,
                ConfigLifecycleState.DRAFT,
                draft.etag(),
                null,
                draft,
                null,
                null,
                null,
                false,
                null,
                null,
                "submit",
                "",
                "corr-PII-12-S03",
                Map.of()));

    assertFalse(missingPolicy.valid());
    assertEquals("POLICY_NOT_SATISFIED: lifecycle policy is required", missingPolicy.error().orElseThrow());
    assertFalse(service.handle(command(draft, ConfigLifecycleAction.SUBMIT, "editor-1", "editors", "idem-submit-2")).valid());
    assertTrue(service.transitions().isEmpty());
  }

  @Test
  void separationOfDutiesDeniesApprovingOwnMaterialChange() {
    ConfigLifecycleVersion submitted =
        service
            .handle(command(service.handle(validate(draft("version-1", "editor-1"), "idem-validate")).value().orElseThrow().version(), ConfigLifecycleAction.SUBMIT, "editor-1", "editors", "idem-submit"))
            .value()
            .orElseThrow()
            .version();

    GovernanceValidationResult<ConfigLifecycleResult> result =
        service.handle(command(submitted, ConfigLifecycleAction.APPROVE, "editor-1", "risk", "idem-approve"));

    assertFalse(result.valid());
    assertEquals("SOD_VIOLATION", result.error().orElseThrow());
  }

  @Test
  void publishSupersedesPriorActiveVersionWhenTenantPolicyAllowsAtomicReplacement() {
    publishThroughWorkflow("active-version", POLICY);

    ConfigLifecycleVersion approved = approved("version-1");
    ConfigLifecycleResult result =
        service
            .handle(
                command(approved, ConfigLifecycleAction.PUBLISH, "publisher-1", "publishers", "idem-publish", REPLACEMENT_POLICY))
            .value()
            .orElseThrow();

    assertEquals(ConfigLifecycleState.PUBLISHED, result.version().status());
    assertEquals(1, result.affectedPublishedVersions().size());
    assertEquals(ConfigLifecycleState.SUPERSEDED, result.affectedPublishedVersions().get(0).status());
  }

  @Test
  void overlappingPublishFailsClosedWithoutExplicitPrecedencePolicy() {
    publishThroughWorkflow("active-version", POLICY);

    ConfigLifecycleVersion approved = approved("candidate-version");
    GovernanceValidationResult<ConfigLifecycleResult> result =
        service.handle(command(approved, ConfigLifecycleAction.PUBLISH, "publisher-1", "publishers", "idem-publish"));

    assertFalse(result.valid());
    assertEquals("PUBLISH_WINDOW_OVERLAP", result.error().orElseThrow());
  }

  @Test
  void idempotencyReplaysSameResultAndRejectsChangedRequest() {
    ConfigLifecycleVersion draft = draft("version-1", "editor-1");
    ConfigLifecycleCommand original = validate(draft, "idem-validate");
    ConfigLifecycleResult first = service.handle(original).value().orElseThrow();
    ConfigLifecycleResult replay = service.handle(original).value().orElseThrow();
    GovernanceValidationResult<ConfigLifecycleResult> conflict = service.handle(validate(draft, "idem-validate", "changed-result"));

    assertEquals(first.version(), replay.version());
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow());
    assertEquals(1, service.outboxEvents().size());
  }

  private ConfigLifecycleCommand validate(ConfigLifecycleVersion version, String idempotencyKey) {
    return validate(version, idempotencyKey, "validation-result-hash");
  }

  private ConfigLifecycleCommand validate(ConfigLifecycleVersion version, String idempotencyKey, String resultHash) {
    return new ConfigLifecycleCommand(
        TENANT,
        idempotencyKey,
        "validator-1",
        "validators",
        version.artifactId(),
        version.versionId(),
        ConfigLifecycleAction.VALIDATE,
        version.status(),
        version.etag(),
        POLICY,
        version,
        "validation-run-1",
        resultHash,
        NOW.minus(Duration.ofMinutes(10)),
        false,
        null,
        null,
        "validation-complete",
        "validation evidence reviewed",
        "corr-PII-12-S03",
        Map.of("validation", "validation-run-1"));
  }

  private ConfigLifecycleCommand command(
      ConfigLifecycleVersion version, ConfigLifecycleAction action, String actorId, String actorGroup, String idempotencyKey) {
    return command(version, action, actorId, actorGroup, idempotencyKey, POLICY);
  }

  private ConfigLifecycleCommand command(
      ConfigLifecycleVersion version,
      ConfigLifecycleAction action,
      String actorId,
      String actorGroup,
      String idempotencyKey,
      ConfigLifecyclePolicy policy) {
    return new ConfigLifecycleCommand(
        TENANT,
        idempotencyKey,
        actorId,
        actorGroup,
        version.artifactId(),
        version.versionId(),
        action,
        version.status(),
        version.etag(),
        policy,
        version,
        version.validationRunId(),
        version.validationResultHash(),
        version.validationCompletedAt(),
        version.validationBlocking(),
        Instant.parse("2026-06-04T05:00:00Z"),
        null,
        action.name().toLowerCase(),
        "story PII-12-S03 lifecycle transition",
        "corr-PII-12-S03",
        Map.of("diff", "diff-evidence", "simulation", "golden-fixture"));
  }

  private ConfigLifecycleVersion publishThroughWorkflow(String versionId, ConfigLifecyclePolicy policy) {
    ConfigLifecycleVersion validated = service.handle(validate(draft(versionId, "editor-1"), "idem-validate-" + versionId)).value().orElseThrow().version();
    ConfigLifecycleVersion submitted =
        service
            .handle(command(validated, ConfigLifecycleAction.SUBMIT, "editor-1", "editors", "idem-submit-" + versionId, policy))
            .value()
            .orElseThrow()
            .version();
    ConfigLifecycleVersion approved =
        service
            .handle(command(submitted, ConfigLifecycleAction.APPROVE, "approver-1", "risk", "idem-approve-" + versionId, policy))
            .value()
            .orElseThrow()
            .version();
    return service
        .handle(command(approved, ConfigLifecycleAction.PUBLISH, "publisher-1", "publishers", "idem-publish-" + versionId, policy))
        .value()
        .orElseThrow()
        .version();
  }

  private ConfigLifecycleVersion draft(String versionId, String editorId) {
    return new ConfigLifecycleVersion(
        TENANT,
        "artifact-1",
        versionId,
        "pricing-rule-set",
        "channel=retail",
        ConfigLifecycleState.DRAFT,
        1,
        "v1-payloadhash1234",
        "payloadhash1234567890",
        editorId,
        null,
        null,
        null,
        false,
        null,
        null,
        Map.of("ruleSetName", "tenant-configured", "sourceRef", "fixture-only"));
  }

  private ConfigLifecycleVersion approved(String versionId) {
    return draft(versionId, "editor-1")
        .withValidation("validation-run-1", "validation-result-hash", NOW.minus(Duration.ofMinutes(10)), false, "v2-payloadhash1234")
        .withStatus(ConfigLifecycleState.SUBMITTED, 3, "v3-payloadhash1234", null, null)
        .withStatus(ConfigLifecycleState.APPROVED, 4, "v4-payloadhash1234", null, null);
  }

}
