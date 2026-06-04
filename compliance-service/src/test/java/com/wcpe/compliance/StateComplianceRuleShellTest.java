package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.StateComplianceRuleShell.StateApplicabilityCriteria;
import com.wcpe.compliance.StateComplianceRuleShell.StateComplianceAdvisoryResult;
import com.wcpe.compliance.StateComplianceRuleShell.StateComplianceEvaluationRequest;
import com.wcpe.compliance.StateComplianceRuleShell.StateRulePackVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StateComplianceRuleShellTest {
  private static final StateApplicabilityCriteria REQUEST_CRITERIA =
      new StateApplicabilityCriteria("CONVENTIONAL", "RETAIL", "FIRST", "OWNER_OCCUPIED");

  @Test
  void resolvesTenantScopedPublishedStateRulePackWithoutHardCodedThresholds() {
    StateRulePackVersion version = publishedVersion("tenant-a", "CA", 2, List.of("ca-rule-ref-v2"));

    StateComplianceAdvisoryResult result =
        StateComplianceRuleShell.resolve(
            request(
                "tenant-a",
                "CA",
                List.of(version),
                Set.of("threshold-config-ca-2026"),
                Set.of("federal-precedence-fed-apr-v3")));

    assertEquals("RESOLVED", result.status());
    assertEquals("tenant-a", result.tenantId());
    assertEquals("CA", result.stateCode());
    assertEquals("STATE-HIGH-COST", result.resolution().rulePackCode());
    assertEquals(2, result.resolution().version());
    assertEquals(List.of("ca-rule-ref-v2"), result.resolution().executableRuleRefs());
    assertEquals(List.of("threshold-config-ca-2026"), result.resolution().thresholdConfigRefs());
    assertEquals(List.of("federal-precedence-fed-apr-v3"), result.resolution().federalRulePackRefs());
    assertEquals(List.of(), result.failClosedReasons());
    assertEquals("state_compliance_shell.completed.v1", result.outboxEventType());
    assertTrue(result.auditRef().startsWith("audit-sha256:"));
  }

  @Test
  void StateRulePackResolverTest_requiresPropertyState() {
    StateRulePackVersion version = publishedVersion("tenant-a", "CA", 2, List.of("ca-rule-ref-v2"));

    StateComplianceAdvisoryResult result =
        StateComplianceRuleShell.resolve(
            request(
                "tenant-a",
                " ",
                List.of(version),
                Set.of("threshold-config-ca-2026"),
                Set.of("federal-precedence-fed-apr-v3")));

    assertEquals("POLICY_NOT_SATISFIED", result.status());
    assertEquals(List.of("MISSING_PROPERTY_STATE"), result.failClosedReasons());
    assertNull(result.resolution());
  }

  @Test
  void failsClosedWithoutCrossTenantOrCrossStateExistenceLeakage() {
    StateRulePackVersion otherTenantVersion =
        publishedVersion("tenant-b", "CA", 2, List.of("ca-rule-ref-v2"));
    StateRulePackVersion otherStateVersion = publishedVersion("tenant-a", "NY", 2, List.of("ny-rule-ref-v2"));

    StateComplianceAdvisoryResult result =
        StateComplianceRuleShell.resolve(
            request(
                "tenant-a",
                "CA",
                List.of(otherTenantVersion, otherStateVersion),
                Set.of("threshold-config-ca-2026"),
                Set.of("federal-precedence-fed-apr-v3")));

    assertEquals("POLICY_NOT_SATISFIED", result.status());
    assertEquals(List.of("STATE_RULE_PACK_NOT_FOUND"), result.failClosedReasons());
    assertNull(result.resolution());
  }

  @Test
  void StateRulePackResolverTest_failsClosedWhenThresholdConfigMissing() {
    StateRulePackVersion version = publishedVersion("tenant-a", "CA", 2, List.of("ca-rule-ref-v2"));

    StateComplianceAdvisoryResult result =
        StateComplianceRuleShell.resolve(
            request(
                "tenant-a",
                "CA",
                List.of(version),
                Set.of(),
                Set.of("federal-precedence-fed-apr-v3")));

    assertEquals("POLICY_NOT_SATISFIED", result.status());
    assertEquals(List.of("MISSING_THRESHOLD_CONFIG:threshold-config-ca-2026"), result.failClosedReasons());
    assertEquals(List.of(), result.resolution().executableRuleRefs());
  }

  @Test
  void StateFederalPrecedenceTest_requiresExplicitPrecedence() {
    StateRulePackVersion version = publishedVersion("tenant-a", "CA", 2, List.of("ca-rule-ref-v2"));

    StateComplianceAdvisoryResult result =
        StateComplianceRuleShell.resolve(
            request(
                "tenant-a",
                "CA",
                List.of(version),
                Set.of("threshold-config-ca-2026"),
                Set.of()));

    assertEquals("POLICY_NOT_SATISFIED", result.status());
    assertEquals(
        List.of("MISSING_FEDERAL_PRECEDENCE_REF:federal-precedence-fed-apr-v3"),
        result.failClosedReasons());
    assertEquals(List.of(), result.resolution().executableRuleRefs());
  }

  @Test
  void StateRulePackVersionTest_rejectsStateOverlap() {
    StateRulePackVersion first = publishedVersion("tenant-a", "CA", 1, List.of("ca-rule-ref-v1"));
    StateRulePackVersion second =
        new StateRulePackVersion(
            "tenant-a",
            "CA",
            "STATE-HIGH-COST",
            2,
            "PUBLISHED",
            LocalDate.parse("2026-06-01"),
            LocalDate.parse("2026-12-31"),
            REQUEST_CRITERIA,
            List.of("ca-rule-ref-v2"),
            List.of("threshold-config-ca-2026"),
            List.of("federal-precedence-fed-apr-v3"),
            List.of("citation-ref-ca-2026"),
            List.of("source-doc-ca-2026"),
            "state-rule-pack-hash-v2");

    List<String> errors = StateComplianceRuleShell.validatePublishedPeriods(List.of(first, second));

    assertEquals(List.of("OVERLAPPING_EFFECTIVE_PERIOD:CA:STATE-HIGH-COST:1:2"), errors);
  }

  @Test
  void auditRefIsDeterministicAndDoesNotExposeBorrowerData() {
    StateRulePackVersion version = publishedVersion("tenant-a", "CA", 2, List.of("ca-rule-ref-v2"));
    StateComplianceEvaluationRequest request =
        request(
            "tenant-a",
            "CA",
            List.of(version),
            Set.of("threshold-config-ca-2026"),
            Set.of("federal-precedence-fed-apr-v3"));

    StateComplianceAdvisoryResult first = StateComplianceRuleShell.resolve(request);
    StateComplianceAdvisoryResult second = StateComplianceRuleShell.resolve(request);

    assertEquals(first.auditRef(), second.auditRef());
    assertFalse(first.auditRef().contains("borrower"));
  }

  @Test
  void malformedRequestReturnsProjectStandardValidationErrorShape() {
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> StateComplianceRuleShell.resolve(null));

    ComplianceShellValidationError error =
        assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertEquals(List.of("request"), error.getDetails());
  }

  private static StateComplianceEvaluationRequest request(
      String tenantId,
      String propertyState,
      List<StateRulePackVersion> versions,
      Set<String> availableThresholdRefs,
      Set<String> availableFederalRefs) {
    return new StateComplianceEvaluationRequest(
        tenantId,
        "request-123",
        "actor-456",
        propertyState,
        "STATE-HIGH-COST",
        LocalDate.parse("2026-06-15"),
        REQUEST_CRITERIA,
        versions,
        availableThresholdRefs,
        availableFederalRefs,
        "correlation-789");
  }

  private static StateRulePackVersion publishedVersion(
      String tenantId, String stateCode, int version, List<String> ruleExpressionRefs) {
    return new StateRulePackVersion(
        tenantId,
        stateCode,
        "STATE-HIGH-COST",
        version,
        "PUBLISHED",
        LocalDate.parse("2026-01-01"),
        LocalDate.parse("2026-12-31"),
        REQUEST_CRITERIA,
        ruleExpressionRefs,
        List.of("threshold-config-ca-2026"),
        List.of("federal-precedence-fed-apr-v3"),
        List.of("citation-ref-ca-2026"),
        List.of("source-doc-ca-2026"),
        "state-rule-pack-hash-v" + version);
  }
}
