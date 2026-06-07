package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class WebhookSubscriptionContractTest {
  @Test
  void webhookSubscriptionFixtureAvoidsRawCredentialDisclosure() {
    IntegrationContractFixtureSupport.assertContains("webhooks/webhook-subscription-v1.json", "endpointHostHash", "deliveryIntegrityHeader", "redactionPolicyRef");
    IntegrationContractFixtureSupport.assertContains("events/integration/webhook-subscription.schema.json", WebhookSubscriptionService.CREATED_EVENT_TYPE, WebhookSubscriptionService.SECRET_ROTATED_EVENT_TYPE, WebhookSubscriptionService.TEST_EVENT_TYPE);
  }
}
