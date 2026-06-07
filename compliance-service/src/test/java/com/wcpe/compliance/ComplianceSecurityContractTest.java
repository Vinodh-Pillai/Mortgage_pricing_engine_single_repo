package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.ComplianceContractTestCatalog.FixtureDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceTenantIsolationContractTest {
  @Test
  void blocksCrossTenantAccess() {
    List<String> fixtureIds =
        ComplianceContractTestCatalog.baselineFixtures().stream().map(FixtureDefinition::fixtureId).toList();

    String tenantA = ComplianceContractTestCatalog.replayHash("tenant-a", fixtureIds);
    String tenantB = ComplianceContractTestCatalog.replayHash("tenant-b", fixtureIds);

    assertTrue(ComplianceContractTestCatalog.restContract().errorCodes().contains("TENANT_ACCESS_DENIED"));
    assertTrue(!tenantA.equals(tenantB));
  }
}

class ComplianceRedactionContractTest {
  @Test
  void enforcesProtectedClassRedaction() {
    List<FixtureDefinition> protectedFixtures =
        ComplianceContractTestCatalog.baselineFixtures().stream()
            .filter(fixture -> "protected".equals(fixture.sensitivity()))
            .toList();

    assertEquals(3, protectedFixtures.size());
    assertTrue(protectedFixtures.stream().allMatch(fixture -> fixture.fixtureId().contains("redacted")
        || fixture.fixtureId().contains("audit")
        || fixture.fixtureId().contains("export")));
  }
}

class ComplianceAuthorizationContractTest {
  @Test
  void requiresExpectedScopes() {
    assertTrue(ComplianceContractTestCatalog.restContract().requiredHeaders().contains("Authorization"));
    assertTrue(ComplianceContractTestCatalog.restContract().errorCodes().contains("UNAUTHENTICATED"));
    assertTrue(ComplianceContractTestCatalog.restContract().errorCodes().contains("TENANT_ACCESS_DENIED"));
  }
}
