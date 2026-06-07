package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wcpe.compliance.ComplianceReasonCodeCatalog.ApprovalCommand;
import com.wcpe.compliance.ComplianceReasonCodeCatalog.ComplianceReasonCodeVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReasonCodeBorrowerTextPolicyTest {
  @Test
  void requiresLegalApproval() {
    ComplianceReasonCodeVersion draft =
        ComplianceReasonCodeCatalog.createDraft(ComplianceReasonCodeTest.draftCommand(), List.of());

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                ComplianceReasonCodeCatalog.approve(
                    draft,
                    new ApprovalCommand(
                        "legal-approver",
                        false,
                        "Approval deliberately withheld.",
                        ComplianceReasonCodeTest.approvedAt(),
                        "corr-456")));

    ComplianceShellValidationError error = assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals(List.of("BORROWER_TEXT_NOT_APPROVED"), error.getDetails());
  }

  @Test
  void separatesAuthorAndApprover() {
    ComplianceReasonCodeVersion draft =
        ComplianceReasonCodeCatalog.createDraft(ComplianceReasonCodeTest.draftCommand(), List.of());

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                ComplianceReasonCodeCatalog.approve(
                    draft,
                    new ApprovalCommand(
                        "catalog-author",
                        true,
                        "Self-approval should be rejected.",
                        ComplianceReasonCodeTest.approvedAt(),
                        "corr-456")));

    ComplianceShellValidationError error = assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals(List.of("AUTHOR_CANNOT_APPROVE_OWN_WORDING"), error.getDetails());
  }
}
