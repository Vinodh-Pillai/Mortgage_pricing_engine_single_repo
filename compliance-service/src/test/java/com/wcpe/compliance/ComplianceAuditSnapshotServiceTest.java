package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.ComplianceAuditSnapshotService.ComplianceAuditSnapshot;
import com.wcpe.compliance.ComplianceAuditSnapshotService.ComplianceAuditSnapshotView;
import com.wcpe.compliance.ComplianceAuditSnapshotService.ConfigVersionGraph;
import com.wcpe.compliance.ComplianceAuditSnapshotService.CreateComplianceAuditSnapshot;
import com.wcpe.compliance.ComplianceAuditSnapshotService.LegalHoldCommand;
import com.wcpe.compliance.ComplianceAuditSnapshotService.SnapshotEvidenceItem;
import com.wcpe.compliance.ComplianceAuditSnapshotService.SnapshotSubjectRef;
import com.wcpe.compliance.ComplianceAuditSnapshotService.SnapshotVerificationResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceAuditSnapshotServiceTest {
  @Test
  void createsImmutableSnapshotWithDeterministicHashAndAuditEvent() {
    ComplianceAuditSnapshot first = ComplianceAuditSnapshotService.createSnapshot(request());
    ComplianceAuditSnapshot second = ComplianceAuditSnapshotService.createSnapshot(request());

    assertEquals("snapshot_created", ComplianceAuditSnapshotService.SNAPSHOT_CREATED);
    assertEquals("tenant-a", first.tenantId());
    assertEquals("snapshot-001", first.snapshotId());
    assertEquals("no_legal_hold", first.legalHoldState());
    assertEquals(first.payloadHash(), second.payloadHash());
    assertEquals(first.resultHash(), second.resultHash());
    assertTrue(first.auditRef().startsWith("compliance-audit-snapshot:sha256:"));
    assertEquals(List.of("ComplianceAuditSnapshotCreated.v1"), first.outboxEventTypes());
    assertEquals("retention-policy-approved-v1", first.retentionPolicyRef());
  }

  @Test
  void rejectsEvidenceWithoutStableSourceRefAndPayloadHash() {
    SnapshotEvidenceItem badItem =
        new SnapshotEvidenceItem(
            1,
            "fair_lending_snapshot",
            "",
            "fair-lending-snapshot.v1",
            "{\"snapshotId\":\"snapshot-001\"}",
            "sha256:wrong",
            "restricted",
            "redaction-policy-v1");

    ComplianceShellValidationError error =
        assertInstanceOf(
            ComplianceShellValidationError.class,
            assertThrows(
                RuntimeException.class,
                () -> ComplianceAuditSnapshotService.createSnapshot(request(List.of(badItem)))));

    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertTrue(error.getDetails().contains("sourceRef must be a non-empty string"));
    assertTrue(error.getDetails().contains("payloadHash mismatch for sequence 1"));
  }

  @Test
  void verifyDetectsTamperedPayloadHash() {
    ComplianceAuditSnapshot snapshot = ComplianceAuditSnapshotService.createSnapshot(request());
    ComplianceAuditSnapshot tampered =
        new ComplianceAuditSnapshot(
            snapshot.snapshotId(),
            snapshot.tenantId(),
            snapshot.subjectRef(),
            snapshot.snapshotType(),
            snapshot.asOfTimestamp(),
            snapshot.configVersionGraph(),
            snapshot.eventSequenceRefs(),
            snapshot.calculationLedgerRefs(),
            snapshot.reasonCodeRefs(),
            snapshot.items(),
            "sha256:tampered",
            snapshot.resultHash(),
            snapshot.retentionPolicyRef(),
            snapshot.legalHoldState(),
            snapshot.createdByService(),
            snapshot.actorId(),
            snapshot.idempotencyKey(),
            snapshot.correlationId(),
            snapshot.auditRef(),
            snapshot.outboxEventTypes(),
            snapshot.legalHoldActions());

    SnapshotVerificationResult result = ComplianceAuditSnapshotService.verify(tampered);

    assertEquals("HASH_MISMATCH", result.status());
    assertTrue(result.reasonCodes().contains("PAYLOAD_HASH_MISMATCH"));
    assertEquals(List.of("ComplianceAuditSnapshotVerified.v1"), result.outboxEventTypes());
  }

  @Test
  void verifySucceedsWhenCanonicalHashesMatch() {
    ComplianceAuditSnapshot snapshot = ComplianceAuditSnapshotService.createSnapshot(request());

    SnapshotVerificationResult result = ComplianceAuditSnapshotService.verify(snapshot);

    assertEquals("snapshot_verified", result.status());
    assertEquals(List.of(), result.reasonCodes());
    assertEquals(snapshot.payloadHash(), result.recalculatedPayloadHash());
    assertEquals(snapshot.resultHash(), result.recalculatedResultHash());
  }

  @Test
  void legalHoldRequiresCommentAndPreventsDuplicatePlace() {
    ComplianceAuditSnapshot snapshot = ComplianceAuditSnapshotService.createSnapshot(request());
    LegalHoldCommand command =
        new LegalHoldCommand(
            "compliance-manager", "Litigation hold requested.", Instant.parse("2026-06-01T11:00:00Z"), "corr-123");

    ComplianceAuditSnapshot held = ComplianceAuditSnapshotService.placeLegalHold(snapshot, command);

    assertEquals("legal_hold_active", held.legalHoldState());
    assertEquals(List.of("ComplianceAuditSnapshotLegalHoldPlaced.v1"), held.outboxEventTypes());
    assertEquals(1, held.legalHoldActions().size());
    assertThrows(
        ComplianceShellValidationError.class,
        () -> ComplianceAuditSnapshotService.placeLegalHold(held, command));
  }

  @Test
  void releaseLegalHoldRecordsImmutableAction() {
    ComplianceAuditSnapshot held =
        ComplianceAuditSnapshotService.placeLegalHold(
            ComplianceAuditSnapshotService.createSnapshot(request()),
            new LegalHoldCommand(
                "compliance-manager", "Litigation hold.", Instant.parse("2026-06-01T11:00:00Z"), "corr-123"));

    ComplianceAuditSnapshot released =
        ComplianceAuditSnapshotService.releaseLegalHold(
            held,
            new LegalHoldCommand(
                "compliance-manager", "Released by legal.", Instant.parse("2026-06-02T11:00:00Z"), "corr-124"));

    assertEquals("legal_hold_released", released.legalHoldState());
    assertEquals(List.of("ComplianceAuditSnapshotLegalHoldReleased.v1"), released.outboxEventTypes());
    assertEquals(2, released.legalHoldActions().size());
  }

  @Test
  void legalHoldDoesNotInvalidateImmutableEvidenceVerification() {
    ComplianceAuditSnapshot held =
        ComplianceAuditSnapshotService.placeLegalHold(
            ComplianceAuditSnapshotService.createSnapshot(request()),
            new LegalHoldCommand(
                "compliance-manager", "Litigation hold.", Instant.parse("2026-06-01T11:00:00Z"), "corr-123"));

    SnapshotVerificationResult result = ComplianceAuditSnapshotService.verify(held);

    assertEquals("snapshot_verified", result.status());
    assertEquals(List.of(), result.reasonCodes());
  }

  @Test
  void redactsSensitiveItemsForNonPrivilegedRole() {
    ComplianceAuditSnapshot snapshot = ComplianceAuditSnapshotService.createSnapshot(request());

    ComplianceAuditSnapshotView auditorLiteView =
        ComplianceAuditSnapshotService.viewForRole(snapshot, "auditor-lite");
    ComplianceAuditSnapshotView managerView =
        ComplianceAuditSnapshotService.viewForRole(snapshot, "compliance-manager");

    assertEquals(1, auditorLiteView.redactedItemCount());
    assertEquals("<redacted>", auditorLiteView.items().get(0).payload());
    assertTrue(auditorLiteView.warnings().contains("REDACTION_REQUIRED"));
    assertEquals(0, managerView.redactedItemCount());
    assertFalse(managerView.items().get(0).redacted());
  }

  private static CreateComplianceAuditSnapshot request() {
    return request(List.of(evidenceItem()));
  }

  private static CreateComplianceAuditSnapshot request(List<SnapshotEvidenceItem> items) {
    return new CreateComplianceAuditSnapshot(
        "tenant-a",
        "snapshot-001",
        new SnapshotSubjectRef("quote", "quote-123"),
        "fair_lending_snapshot",
        Instant.parse("2026-06-01T10:15:30Z"),
        new ConfigVersionGraph(
            List.of("fair-lending-config-2026-05", "reason-code-catalog-2026-05")),
        List.of("event-sequence:quote-123:1-8"),
        List.of("fair-lending-ledger:snapshot-001"),
        List.of("RATE_DISPARITY_ALERT"),
        items,
        "retention-policy-approved-v1",
        "compliance-service",
        "compliance-analyst",
        "idem-123",
        "corr-123");
  }

  private static SnapshotEvidenceItem evidenceItem() {
    String payload = "{\"snapshotId\":\"snapshot-001\",\"status\":\"completed_alert\"}";
    return new SnapshotEvidenceItem(
        1,
        "fair_lending_snapshot",
        "fair-lending:snapshot-001",
        "fair-lending-snapshot.v1",
        payload,
        ComplianceAuditSnapshotService.hashPayload(payload),
        "restricted",
        "redaction-policy-v1");
  }
}
