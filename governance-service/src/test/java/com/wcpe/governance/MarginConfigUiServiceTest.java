package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarginConfigUiServiceTest {
  private static final String TENANT_ID = "33333333-3333-3333-3333-333333333333";
  private static final Instant NOW = Instant.parse("2026-06-06T12:00:00Z");

  private final MarginConfigUiService service = new MarginConfigUiService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void approvesAndPublishesMetadataDrivenMarginConfigWithSimulationAuditAndReplayEvidence() {
    GovernanceValidationResult<MarginConfigUiResult> result = service.approveAndPublish(command("idem-margin-1"));

    assertTrue(result.valid());
    MarginConfigUiResult value = result.value().orElseThrow();
    assertEquals("PUBLISHED", value.status());
    assertEquals(MarginConfigUiService.ARTIFACT_TYPE, value.resultSummary().get("artifactType"));
    assertEquals("margin-schema-tenant-2026-06", value.resultSummary().get("schemaVersion"));
    assertEquals("impact-fixture-channel-overlay-1", value.resultSummary().get("impactSimulationRef"));
    assertEquals(1, value.rowEvidence().size());
    assertEquals(64, value.rowEvidence().get(0).rowHash().length());
    assertEquals(MarginConfigUiService.COMPLETED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(MarginConfigUiService.AUDIT_ACTION, service.auditRecords().get(0).action());
    assertTrue(service.auditRecords().get(0).afterHash().startsWith("afterHash="));
  }

  @Test
  void replaysIdempotentCommandAndRejectsChangedReplay() {
    MarginConfigUiResult first = service.saveDraft(draftCommand("idem-margin-2")).value().orElseThrow();
    MarginConfigUiResult replay = service.saveDraft(draftCommand("idem-margin-2")).value().orElseThrow();
    GovernanceValidationResult<MarginConfigUiResult> conflict =
        service.saveDraft(new MarginConfigUiCommand(
            TENANT_ID,
            "idem-margin-2",
            "pricing-admin-1",
            null,
            draftPermissions(),
            metadata(),
            List.of(new MarginConfigRow(
                "row-2",
                Map.of("product", "tenant-product-ref", "channel", "tenant-channel-ref"),
                new BigDecimal("0.035"),
                "POINTS",
                new BigDecimal("0.200"),
                new BigDecimal("0.000"),
                "TENANT_MARGIN_CHANGE",
                2,
                Instant.parse("2026-07-01T00:00:00Z"),
                null)),
            null,
            "corr-PII-12-S07"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow());
  }

  @Test
  void failsClosedWhenMetadataDimensionReasonPolicyOrSeparationOfDutiesIsInvalid() {
    GovernanceValidationResult<MarginConfigUiResult> unsupportedDimension =
        service.saveDraft(new MarginConfigUiCommand(
            TENANT_ID,
            "idem-margin-3",
            "pricing-admin-1",
            null,
            draftPermissions(),
            metadata(),
            List.of(new MarginConfigRow(
                "row-unsupported",
                Map.of("unsupportedDimension", "value"),
                new BigDecimal("0.025"),
                "POINTS",
                new BigDecimal("0.200"),
                new BigDecimal("0.000"),
                "TENANT_MARGIN_CHANGE",
                3,
                Instant.parse("2026-07-01T00:00:00Z"),
                null)),
            null,
            "corr-PII-12-S07"));
    GovernanceValidationResult<MarginConfigUiResult> deniedNegative =
        service.saveDraft(new MarginConfigUiCommand(
            TENANT_ID,
            "idem-margin-4",
            "pricing-admin-1",
            null,
            draftPermissions(),
            metadata(),
            List.of(new MarginConfigRow(
                "row-negative",
                Map.of("product", "tenant-product-ref", "channel", "tenant-channel-ref"),
                new BigDecimal("-0.025"),
                "POINTS",
                new BigDecimal("0.200"),
                new BigDecimal("0.000"),
                "TENANT_MARGIN_CHANGE",
                4,
                Instant.parse("2026-07-01T00:00:00Z"),
                null)),
            null,
            "corr-PII-12-S07"));
    GovernanceValidationResult<MarginConfigUiResult> sod =
        service.approveAndPublish(new MarginConfigUiCommand(
            TENANT_ID,
            "idem-margin-5",
            "pricing-admin-1",
            "pricing-admin-1",
            publishPermissions(),
            metadata(),
            rows(),
            impact(),
            "corr-PII-12-S07"));

    assertFalse(unsupportedDimension.valid());
    assertEquals("VALIDATION_FAILED: unsupported margin dimension unsupportedDimension", unsupportedDimension.error().orElseThrow());
    assertFalse(deniedNegative.valid());
    assertEquals("POLICY_NOT_SATISFIED: negative margins require tenant policy", deniedNegative.error().orElseThrow());
    assertFalse(sod.valid());
    assertEquals("POLICY_NOT_SATISFIED: requester cannot approve their own margin config", sod.error().orElseThrow());
  }

  @Test
  void goldenImpactFixtureIsCommittedForTesterReplay() throws Exception {
    String fixture = Files.readString(Path.of("golden/PII-12-admin-governance/margin-config-ui.json"));

    assertTrue(fixture.contains("\"story_id\": \"PII-12-S07\""));
    assertTrue(fixture.contains("\"artifact_type\": \"MARGIN_CONFIG\""));
    assertTrue(fixture.contains("impact-fixture-channel-overlay-1"));
  }

  private MarginConfigUiCommand command(String idempotencyKey) {
    return new MarginConfigUiCommand(
        TENANT_ID,
        idempotencyKey,
        "pricing-admin-1",
        "finance-approver-1",
        publishPermissions(),
        metadata(),
        rows(),
        impact(),
        "corr-PII-12-S07");
  }

  private MarginConfigUiCommand draftCommand(String idempotencyKey) {
    return new MarginConfigUiCommand(
        TENANT_ID,
        idempotencyKey,
        "pricing-admin-1",
        null,
        draftPermissions(),
        metadata(),
        rows(),
        null,
        "corr-PII-12-S07");
  }

  private List<String> publishPermissions() {
    return List.of(
        MarginConfigUiService.WRITE_PERMISSION,
        MarginConfigUiService.SIMULATE_PERMISSION,
        MarginConfigUiService.APPROVE_PERMISSION,
        MarginConfigUiService.PUBLISH_PERMISSION);
  }

  private List<String> draftPermissions() {
    return List.of(MarginConfigUiService.WRITE_PERMISSION);
  }

  private MarginConfigMetadata metadata() {
    return new MarginConfigMetadata(
        "margin-schema-tenant-2026-06",
        List.of(new MarginDimensionMetadata("product", true), new MarginDimensionMetadata("channel", true)),
        List.of("TENANT_MARGIN_CHANGE", "COMPLIANCE_EXCEPTION"),
        new MarginValuePolicy(new BigDecimal("0.000"), new BigDecimal("0.250"), 3, false),
        false);
  }

  private List<MarginConfigRow> rows() {
    return List.of(new MarginConfigRow(
        "row-channel-overlay-1",
        Map.of("product", "tenant-product-ref", "channel", "tenant-channel-ref"),
        new BigDecimal("0.025"),
        "POINTS",
        new BigDecimal("0.200"),
        new BigDecimal("0.000"),
        "TENANT_MARGIN_CHANGE",
        1,
        Instant.parse("2026-07-01T00:00:00Z"),
        null));
  }

  private MarginImpactSimulationRef impact() {
    return new MarginImpactSimulationRef(
        "impact-fixture-channel-overlay-1",
        "golden/PII-12-admin-governance/margin-config-ui.json",
        "tenant-supplied-impact-result-hash");
  }
}
