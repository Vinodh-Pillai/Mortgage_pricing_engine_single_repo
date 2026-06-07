package com.wcpe.integration;

import org.junit.jupiter.api.Test;

class LosQuoteRequestContractTest {
  @Test
  void losQuoteFixturePinsSchemaCompatibilityAndPricingConfigReference() {
    IntegrationContractFixtureSupport.assertContains("los/los-quote-request-v1.json", LosQuoteRequestService.SUPPORTED_SCHEMA_VERSION, "pricingConfigVersion", "UNSUPPORTED_SCHEMA_VERSION", "POLICY_NOT_SATISFIED");
    IntegrationContractFixtureSupport.assertContains("events/integration/los-quote.schema.json", LosQuoteRequestService.ACCEPTED_EVENT_TYPE, LosQuoteRequestService.PRICED_EVENT_TYPE, LosQuoteRequestService.REJECTED_EVENT_TYPE);
  }
}
