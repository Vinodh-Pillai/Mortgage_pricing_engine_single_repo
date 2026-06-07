package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class InvestorApiAdapterContractTest {
  @Test
  void investorApiFixtureUsesCredentialReferencesAndDependencyErrors() {
    IntegrationContractFixtureSupport.assertContains("investor-api/investor-feed-api-v1.json", "investorId", "credentialRef", "normalizedRecordCount", "DEPENDENCY_UNAVAILABLE");
    IntegrationContractFixtureSupport.assertContains("events/integration/investor-feed.schema.json", InvestorFeedApiAdapterService.RUN_NORMALIZED_EVENT_TYPE, InvestorFeedApiAdapterService.RUN_FAILED_EVENT_TYPE);
  }
}
