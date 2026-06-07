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

class FeeConfigUiServiceTest {
  private static final String TENANT_ID = "44444444-4444-4444-4444-444444444444";
  private static final Instant NOW = Instant.parse("2026-06-06T13:00:00Z");

  private final FeeConfigUiService service = new FeeConfigUiService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void approvesAndPublishesMetadataDrivenFeeConfigWithSimulationAuditAndReplayEvidence() {
    GovernanceValidationResult<FeeConfigUiResult> result = service.approveAndPublish(command("idem-fee-1"));

    assertTrue(result.valid());
    FeeConfigUiResult value = result.value().orElseThrow();
    assertEquals("PUBLISHED", value.status());
    assertEquals(FeeConfigUiService.ARTIFACT_TYPE, value.resultSummary().get("artifactType"));
    assertEquals("fee-schema-tenant-2026-06", value.resultSummary().get("schemaVersion"));
    assertEquals("impact-fixture-conventional-fees-1", value.resultSummary().get("impactSimulationRef"));
    assertEquals(1, value.rowEvidence().size());
    assertEquals("DISCLOSURE-REF-APR-2026", value.rowEvidence().get(0).disclosureRef());
    assertEquals("TOLERANCE-REF-APR-2026", value.rowEvidence().get(0).toleranceRef());
    assertEquals(64, value.rowEvidence().get(0).rowHash().length());
    assertEquals(FeeConfigUiService.COMPLETED_EVENT_TYPE, service.outboxEvents().get(0).eventType());
    assertEquals(FeeConfigUiService.AUDIT_ACTION, service.auditRecords().get(0).action());
    assertTrue(service.auditRecords().get(0).afterHash().startsWith("afterHash="));
  }

