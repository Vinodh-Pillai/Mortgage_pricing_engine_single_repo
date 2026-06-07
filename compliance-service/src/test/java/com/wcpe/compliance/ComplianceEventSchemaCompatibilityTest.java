package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.ComplianceContractTestCatalog.EventContract;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceEventSchemaCompatibilityTest {
  @Test
  void validatesAllV1Events() {
    List<EventContract> contracts = ComplianceContractTestCatalog.eventContracts();

    assertEquals(List.of(), ComplianceContractTestCatalog.validateEventContracts(contracts));
    assertTrue(contracts.stream().allMatch(contract -> contract.eventType().endsWith(".v1")));
    assertTrue(contracts.stream().anyMatch(contract -> ComplianceExportService.COMPLETED_EVENT_TYPE.equals(contract.eventType())));
    assertTrue(contracts.stream().anyMatch(contract -> ComplianceAuditSnapshotService.CREATED_EVENT_TYPE.equals(contract.eventType())));
    assertTrue(contracts.stream().anyMatch(contract -> RegulatoryConfigApprovalService.PUBLISHED_EVENT_TYPE.equals(contract.eventType())));
  }

  @Test
  void requiresTenantCorrelationCausationSchemaVersion() {
    for (EventContract contract : ComplianceContractTestCatalog.eventContracts()) {
      assertTrue(contract.headers().containsAll(List.of("tenantId", "correlationId", "causationId", "eventVersion")));
      assertEquals("tenantId:id", contract.keyStrategy());
      assertEquals("tenant-scoped-key", contract.partitionStrategy());
      assertTrue(contract.retryDlqSemantics().contains("idempotency-key"));
    }
  }
}
