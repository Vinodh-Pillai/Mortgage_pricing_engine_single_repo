package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigUiShellServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final Instant NOW = Instant.parse("2026-06-04T12:00:00Z");

  private final ConfigUiShellService service = new ConfigUiShellService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void returnsMetadataDrivenInventoryApprovalQueueAndAuditEvidence() {
    GovernanceValidationResult<ConfigUiShellSnapshot> result = service.load(query("idem-shell-1"));

    assertTrue(result.valid());
    ConfigUiShellSnapshot snapshot = result.value().orElseThrow();
    assertEquals(TENANT_ONE, snapshot.tenantId());
    assertEquals("metadata-2026-06", snapshot.metadataVersion());
    assertEquals(2, snapshot.artifactTypes().get(0).fields().size());
    assertEquals(1, snapshot.inventoryRows().size());
    assertEquals("artifact-margin", snapshot.inventoryRows().get(0).artifactId());
    assertEquals(1, snapshot.actionsByArtifactId().get("artifact-margin").size());
    assertEquals("approve", snapshot.approvalQueueItems().get(0).availableActions().get(0).actionKey());
    assertEquals(1, snapshot.statusCounts().get("SUBMITTED"));
    assertEquals(32, snapshot.cacheKey().length());
    assertEquals("corr-PII-12-S04", snapshot.correlationId());
    assertEquals(ConfigUiShellService.COMPLETED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(ConfigUiShellService.AUDIT_ACTION, service.auditRecords().get(0).action());
  }

  @Test
  void replaysIdempotentMetadataRequestAndRejectsChangedRequest() {
    ConfigUiShellSnapshot first = service.load(query("idem-shell-1")).value().orElseThrow();
    ConfigUiShellSnapshot replay = service.load(query("idem-shell-1")).value().orElseThrow();
    GovernanceValidationResult<ConfigUiShellSnapshot> conflict =
        service.load(
            new ConfigUiShellQuery(
                TENANT_ONE,
                "idem-shell-1",
                "admin-editor-1",
                "metadata-2026-07",
                "permissions-hash-1",
                permissions(),
                artifactTypes(),
                inventoryRows(),
                approvalQueueItems(),
                filter(),
                "corr-PII-12-S04"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow());
    assertEquals(1, service.outboxEvents().size());
  }

  @Test
  void failsClosedWhenMetadataOrPermissionsAreMissing() {
    GovernanceValidationResult<ConfigUiShellSnapshot> missingMetadata =
        service.load(
            new ConfigUiShellQuery(
                TENANT_ONE,
                "idem-shell-1",
                "admin-editor-1",
                "metadata-2026-06",
                "permissions-hash-1",
                permissions(),
                List.of(),
                inventoryRows(),
                approvalQueueItems(),
                filter(),
                "corr-PII-12-S04"));
    GovernanceValidationResult<ConfigUiShellSnapshot> denied =
        service.load(
            new ConfigUiShellQuery(
                TENANT_ONE,
                "idem-shell-2",
                "admin-editor-1",
                "metadata-2026-06",
                "permissions-hash-2",
                List.of("governance:config-ui-shell:write"),
                artifactTypes(),
                inventoryRows(),
                approvalQueueItems(),
                filter(),
                "corr-PII-12-S04"));

    assertFalse(missingMetadata.valid());
    assertEquals("POLICY_NOT_SATISFIED: artifact type metadata is required", missingMetadata.error().orElseThrow());
    assertFalse(denied.valid());
    assertEquals("TENANT_ACCESS_DENIED", denied.error().orElseThrow());
    assertTrue(service.auditRecords().isEmpty());
    assertTrue(service.outboxEvents().isEmpty());
  }

  @Test
  void cacheKeySeparatesTenantPermissionsMetadataVersionAndFiltersWithoutPayloadValues() {
    ConfigUiShellSnapshot first = service.load(query("idem-shell-1")).value().orElseThrow();
    ConfigUiShellSnapshot second =
        service
            .load(
                new ConfigUiShellQuery(
                    TENANT_ONE,
                    "idem-shell-2",
                    "admin-editor-1",
                    "metadata-2026-07",
                    "permissions-hash-1",
                    permissions(),
                    artifactTypes(),
                    inventoryRows(),
                    approvalQueueItems(),
                    filter(),
                    "corr-PII-12-S04"))
            .value()
            .orElseThrow();

    assertFalse(first.cacheKey().contains("Margin governance shell"));
    assertFalse(first.cacheKey().equals(second.cacheKey()));
  }

  private ConfigUiShellQuery query(String idempotencyKey) {
    return new ConfigUiShellQuery(
        TENANT_ONE,
        idempotencyKey,
        "admin-editor-1",
        "metadata-2026-06",
        "permissions-hash-1",
        permissions(),
        artifactTypes(),
        inventoryRows(),
        approvalQueueItems(),
        filter(),
        "corr-PII-12-S04");
  }

  private List<String> permissions() {
    return List.of(ConfigUiShellService.READ_PERMISSION, "governance:config-ui-shell:approve");
  }

  private List<ConfigArtifactTypeMetadata> artifactTypes() {
    return List.of(
        new ConfigArtifactTypeMetadata(
            "margin-policy",
            "Tenant configured margin policy",
            "/admin/config/margins/{artifactId}",
            List.of(
                new ConfigFieldMetadata("name", "Name", true, true, "string"),
                new ConfigFieldMetadata("effectiveWindow", "Effective window", true, true, "date-range")),
            List.of(
                new ConfigActionDescriptor(
                    "edit",
                    "Edit draft",
                    "/admin/config/margins/{artifactId}/draft",
                    "governance:config-ui-shell:write",
                    List.of("DRAFT"),
                    false),
                new ConfigActionDescriptor(
                    "approve",
                    "Approve",
                    "/admin/config/margins/{artifactId}/approvals",
                    "governance:config-ui-shell:approve",
                    List.of("SUBMITTED"),
                    true))));
  }

  private List<ConfigInventoryRow> inventoryRows() {
    return List.of(
        new ConfigInventoryRow(
            TENANT_ONE,
            "artifact-draft",
            "margin-policy",
            "Draft artifact outside filter",
            "retail",
            "DRAFT",
            1,
            Instant.parse("2026-06-01T00:00:00Z"),
            null,
            "WARNING",
            "NOT_SUBMITTED",
            "admin-editor-1",
            Instant.parse("2026-06-04T10:00:00Z"),
            "NOT_PUBLISHED"),
        new ConfigInventoryRow(
            TENANT_ONE,
            "artifact-margin",
            "margin-policy",
            "Margin governance shell",
            "retail",
            "SUBMITTED",
            3,
            Instant.parse("2026-06-01T00:00:00Z"),
            null,
            "PASSED",
            "PENDING",
            "admin-editor-2",
            Instant.parse("2026-06-04T11:00:00Z"),
            "NOT_PUBLISHED"),
        new ConfigInventoryRow(
            TENANT_TWO,
            "artifact-other-tenant",
            "margin-policy",
            "Other tenant artifact",
            "retail",
            "SUBMITTED",
            1,
            Instant.parse("2026-06-01T00:00:00Z"),
            null,
            "PASSED",
            "PENDING",
            "admin-editor-3",
            Instant.parse("2026-06-04T11:30:00Z"),
            "NOT_PUBLISHED"));
  }

  private List<ApprovalQueueItem> approvalQueueItems() {
    return List.of(
        new ApprovalQueueItem(
            TENANT_ONE,
            "approval-1",
            "artifact-margin",
            "margin-policy",
            "Margin governance shell",
            "OPEN",
            "admin-editor-2",
            Instant.parse("2026-06-04T11:05:00Z"),
            Instant.parse("2026-06-05T00:00:00Z"),
            List.of()),
        new ApprovalQueueItem(
            TENANT_TWO,
            "approval-2",
            "artifact-other-tenant",
            "margin-policy",
            "Other tenant artifact",
            "OPEN",
            "admin-editor-3",
            Instant.parse("2026-06-04T11:05:00Z"),
            null,
            List.of()));
  }

  private ConfigInventoryFilter filter() {
    return new ConfigInventoryFilter("margin", "margin-policy", "SUBMITTED", NOW, "name", true);
  }
}