  @Test
  void replaysIdempotentCommandAndRejectsChangedReplay() {
    FeeConfigUiResult first = service.saveDraft(draftCommand("idem-fee-2")).value().orElseThrow();
    FeeConfigUiResult replay = service.saveDraft(draftCommand("idem-fee-2")).value().orElseThrow();
    GovernanceValidationResult<FeeConfigUiResult> conflict =
        service.saveDraft(new FeeConfigUiCommand(
            TENANT_ID,
            "idem-fee-2",
            "pricing-admin-1",
            null,
            draftPermissions(),
            metadata(),
            List.of(new FeeConfigRow(
                "row-origination-1",
                "ORIGINATION_FEE",
                "LENDER",
                "Origination Fee",
                "FIXED_AMOUNT",
                Map.of("product", "tenant-product-ref", "channel", "tenant-channel-ref", "jurisdiction", "TX"),
                new BigDecimal("950.00"),
                "USD",
                new BigDecimal("1200.00"),
                new BigDecimal("0.00"),
                "NONE",
                "DISCLOSURE-REF-APR-2026",
                "TOLERANCE-REF-APR-2026",
                "TENANT_FEE_CHANGE",
                1,
                Instant.parse("2026-07-01T00:00:00Z"),
                null)),
            null,
            "corr-PII-12-S08"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow());
  }

  @Test
  void failsClosedWhenDisclosureToleranceCapDimensionOrSeparationOfDutiesIsInvalid() {
    GovernanceValidationResult<FeeConfigUiResult> unsupportedDimension =
        service.saveDraft(new FeeConfigUiCommand(
            TENANT_ID,
            "idem-fee-3",
            "pricing-admin-1",
            null,
            draftPermissions(),
            metadata(),
            List.of(new FeeConfigRow(
                "row-unsupported",
                "ORIGINATION_FEE",
                "LENDER",
                "Origination Fee",
                "FIXED_AMOUNT",
                Map.of("unsupportedDimension", "value"),
                new BigDecimal("900.00"),
                "USD",
                new BigDecimal("1200.00"),
                new BigDecimal("0.00"),
                "NONE",
                "DISCLOSURE-REF-APR-2026",
                "TOLERANCE-REF-APR-2026",
                "TENANT_FEE_CHANGE",
                2,
                Instant.parse("2026-07-01T00:00:00Z"),
                null)),
            null,
            "corr-PII-12-S08"));
    GovernanceValidationResult<FeeConfigUiResult> missingDisclosure =
        service.saveDraft(new FeeConfigUiCommand(
            TENANT_ID,
            "idem-fee-4",
            "pricing-admin-1",
            null,
            draftPermissions(),
            metadata(),
            List.of(rowWithRefs("row-missing-disclosure", "", "TOLERANCE-REF-APR-2026", new BigDecimal("1200.00"), new BigDecimal("0.00"))),
            null,
            "corr-PII-12-S08"));
    GovernanceValidationResult<FeeConfigUiResult> badCap =
        service.saveDraft(new FeeConfigUiCommand(
            TENANT_ID,
            "idem-fee-5",
            "pricing-admin-1",
            null,
            draftPermissions(),
            metadata(),
            List.of(rowWithRefs("row-bad-cap", "DISCLOSURE-REF-APR-2026", "TOLERANCE-REF-APR-2026", new BigDecimal("10.00"), new BigDecimal("20.00"))),
            null,
            "corr-PII-12-S08"));
    GovernanceValidationResult<FeeConfigUiResult> sod =
        service.approveAndPublish(new FeeConfigUiCommand(
            TENANT_ID,
            "idem-fee-6",
            "pricing-admin-1",
            "pricing-admin-1",
            publishPermissions(),
            metadata(),
            rows(),
            impact(),
            "corr-PII-12-S08"));

    assertFalse(unsupportedDimension.valid());
    assertEquals("VALIDATION_FAILED: unsupported fee dimension unsupportedDimension", unsupportedDimension.error().orElseThrow());
    assertFalse(missingDisclosure.valid());
    assertEquals("POLICY_NOT_SATISFIED: disclosure reference is required for fee config", missingDisclosure.error().orElseThrow());
    assertFalse(badCap.valid());
    assertEquals("VALIDATION_FAILED: fee cap cannot be below floor", badCap.error().orElseThrow());
    assertFalse(sod.valid());
    assertEquals("POLICY_NOT_SATISFIED: requester cannot approve their own fee config", sod.error().orElseThrow());
  }

  @Test
  void failsClosedWhenJurisdictionProductOrChannelValueIsNotInMetadata() {
    GovernanceValidationResult<FeeConfigUiResult> unsupportedProduct =
        service.saveDraft(commandWithRows(
            "idem-fee-unsupported-product",
            List.of(rowWithContext(
                "row-unsupported-product",
                Map.of("product", "unsupported-product-ref", "channel", "tenant-channel-ref", "jurisdiction", "TX"),
                1,
                Instant.parse("2026-07-01T00:00:00Z"),
                null))));
    GovernanceValidationResult<FeeConfigUiResult> unsupportedChannel =
        service.saveDraft(commandWithRows(
            "idem-fee-unsupported-channel",
            List.of(rowWithContext(
                "row-unsupported-channel",
                Map.of("product", "tenant-product-ref", "channel", "unsupported-channel-ref", "jurisdiction", "TX"),
                1,
                Instant.parse("2026-07-01T00:00:00Z"),
                null))));
    GovernanceValidationResult<FeeConfigUiResult> unsupportedJurisdiction =
        service.saveDraft(commandWithRows(
            "idem-fee-unsupported-jurisdiction",
            List.of(rowWithContext(
                "row-unsupported-jurisdiction",
                Map.of("product", "tenant-product-ref", "channel", "tenant-channel-ref", "jurisdiction", "WA"),
                1,
                Instant.parse("2026-07-01T00:00:00Z"),
                null))));

    assertFalse(unsupportedProduct.valid());
    assertEquals("VALIDATION_FAILED: unsupported fee dimension value product", unsupportedProduct.error().orElseThrow());
    assertFalse(unsupportedChannel.valid());
    assertEquals("VALIDATION_FAILED: unsupported fee dimension value channel", unsupportedChannel.error().orElseThrow());
    assertFalse(unsupportedJurisdiction.valid());
    assertEquals("VALIDATION_FAILED: unsupported fee dimension value jurisdiction", unsupportedJurisdiction.error().orElseThrow());
  }

  @Test
  void rejectsOverlappingEffectiveWindowsForSameFeeKeyWithoutPrecedence() {
    GovernanceValidationResult<FeeConfigUiResult> overlap =
        service.saveDraft(commandWithRows(
            "idem-fee-overlap",
            List.of(
                rowWithContext(
                    "row-overlap-1",
                    Map.of("product", "tenant-product-ref", "channel", "tenant-channel-ref", "jurisdiction", "TX"),
                    1,
                    Instant.parse("2026-07-01T00:00:00Z"),
                    Instant.parse("2026-09-01T00:00:00Z")),
                rowWithContext(
                    "row-overlap-2",
                    Map.of("product", "tenant-product-ref", "channel", "tenant-channel-ref", "jurisdiction", "TX"),
                    2,
                    Instant.parse("2026-08-01T00:00:00Z"),
                    null))));

    assertFalse(overlap.valid());
    assertEquals("VALIDATION_FAILED: overlapping active fee requires configured precedence", overlap.error().orElseThrow());
  }

  @Test
  void goldenImpactFixtureIsCommittedForTesterReplay() throws Exception {
    String fixture = Files.readString(Path.of("golden/PII-12-admin-governance/fee-config-ui.json"));

    assertTrue(fixture.contains("\"story_id\": \"PII-12-S08\""));
    assertTrue(fixture.contains("\"artifact_type\": \"FEE_CONFIG\""));
    assertTrue(fixture.contains("impact-fixture-conventional-fees-1"));
  }

  private FeeConfigUiCommand command(String idempotencyKey) {
    return new FeeConfigUiCommand(
        TENANT_ID,
        idempotencyKey,
        "pricing-admin-1",
        "finance-approver-1",
        publishPermissions(),
        metadata(),
        rows(),
        impact(),
        "corr-PII-12-S08");
  }

  private FeeConfigUiCommand draftCommand(String idempotencyKey) {
    return new FeeConfigUiCommand(
        TENANT_ID,
        idempotencyKey,
        "pricing-admin-1",
        null,
        draftPermissions(),
        metadata(),
        rows(),
        null,
        "corr-PII-12-S08");
  }

  private FeeConfigUiCommand commandWithRows(String idempotencyKey, List<FeeConfigRow> rows) {
    return new FeeConfigUiCommand(
        TENANT_ID,
        idempotencyKey,
        "pricing-admin-1",
        null,
        draftPermissions(),
        metadata(),
        rows,
        null,
        "corr-PII-12-S08");
  }

  private List<String> publishPermissions() {
    return List.of(
        FeeConfigUiService.WRITE_PERMISSION,
        FeeConfigUiService.SIMULATE_PERMISSION,
        FeeConfigUiService.APPROVE_PERMISSION,
        FeeConfigUiService.PUBLISH_PERMISSION);
  }

  private List<String> draftPermissions() {
    return List.of(FeeConfigUiService.WRITE_PERMISSION);
  }

  private FeeConfigMetadata metadata() {
    return new FeeConfigMetadata(
        "fee-schema-tenant-2026-06",
        List.of(
            new FeeDimensionMetadata("product", true, List.of("tenant-product-ref")),
            new FeeDimensionMetadata("channel", true, List.of("tenant-channel-ref")),
            new FeeDimensionMetadata("jurisdiction", true, List.of("TX"))),
        List.of("TENANT_FEE_CHANGE", "COMPLIANCE_EXCEPTION"),
        List.of("FIXED_AMOUNT", "PERCENTAGE", "BASIS_POINTS"),
        new FeeValuePolicy(new BigDecimal("0.00"), new BigDecimal("5000.00"), 2, false),
        false,
        true,
        true);
  }

  private List<FeeConfigRow> rows() {
    return List.of(rowWithRefs(
        "row-origination-1",
        "DISCLOSURE-REF-APR-2026",
        "TOLERANCE-REF-APR-2026",
        new BigDecimal("1200.00"),
        new BigDecimal("0.00")));
  }

  private FeeConfigRow rowWithRefs(String rowId, String disclosureRef, String toleranceRef, BigDecimal cap, BigDecimal floor) {
    return rowWithContext(
        rowId,
        Map.of("product", "tenant-product-ref", "channel", "tenant-channel-ref", "jurisdiction", "TX"),
        1,
        Instant.parse("2026-07-01T00:00:00Z"),
        null,
        disclosureRef,
        toleranceRef,
        cap,
        floor);
  }

  private FeeConfigRow rowWithContext(String rowId, Map<String, String> context, int priority, Instant effectiveStart, Instant effectiveEnd) {
    return rowWithContext(
        rowId,
        context,
        priority,
        effectiveStart,
        effectiveEnd,
        "DISCLOSURE-REF-APR-2026",
        "TOLERANCE-REF-APR-2026",
        new BigDecimal("1200.00"),
        new BigDecimal("0.00"));
  }

  private FeeConfigRow rowWithContext(
      String rowId,
      Map<String, String> context,
      int priority,
      Instant effectiveStart,
      Instant effectiveEnd,
      String disclosureRef,
      String toleranceRef,
      BigDecimal cap,
      BigDecimal floor) {
    return new FeeConfigRow(
        rowId,
        "ORIGINATION_FEE",
        "LENDER",
        "Origination Fee",
        "FIXED_AMOUNT",
        context,
        new BigDecimal("900.00"),
        "USD",
        cap,
        floor,
        "NONE",
        disclosureRef,
        toleranceRef,
        "TENANT_FEE_CHANGE",
        priority,
        effectiveStart,
        effectiveEnd);
  }

  private FeeImpactSimulationRef impact() {
    return new FeeImpactSimulationRef(
        "impact-fixture-conventional-fees-1",
        "golden/PII-12-admin-governance/fee-config-ui.json",
        "tenant-supplied-fee-impact-result-hash");
  }
}
