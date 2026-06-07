package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class IntegrationEventSchemaContractTest {
  @Test
  void eventSchemasCoverAllIntegrationEventFamilies() {
    IntegrationContractFixtureSupport.assertEventSchema("events/integration/channel.schema.json", ChannelApiFoundationService.REGISTERED_EVENT_TYPE, ChannelApiFoundationService.UPDATED_EVENT_TYPE);
    IntegrationContractFixtureSupport.assertEventSchema("events/integration/los-quote.schema.json", LosQuoteRequestService.ACCEPTED_EVENT_TYPE, LosQuoteRequestService.PRICED_EVENT_TYPE, LosQuoteRequestService.REJECTED_EVENT_TYPE);
    IntegrationContractFixtureSupport.assertEventSchema("events/integration/webhook-subscription.schema.json", WebhookSubscriptionService.CREATED_EVENT_TYPE, WebhookSubscriptionService.SECRET_ROTATED_EVENT_TYPE);
    IntegrationContractFixtureSupport.assertEventSchema("events/integration/webhook-delivery.schema.json", WebhookDeliveryService.DELIVERED_EVENT_TYPE, WebhookDeliveryService.DEAD_LETTERED_EVENT_TYPE);
    IntegrationContractFixtureSupport.assertEventSchema("events/integration/investor-feed.schema.json", InvestorFeedApiAdapterService.RUN_STARTED_EVENT_TYPE, InvestorFeedApiAdapterService.RECORD_NORMALIZED_EVENT_TYPE);
    IntegrationContractFixtureSupport.assertEventSchema("events/integration/sftp-file.schema.json", SftpFeedAdapterService.FILE_DISCOVERED_EVENT_TYPE, SftpFeedAdapterService.RECORD_NORMALIZED_EVENT_TYPE);
    IntegrationContractFixtureSupport.assertEventSchema("events/integration/credential-lifecycle.schema.json", ServiceAccountAccessService.ACCOUNT_CREATED_EVENT_TYPE, ServiceAccountAccessService.CREDENTIAL_ROTATED_EVENT_TYPE);
    IntegrationContractFixtureSupport.assertEventSchema("events/integration/health-status.schema.json", IntegrationHealthDashboardService.HEALTH_STATUS_CHANGED_EVENT_TYPE);
    IntegrationContractFixtureSupport.assertEventSchema("events/integration/dead-letter-replay.schema.json", DeadLetterReplayService.OPENED_EVENT_TYPE, DeadLetterReplayService.REPLAY_REQUESTED_EVENT_TYPE, DeadLetterReplayService.BLOCKED_EVENT_TYPE);
  }
}
