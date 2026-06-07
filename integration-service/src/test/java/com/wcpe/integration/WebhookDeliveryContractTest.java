package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class WebhookDeliveryContractTest {
  @Test
  void webhookDeliveryFixtureCoversFailedAndDeadLetteredAuditStates() {
    IntegrationContractFixtureSupport.assertContains("webhooks/webhook-delivery-audit-v1.json", "DEAD_LETTERED", "attempt", "endpointHostHash", "redactionPolicyRef");
    IntegrationContractFixtureSupport.assertContains("events/integration/webhook-delivery.schema.json", WebhookDeliveryService.FAILED_EVENT_TYPE, WebhookDeliveryService.DEAD_LETTERED_EVENT_TYPE);
  }
}
