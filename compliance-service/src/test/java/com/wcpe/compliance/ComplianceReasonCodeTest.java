package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.ComplianceReasonCodeCatalog.ApprovalCommand;
import com.wcpe.compliance.ComplianceReasonCodeCatalog.ComplianceReasonCodeVersion;
import com.wcpe.compliance.ComplianceReasonCodeCatalog.DraftReasonCodeCommand;
import com.wcpe.compliance.ComplianceReasonCodeCatalog.ReasonCodeLifecycleResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceReasonCodeTest {
  @Test
  void rejectsDuplicateCode() {
    ComplianceReasonCodeVersion existing = ComplianceReasonCodeCatalog.createDraft(draftCommand(), List.of());

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> ComplianceReasonCodeCatalog.createDraft(draftCommand(), List.of(existing)));

    ComplianceShellValidationError error = assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertEquals(List.of("DUPLICATE_CODE"), error.getDetails());
  }

  @Test
  void createsVersionedDraftWithoutHardCodedPolicyText() {
    ComplianceReasonCodeVersion draft = ComplianceReasonCodeCatalog.createDraft(draftCommand(), List.of());

    assertEquals("RATE_DISPARITY_ALERT", draft.code());
    assertEquals("FAIR_LENDING", draft.category());
    assertEquals("HIGH", draft.severity());
    assertEquals("draft", draft.status());
    assertEquals("Configured internal label", draft.internalLabel());
    assertTrue(draft.hash().startsWith("sha256:"));
  }

  @Test
  void publishesApprovedVersionWithAuditAndOutboxEvidence() {
    ComplianceReasonCodeVersion draft = ComplianceReasonCodeCatalog.createDraft(draftCommand(), List.of());
    ComplianceReasonCodeVersion approved =
        ComplianceReasonCodeCatalog.approve(
            draft,
            new ApprovalCommand(
                "legal-approver", true, "Legal wording evidence attached", approvedAt(), "corr-456"));

    ReasonCodeLifecycleResult result = ComplianceReasonCodeCatalog.publish(approved);

    assertEquals("published", result.version().status());
    assertEquals(List.of("ComplianceReasonCodePublished.v1"), result.outboxEventTypes());
    assertEquals("COMPLIANCE_REASON_CODE_PUBLISHED", result.auditAction());
    assertTrue(result.auditRef().startsWith("reason-code-audit:sha256:"));
  }

  static DraftReasonCodeCommand draftCommand() {
    return new DraftReasonCodeCommand(
        "tenant-a",
        "rate_disparity_alert",
        "fair_lending",
        "high",
        LocalDate.parse("2026-01-01"),
        null,
        "Configured internal label",
        true,
        false,
        "Configured long description from tenant compliance catalog.",
        List.of("reg-citation-config-ref"),
        List.of("rule:fair-lending-rate-disparity"),
        List.of("en-US|borrower_safe|Configured borrower-safe label"),
        null,
        "catalog-author",
        "idem-123",
        "corr-123");
  }

  static OffsetDateTime approvedAt() {
    return OffsetDateTime.parse("2026-02-01T12:00:00Z");
  }
}
