package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wcpe.compliance.ComplianceReasonCodeCatalog.ApprovalCommand;
import com.wcpe.compliance.ComplianceReasonCodeCatalog.ComplianceReasonCodeVersion;
import com.wcpe.compliance.ComplianceReasonCodeCatalog.DeprecationCommand;
import com.wcpe.compliance.ComplianceReasonCodeCatalog.ReasonCodeLifecycleResult;
import com.wcpe.compliance.ComplianceReasonCodeCatalog.ReasonCodeResolution;
import com.wcpe.compliance.ComplianceReasonCodeCatalog.ResolveReasonCodeRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReasonCodeResolverTest {
  @Test
  void resolvesDeprecatedForReplay() {
    ComplianceReasonCodeVersion published = publishedVersion();
    ReasonCodeLifecycleResult deprecated =
        ComplianceReasonCodeCatalog.deprecate(
            published, new DeprecationCommand("RATE_DISPARITY_SUCCESSOR", "legal-approver", "corr-789"));

    ReasonCodeResolution replay =
        ComplianceReasonCodeCatalog.resolve(
            new ResolveReasonCodeRequest(
                "tenant-a",
                "RATE_DISPARITY_ALERT",
                LocalDate.parse("2026-04-01"),
                "internal",
                "en-US",
                true,
                "corr-replay"),
            List.of(deprecated.version()));

    assertEquals("deprecated", replay.status());
    assertEquals("RATE_DISPARITY_SUCCESSOR", replay.successorCode());
    assertEquals(deprecated.version().hash(), replay.replayHash());
  }

  @Test
  void rejectsBorrowerSafeResolutionWhenApprovalEvidenceMissing() {
    ComplianceReasonCodeVersion draft =
        ComplianceReasonCodeCatalog.createDraft(ComplianceReasonCodeTest.draftCommand(), List.of());

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                ComplianceReasonCodeCatalog.resolve(
                    new ResolveReasonCodeRequest(
                        "tenant-a",
                        "RATE_DISPARITY_ALERT",
                        LocalDate.parse("2026-04-01"),
                        "borrower_safe",
                        "en-US",
                        false,
                        "corr-resolve"),
                    List.of(draft.withStatus("published"))));

    ComplianceShellValidationError error = assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals(List.of("BORROWER_TEXT_NOT_APPROVED"), error.getDetails());
  }

  @Test
  void resolvesBorrowerSafeTextAfterLegalApproval() {
    ComplianceReasonCodeVersion published = publishedVersion();

    ReasonCodeResolution resolution =
        ComplianceReasonCodeCatalog.resolve(
            new ResolveReasonCodeRequest(
                "tenant-a",
                "rate_disparity_alert",
                LocalDate.parse("2026-04-01"),
                "borrower_safe",
                "en-US",
                false,
                "corr-resolve"),
            List.of(published));

    assertEquals("Configured borrower-safe label", resolution.displayText());
    assertEquals(List.of("reg-citation-config-ref"), resolution.citations());
    assertEquals(List.of("rule:fair-lending-rate-disparity"), resolution.ruleMappings());
  }

  private static ComplianceReasonCodeVersion publishedVersion() {
    ComplianceReasonCodeVersion draft =
        ComplianceReasonCodeCatalog.createDraft(ComplianceReasonCodeTest.draftCommand(), List.of());
    ComplianceReasonCodeVersion approved =
        ComplianceReasonCodeCatalog.approve(
            draft,
            new ApprovalCommand(
                "legal-approver",
                true,
                "Legal wording evidence attached.",
                ComplianceReasonCodeTest.approvedAt(),
                "corr-456"));
    return ComplianceReasonCodeCatalog.publish(approved).version();
  }
}
