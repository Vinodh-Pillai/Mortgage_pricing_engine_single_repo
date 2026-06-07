package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class IntegrationsOpenApiContractTest {
  @Test
  void openApiCatalogCoversIntegrationRoutesAndErrorEnvelope() {
    IntegrationContractFixtureSupport.assertContains(
        "openapi/integrations-v1.yaml",
        ChannelApiFoundationService.BASE_PATH.replace("/api/v1", ""),
        LosQuoteRequestService.BASE_PATH.replace("/api/v1", ""),
        WebhookSubscriptionService.BASE_PATH.replace("/api/v1", ""),
        WebhookDeliveryService.BASE_PATH.replace("/api/v1", ""),
        InvestorFeedApiAdapterService.BASE_PATH.replace("/api/v1", ""),
        SftpFeedAdapterService.BASE_PATH.replace("/api/v1", ""),
        ServiceAccountAccessService.BASE_PATH.replace("/api/v1", ""),
        IntegrationHealthDashboardService.BASE_PATH.replace("/api/v1", ""),
        DeadLetterReplayService.BASE_PATH.replace("/api/v1", ""),
        "ErrorEnvelope",
        "IDEMPOTENCY_CONFLICT",
        "POLICY_NOT_SATISFIED",
        "DEPENDENCY_UNAVAILABLE");
  }
}
