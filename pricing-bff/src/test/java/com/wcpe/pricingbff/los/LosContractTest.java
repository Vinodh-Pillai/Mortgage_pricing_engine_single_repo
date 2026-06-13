package com.wcpe.pricingbff.los;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LosContractTest {
  private final String spec = readSpec();

  @Test
  void pricingRequestSchema() {
    assertThat(spec).contains("LosPricingRequest:", "/pricing-requests:", "requestId", "tenantId", "LosLoan:");
  }

  @Test
  void pricingResponseSchema() {
    assertThat(spec).contains("LosPricingResponse:", "LosOffer:", "WaterfallStep:", "status:");
  }

  @Test
  void lockRequestSchema() {
    assertThat(spec).contains("LosLockRequest:", "LosLockResponse:", "/locks:", "/locks/{id}/extend:");
  }

  @Test
  void webhookPayloadSchema() {
    assertThat(spec).contains("LosWebhookRegistrationRequest:", "pricing.completed", "lock.confirmed", "rate.changed");
  }

  private String readSpec() {
    try {
      return Files.readString(Path.of("src/main/resources/static/api-docs/los-api.yaml"));
    } catch (Exception ex) {
      throw new IllegalStateException("los-api.yaml must be published under static/api-docs", ex);
    }
  }
}
