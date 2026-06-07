package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class ServiceCredentialContractTest {
  @Test
  void credentialFixtureAllowsOnlyReferencesAndOneTimeDisclosureMarker() {
    IntegrationContractFixtureSupport.assertContains("credentials/service-credential-v1.json", "serviceAccountId", "credentialRef", "oneTimeDisclosure");
    IntegrationContractFixtureSupport.assertContains("events/integration/credential-lifecycle.schema.json", ServiceAccountAccessService.ACCOUNT_CREATED_EVENT_TYPE, ServiceAccountAccessService.CREDENTIAL_CREATED_EVENT_TYPE, ServiceAccountAccessService.CREDENTIAL_REVOKED_EVENT_TYPE);
  }
}
