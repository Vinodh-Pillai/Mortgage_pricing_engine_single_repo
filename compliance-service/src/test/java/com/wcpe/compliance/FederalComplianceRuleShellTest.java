package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.FederalComplianceRuleShell.ApplicabilityCriteria;
import com.wcpe.compliance.FederalComplianceRuleShell.ComplianceAdvisoryResult;
import com.wcpe.compliance.FederalComplianceRuleShell.ComplianceEvaluationRequest;
import com.wcpe.compliance.FederalComplianceRuleShell.FederalRulePackVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FederalComplianceRuleShellTest {
  private static final ApplicabilityCriteria REQUEST_CRITERIA =
      new ApplicabilityCriteria("CONVENTIONAL", "RETAIL", "CA", "FIRST");

  @Test
  void resolvesTenantScopedPublishedRulePackWithoutHardCodedThresholds() {
    FederalRulePackVersion version = publishedVersion("tenant-a", 3, List.of("apr-rule-ref-v3"));

    ComplianceAdvisoryResult result =
        FederalComplianceRuleShell.evaluate(
            request("tenant-a", List.of(version), Set.of("threshold-config-apr-2026")));

    assertEquals("RESOLVED", result.status());
    assertEquals("tenant-a", result.tenantId());
    assertEquals("FED-APR", result.resolution().rulePackCode());
    assertEquals(3, result.resolution().version());
    assertEquals(List.of("apr-rule-ref-v3"), result.resolution().executableRuleRefs());
    assertEquals(List.of(), result.failClosedReasons());
    assertEquals("federal_compliance_rule_shell.completed.v1", result.outboxEventType());
    assertTrue(result.auditRef().startsWith("audit-sha256:"));
  }

  @Test
  void FederalRulePackResolverTest_failsClosedWhenThresholdConfigMissing() {
    FederalRulePackVersion version = publishedVersion("tenant-a", 3, List.of("apr-rule-ref-v3"));

    ComplianceAdvisoryResult result =
        FederalComplianceRuleShell.evaluate(request("tenant-a", List.of(version), Set.of()));

    assertEquals("POLICY_NOT_SATISFIED", result.status());
    assertEquals(List.of("MISSING_THRESHOLD_CONFIG:threshold-config-apr-2026"), result.failClosedReasons());
    assertEquals(List.of(), result.resolution().executableRuleRefs());
  }

  @Test
  void returnsFailClosedWithoutCrossTenantExistenceLeakage() {
    FederalRulePackVersion otherTenantVersion = publishedVersion("tenant-b", 3, List.of("apr-rule-ref-v3"));

    ComplianceAdvisoryResult result =
        FederalComplianceRuleShell.evaluate(
            request("tenant-a", List.of(otherTenantVersion), Set.of("threshold-config-apr-2026")));

    assertEquals("POLICY_NOT_SATISFIED", result.status());
    assertEquals(List.of("RULE_PACK_NOT_FOUND"), result.failClosedReasons());
    assertNull(result.resolution());
  }

  @Test
  void FederalRulePackVersionTest_rejectsOverlappingPublishedPeriods() {
    FederalRulePackVersion first = publishedVersion("tenant-a", 1, List.of("apr-rule-ref-v1"));
    FederalRulePackVersion second =
        new FederalRulePackVersion(
            "tenant-a",
            "FED-APR",
            2,
            "PUBLISHED",
            LocalDate.parse("2026-06-01"),
            LocalDate.parse("2026-12-31"),
            REQUEST_CRITERIA,
            List.of("apr-rule-ref-v2"),
            List.of("threshold-config-apr-2026"),
            List.of("12 CFR 1026"),
            "rule-pack-hash-v2");

    List<String> errors = FederalComplianceRuleShell.validatePublishedPeriods(List.of(first, second));

    assertEquals(List.of("OVERLAPPING_EFFECTIVE_PERIOD:FED-APR:1:2"), errors);
  }

  @Test
  void auditRefIsDeterministicForReplayEvidence() {
    FederalRulePackVersion version = publishedVersion("tenant-a", 3, List.of("apr-rule-ref-v3"));
    ComplianceEvaluationRequest request =
        request("tenant-a", List.of(version), Set.of("threshold-config-apr-2026"));

    ComplianceAdvisoryResult first = FederalComplianceRuleShell.evaluate(request);
    ComplianceAdvisoryResult second = FederalComplianceRuleShell.evaluate(request);

    assertEquals(first.auditRef(), second.auditRef());
    assertFalse(first.auditRef().contains("borrower"));
  }

  @Test
  void malformedRequestReturnsProjectStandardValidationErrorShape() {
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> FederalComplianceRuleShell.evaluate(null));

    ComplianceShellValidationError error =
        assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertEquals(List.of("request"), error.getDetails());
  }

  private static ComplianceEvaluationRequest request(
      String tenantId, List<FederalRulePackVersion> versions, Set<String> availableThresholdRefs) {
    return new ComplianceEvaluationRequest(
        tenantId,
        "request-123",
        "actor-456",
        "FED-APR",
        LocalDate.parse("2026-06-15"),
        REQUEST_CRITERIA,
        versions,
        availableThresholdRefs,
        "correlation-789");
  }

  private static FederalRulePackVersion publishedVersion(
      String tenantId, int version, List<String> ruleExpressionRefs) {
    return new FederalRulePackVersion(
        tenantId,
        "FED-APR",
        version,
        "PUBLISHED",
        LocalDate.parse("2026-01-01"),
        LocalDate.parse("2026-12-31"),
        REQUEST_CRITERIA,
        ruleExpressionRefs,
        List.of("threshold-config-apr-2026"),
        List.of("12 CFR 1026"),
        "rule-pack-hash-v" + version);
  }
}
