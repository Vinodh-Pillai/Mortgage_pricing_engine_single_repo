package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class IntegrationHealthContractTest {
  @Test
  void healthFixtureUsesTenantHashAndAvoidsPayloadDisclosure() {
    IntegrationContractFixtureSupport.assertContains("health/integration-health-v1.json", "tenantHash", "HEALTHY", "DEGRADED", "DOWN", "payloadFieldsAllowed");
    IntegrationContractFixtureSupport.assertContains("events/integration/health-status.schema.json", IntegrationHealthDashboardService.HEALTH_STATUS_CHANGED_EVENT_TYPE, "tenantHash");
  }
}
