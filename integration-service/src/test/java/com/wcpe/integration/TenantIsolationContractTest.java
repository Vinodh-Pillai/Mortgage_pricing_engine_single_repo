package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class TenantIsolationContractTest {
  @Test
  void fixturesAndOpenApiRequireTenantScopedAccessOnly() {
    IntegrationContractFixtureSupport.assertContains("openapi/integrations-v1.yaml", "/tenants/{tenantId}/channels", "/tenants/{tenantId}/integration-health", "/tenants/{tenantId}/dead-letters");
    IntegrationContractFixtureSupport.assertContains("channel/channel-api-v1.json", "tenantId");
    IntegrationContractFixtureSupport.assertContains("los/los-quote-request-v1.json", "tenantId");
    IntegrationContractFixtureSupport.assertContains("webhooks/webhook-delivery-audit-v1.json", "tenantId");
    IntegrationContractFixtureSupport.assertContains("investor-api/investor-feed-api-v1.json", "tenantId");
    IntegrationContractFixtureSupport.assertContains("sftp/sftp-feed-v1.json", "tenantId");
    IntegrationContractFixtureSupport.assertContains("credentials/service-credential-v1.json", "tenantId");
    IntegrationContractFixtureSupport.assertContains("dlq/dead-letter-replay-v1.json", "tenantId");
  }
}
