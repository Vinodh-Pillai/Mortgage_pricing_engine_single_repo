package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceShellTest {
  @Test
  void validPlaceholderRequestReturnsDeterministicPendingEvidenceShellResponse() {
    ComplianceShellResponse response =
        ComplianceShell.evaluateComplianceShell(
            new ComplianceShellRequest("corr-123", "quote-placeholder-456", List.of()));

    assertEquals(
        new ComplianceShellResponse(
            "corr-123",
            "pending_evidence",
            List.of(),
            List.of(
                "Compliance evaluation is deferred until quote, lock, and concession evidence is available."),
            List.of(),
            null),
        response);
  }

  @Test
  void validPlaceholderRequestPreservesOptionalFutureEvidenceSeams() {
    ComplianceShellResponse response =
        ComplianceShell.evaluateComplianceShell(
            new ComplianceShellRequest(
                "corr-789",
                "lock-placeholder-101",
                List.of("quote-evidence-ref"),
                "configured-rule-set-ref"));

    assertEquals("corr-789", response.requestId());
    assertEquals(List.of("quote-evidence-ref"), response.evidenceRefs());
    assertEquals("configured-rule-set-ref", response.ruleSetRef());
    assertEquals(List.of(), response.decisions());
  }

  @Test
  void malformedPlaceholderRequestReturnsProjectStandardValidationErrorShape() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                ComplianceShell.evaluateComplianceShell(
                    new ComplianceShellRequest(null, "quote-placeholder-456")));

    ComplianceShellValidationError error =
        assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertEquals(List.of("requestId must be a non-empty string"), error.getDetails());
  }
}
