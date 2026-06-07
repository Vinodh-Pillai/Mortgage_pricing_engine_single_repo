package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.ComplianceContractTestCatalog.ContractReport;
import com.wcpe.compliance.ComplianceContractTestCatalog.ContractTestResult;
import com.wcpe.compliance.ComplianceContractTestCatalog.FixtureDefinition;
import com.wcpe.compliance.ComplianceContractTestCatalog.RestContract;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceContractFixtureIndexTest {
  @Test
  void fixtureIndexCoversS01ThroughS09WithSyntheticOwnedHashes() throws IOException {
    List<FixtureDefinition> fixtures = ComplianceContractTestCatalog.baselineFixtures();

    assertEquals(List.of(), ComplianceContractTestCatalog.validateFixtureIndex(fixtures));
    for (int story = 1; story <= 9; story++) {
      String storyId = String.format("PII-15-S%02d", story);
      assertTrue(fixtures.stream().anyMatch(fixture -> storyId.equals(fixture.storyId())));
    }
    assertTrue(fixtures.stream().allMatch(fixture -> fixture.expectedHash().startsWith("sha256:")));
    assertTrue(fixtures.stream().allMatch(fixture -> "synthetic".equals(fixture.owner())));

    Path index = Path.of("fixtures", "compliance", "fixture-index.json");
    String persisted = Files.readString(index);
    assertTrue(persisted.contains("compliance-fixture-index.v1"));
    assertTrue(persisted.contains("federal-rule-pack-lifecycle"));
    assertTrue(persisted.contains("compliance-export-redacted-manifest"));
  }

  @Test
  void restContractDefinesTenantScopeIdempotencyAndExpectedErrors() {
    RestContract contract = ComplianceContractTestCatalog.restContract();

    assertEquals("POST", contract.commandMethod());
    assertEquals("/api/v1/tenants/{tenantId}/compliance-contract-tests", contract.commandPath());
    assertEquals("GET", contract.readMethod());
    assertTrue(contract.requiredHeaders().contains("Authorization"));
    assertTrue(contract.requiredHeaders().contains("Idempotency-Key"));
    assertTrue(contract.requestFields().containsAll(List.of("tenantId", "requestId", "actorId", "payload")));
    assertTrue(contract.responseFields().containsAll(List.of("auditRef", "replayRef", "correlationId")));
    assertTrue(contract.errorCodes().containsAll(List.of("TENANT_ACCESS_DENIED", "POLICY_NOT_SATISFIED")));
  }

  @Test
  void writesAccessibleJsonContractReportArtifact() throws IOException {
    List<String> fixtureIds =
        ComplianceContractTestCatalog.baselineFixtures().stream().map(FixtureDefinition::fixtureId).toList();
    String resultHash = ComplianceContractTestCatalog.replayHash("synthetic-tenant", fixtureIds);
    ContractReport report =
        ComplianceContractTestCatalog.contractReport(
            List.of(
                new ContractTestResult(
                    "PII-15-S10-local-contract-suite",
                    fixtureIds,
                    List.of("rest:v1", "event:v1", "audit:v1"),
                    resultHash,
                    List.of())),
            "local-gradle-junit");

    Path reportPath = Path.of("build", "reports", "compliance-contracts", "contract-results.json");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, ComplianceContractTestCatalog.reportJson(report));

    String persisted = Files.readString(reportPath);
    assertTrue(persisted.contains("compliance-contract-results.v1"));
    assertTrue(persisted.contains("PII-15-S10-local-contract-suite"));
    assertTrue(persisted.contains("\"failures\": []"));
  }
}
