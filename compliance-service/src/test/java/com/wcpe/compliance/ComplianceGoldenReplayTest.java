package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.ComplianceContractTestCatalog.FixtureDefinition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceGoldenReplayTest {
  @Test
  void replaysAprHighCostAuditExportFixtures() {
    List<String> fixtureIds =
        ComplianceContractTestCatalog.baselineFixtures().stream()
            .filter(
                fixture ->
                    List.of("PII-15-S03", "PII-15-S04", "PII-15-S07", "PII-15-S09")
                        .contains(fixture.storyId()))
            .map(FixtureDefinition::fixtureId)
            .toList();

    String first = ComplianceContractTestCatalog.replayHash("synthetic-tenant", fixtureIds);
    List<String> reversed = new ArrayList<>(fixtureIds);
    java.util.Collections.reverse(reversed);
    String second = ComplianceContractTestCatalog.replayHash("synthetic-tenant", reversed);

    assertEquals(first, second);
    assertTrue(first.startsWith("sha256:"));
  }

  @Test
  void detectsHashMismatch() {
    String expected =
        ComplianceContractTestCatalog.replayHash(
            "synthetic-tenant", List.of("apr-advisory-ledger", "high-cost-evaluation-fail-closed"));
    String mutated =
        ComplianceContractTestCatalog.replayHash(
            "synthetic-tenant", List.of("apr-advisory-ledger", "high-cost-evaluation-mutated"));

    assertNotEquals(expected, mutated);
  }
}
