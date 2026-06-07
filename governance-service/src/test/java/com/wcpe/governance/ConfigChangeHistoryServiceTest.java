package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigChangeHistoryServiceTest {
  private static final String TENANT_ONE = "44444444-4444-4444-4444-444444444444";
  private static final String TENANT_TWO = "55555555-5555-5555-5555-555555555555";
  private static final Instant NOW = Instant.parse("2026-06-07T12:00:00Z");

  private final ConfigChangeHistoryService service = new ConfigChangeHistoryService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void searchesTenantScopedHistoryInTimelineOrderWithAuditReplayAndRedaction() {
    GovernanceValidationResult<ConfigHistorySearchResult> result = service.search(query(filterForArtifact("artifact-margin")));

    assertTrue(result.valid());
    ConfigHistorySearchResult value = result.value().orElseThrow();
    assertEquals(2, value.entries().size());
    assertEquals("history-published", value.entries().get(0).historyId());
    assertEquals("***REDACTED***", value.entries().get(0).diffSummary().get("aprCap").substring(0, 14));
    assertEquals("0", value.metrics().get("searchErrorCount"));
    assertEquals("2", value.metrics().get("redactionDenialCount"));
    assertEquals(ConfigChangeHistoryService.COMPLETED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(ConfigChangeHistoryService.AUDIT_ACTION, service.auditRecords().get(0).action());
  }

  @Test
  void returnsDetailsWithoutRevealingCrossTenantExistence() {
    ConfigHistoryDetail detail = service.detail(query(filterForArtifact("artifact-margin")), "history-draft").value().orElseThrow();
    GovernanceValidationResult<ConfigHistoryDetail> otherTenant = service.detail(query(filterForArtifact("artifact-margin")), "history-other-tenant");
    GovernanceValidationResult<ConfigHistoryDetail> denied =
        service.detail(new ConfigHistoryQuery(TENANT_ONE, "auditor-1", List.of(), filterForArtifact("artifact-margin"), redactionPolicy(), entries(), "corr-PII-12-S09"), "history-draft");

    assertEquals("history-draft", detail.entry().historyId());
    assertFalse(otherTenant.valid());
    assertEquals("NOT_FOUND", otherTenant.error().orElseThrow());
    assertFalse(denied.valid());
    assertEquals("TENANT_ACCESS_DENIED", denied.error().orElseThrow());
  }

  @Test
  void createsRedactedDiffWithDeterministicHash() {
    ConfigDiffResult diff = service.createDiff(diffRequest()).value().orElseThrow();

    assertEquals(TENANT_ONE, diff.tenantId());
    assertEquals("version-draft", diff.fromVersionId());
    assertEquals("version-published", diff.toVersionId());
    assertTrue(diff.diffJsonRedacted().get("aprCap").startsWith("***REDACTED***#"));
    assertEquals(64, diff.diffHash().length());
    assertEquals(redactionPolicy().policyId(), diff.redactionPolicyId());
  }

  @Test
  void auditsEvidenceExportAndDeniesMissingPermission() {
    ConfigEvidencePackage evidence = service.evidence(evidenceRequest(List.of(ConfigChangeHistoryService.EVIDENCE_PERMISSION))).value().orElseThrow();
    GovernanceValidationResult<ConfigEvidencePackage> denied = service.evidence(evidenceRequest(List.of(ConfigChangeHistoryService.READ_PERMISSION)));

    assertEquals("history-published", evidence.historyId());
    assertEquals(3, evidence.evidenceLinks().size());
    assertEquals(ConfigChangeHistoryService.EVIDENCE_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(ConfigChangeHistoryService.EVIDENCE_AUDIT_ACTION, service.auditRecords().get(0).action());
    assertFalse(denied.valid());
    assertEquals("TENANT_ACCESS_DENIED", denied.error().orElseThrow());
  }

  @Test
  void goldenFixtureDocumentsHistoryPageContractForTesterReplay() throws Exception {
    String fixture = Files.readString(Path.of("golden/PII-12-admin-governance/change-history-ui.json"));

    assertTrue(fixture.contains("\"story_id\": \"PII-12-S09\""));
    assertTrue(fixture.contains("/api/v1/tenants/{tenantId}/admin/config-history"));
    assertTrue(fixture.contains("***REDACTED***"));
  }

  private ConfigHistoryQuery query(ConfigHistoryFilter filter) {
    return new ConfigHistoryQuery(
        TENANT_ONE,
        "auditor-1",
        List.of(ConfigChangeHistoryService.READ_PERMISSION),
        filter,
        redactionPolicy(),
        entries(),
        "corr-PII-12-S09");
  }

  private ConfigDiffRequest diffRequest() {
    return new ConfigDiffRequest(
        TENANT_ONE,
        "auditor-1",
        List.of(ConfigChangeHistoryService.DIFF_PERMISSION),
        "version-draft",
        "version-published",
        redactionPolicy(),
        entries(),
        "corr-PII-12-S09");
  }

  private ConfigEvidenceRequest evidenceRequest(List<String> permissions) {
    return new ConfigEvidenceRequest(TENANT_ONE, "history-published", "auditor-1", permissions, entries(), "corr-PII-12-S09");
  }

  private ConfigHistoryFilter filterForArtifact(String artifactId) {
    return new ConfigHistoryFilter(
        artifactId,
        null,
        null,
        null,
        null,
        Instant.parse("2026-06-01T00:00:00Z"),
        Instant.parse("2026-06-08T00:00:00Z"));
  }

  private RedactionPolicyRef redactionPolicy() {
    return new RedactionPolicyRef("redaction-policy-auditor-masked-2026-06", List.of("aprCap", "internalPayload"), false);
  }

  private List<ConfigHistoryEntry> entries() {
    return List.of(
        entry(
            TENANT_ONE,
            "history-draft",
            "version-draft",
            "DRAFT_SAVED",
            "NONE",
            "DRAFT",
            "TENANT_MARGIN_CHANGE",
            Instant.parse("2026-06-06T09:00:00Z"),
            1,
            Map.of("marginFloor", "0.000", "internalPayload", "borrower-like-sensitive-value")),
        entry(
            TENANT_ONE,
            "history-published",
            "version-published",
            "PUBLISHED",
            "APPROVED",
            "PUBLISHED",
            "TENANT_MARGIN_CHANGE",
            Instant.parse("2026-06-06T11:00:00Z"),
            2,
            Map.of("marginFloor", "0.000", "aprCap", "tenant-configured-cap-ref-2026-06")),
        entry(
            TENANT_TWO,
            "history-other-tenant",
            "version-other",
            "PUBLISHED",
            "APPROVED",
            "PUBLISHED",
            "TENANT_MARGIN_CHANGE",
            Instant.parse("2026-06-06T12:00:00Z"),
            3,
            Map.of("marginFloor", "0.100")));
  }

  private ConfigHistoryEntry entry(
      String tenantId,
      String historyId,
      String versionId,
      String eventType,
      String statusFrom,
      String statusTo,
      String reasonCode,
      Instant occurredAt,
      long sequence,
      Map<String, String> diff) {
    return new ConfigHistoryEntry(
        tenantId,
        historyId,
        "artifact-margin",
        "MARGIN_CONFIG",
        versionId,
        eventType,
        1,
        "pricing-admin-1",
        "pricing-governance",
        statusFrom,
        statusTo,
        reasonCode,
        "Margin policy moved to " + statusTo,
        diff,
        "audit-" + historyId,
        "outbox-" + historyId,
        new ReplayReference("event-" + historyId, "replay-hash-" + historyId, "audit-" + historyId),
        List.of(
            new EvidenceLink("approval-" + historyId, "APPROVAL", "approval-workflow-ref", "approval-hash-" + historyId, true),
            new EvidenceLink("validation-" + historyId, "VALIDATION", "validation-run-ref", "validation-hash-" + historyId, true),
            new EvidenceLink("publish-" + historyId, "PUBLISH", "outbox-" + historyId, "publish-hash-" + historyId, true)),
        occurredAt,
        sequence,
        false);
  }
}
