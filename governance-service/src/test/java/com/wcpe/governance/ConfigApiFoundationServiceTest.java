package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigApiFoundationServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final Instant EFFECTIVE_START = Instant.parse("2026-06-04T00:00:00Z");

  private final ConfigApiFoundationService service =
      new ConfigApiFoundationService(Clock.fixed(Instant.parse("2026-06-04T01:00:00Z"), ZoneOffset.UTC));

  @Test
  void createsTenantScopedDraftWithAuditAndOutboxEvidence() {
    GovernanceValidationResult<ConfigApiDraftResponse> result = service.createDraft(command(TENANT_ONE, "idem-1"));

    assertTrue(result.valid());
    ConfigApiDraftResponse response = result.value().orElseThrow();
    assertEquals(ConfigApiFoundationService.DRAFT_STATUS, response.status());
    assertEquals(1, response.versionNumber());
    assertEquals(64, response.payloadHash().length());
    assertEquals("corr-PII-12-S01", response.correlationId());
    assertEquals(1, service.draftsForTenant(TENANT_ONE).size());
    assertEquals(0, service.draftsForTenant(TENANT_TWO).size());
    assertEquals(ConfigApiFoundationService.CREATED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(ConfigApiFoundationService.CREATED_AUDIT_ACTION, service.auditRecords().get(0).action());
  }

  @Test
  void replaysSameIdempotencyKeyAndRejectsChangedRequest() {
    ConfigApiCreateCommand original = command(TENANT_ONE, "idem-1");
    ConfigApiDraftResponse first = service.createDraft(original).value().orElseThrow();

    ConfigApiDraftResponse replay = service.createDraft(original).value().orElseThrow();
    GovernanceValidationResult<ConfigApiDraftResponse> conflict =
        service.createDraft(
            new ConfigApiCreateCommand(
                TENANT_ONE,
                "idem-1",
                "admin-editor-1",
                "pricing-margin-set",
                "Different artifact",
                "2026.06",
                Map.of("marginPolicyRef", "tenant-defined"),
                Map.of("channel", "retail"),
                EFFECTIVE_START,
                null,
                "different request",
                "corr-PII-12-S01"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_REPLAY_MISMATCH", conflict.error().orElseThrow());
    assertEquals(1, service.outboxEvents().size());
  }

  @Test
  void failsClosedForMissingRequiredConfigurationFields() {
    GovernanceValidationResult<ConfigApiDraftResponse> result =
        service.createDraft(
            new ConfigApiCreateCommand(
                TENANT_ONE,
                "idem-1",
                "admin-editor-1",
                "pricing-rule-set",
                "Conventional baseline rules",
                "2026.06",
                Map.of(),
                Map.of("channel", "retail"),
                EFFECTIVE_START,
                null,
                "create draft",
                "corr-PII-12-S01"));

    assertFalse(result.valid());
    assertEquals("CONFIG_SCHEMA_INVALID: payload is required", result.error().orElseThrow());
    assertTrue(service.auditRecords().isEmpty());
    assertTrue(service.outboxEvents().isEmpty());
  }

  @Test
  void createsDeterministicPayloadHashForEquivalentPayloadOrder() {
    ConfigApiDraftResponse first =
        service
            .createDraft(
                new ConfigApiCreateCommand(
                    TENANT_ONE,
                    "idem-1",
                    "admin-editor-1",
                    "pricing-rule-set",
                    "Conventional baseline rules",
                    "2026.06",
                    Map.of("b", "two", "a", "one"),
                    Map.of("channel", "retail"),
                    EFFECTIVE_START,
                    null,
                    "create draft",
                    "corr-PII-12-S01"))
            .value()
            .orElseThrow();
    ConfigApiDraftResponse second =
        service
            .createDraft(
                new ConfigApiCreateCommand(
                    TENANT_TWO,
                    "idem-2",
                    "admin-editor-1",
                    "pricing-rule-set",
                    "Conventional baseline rules",
                    "2026.06",
                    Map.of("a", "one", "b", "two"),
                    Map.of("channel", "retail"),
                    EFFECTIVE_START,
                    null,
                    "create draft",
                    "corr-PII-12-S01"))
            .value()
            .orElseThrow();

    assertEquals(first.payloadHash(), second.payloadHash());
  }

  private ConfigApiCreateCommand command(String tenantId, String idempotencyKey) {
    return new ConfigApiCreateCommand(
        tenantId,
        idempotencyKey,
        "admin-editor-1",
        "pricing-rule-set",
        "Conventional baseline rules",
        "2026.06",
        Map.of("ruleSetName", "tenant-configured-rule-set", "sourceRef", "fixture-only"),
        Map.of("channel", "retail", "product", "conventional"),
        EFFECTIVE_START,
        null,
        "create draft",
        "corr-PII-12-S01");
  }
}
