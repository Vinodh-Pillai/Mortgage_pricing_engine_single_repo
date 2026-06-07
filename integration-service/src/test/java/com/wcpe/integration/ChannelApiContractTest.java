package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class ChannelApiContractTest {
  @Test
  void channelFixtureRequiresTenantScopeIdempotencyAndPolicyRefs() {
    IntegrationContractFixtureSupport.assertContains("channel/channel-api-v1.json", "tenantId", "allowedProducts", "rateLimitPolicyRef", "IDEMPOTENCY_CONFLICT", "POLICY_NOT_SATISFIED");
    IntegrationContractFixtureSupport.assertContains("events/integration/channel.schema.json", ChannelApiFoundationService.REGISTERED_EVENT_TYPE, ChannelApiFoundationService.UPDATED_EVENT_TYPE);
  }
}
