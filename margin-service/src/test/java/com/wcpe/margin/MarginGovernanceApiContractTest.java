package com.wcpe.margin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MarginGovernanceApiContractTest {
  @Test
  void exposesStoryNamedGovernanceApiContractAndPermissions() {
    assertEquals("POST /api/v1/tenants/{tenantId}/margin-governance/change-requests",
        MarginGovernanceService.CHANGE_REQUESTS_API);
    assertEquals("POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/submit",
        MarginGovernanceService.SUBMIT_API);
    assertEquals("POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/approve",
        MarginGovernanceService.APPROVE_API);
    assertEquals("POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/reject",
        MarginGovernanceService.REJECT_API);
    assertEquals("POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/publish",
        MarginGovernanceService.PUBLISH_API);
    assertEquals("POST /api/v1/tenants/{tenantId}/margin-governance/{changeId}/rollback",
        MarginGovernanceService.ROLLBACK_API);
    assertEquals("pricing.governance.submit", MarginGovernanceService.SUBMIT_PERMISSION);
    assertEquals("pricing.governance.rollback", MarginGovernanceService.ROLLBACK_PERMISSION);
  }
}
