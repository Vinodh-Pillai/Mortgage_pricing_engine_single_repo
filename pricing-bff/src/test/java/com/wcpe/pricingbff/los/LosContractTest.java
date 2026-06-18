package com.wcpe.pricingbff.los;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LosContractTest {
  private final String spec = readSpec();

  @Test
  void pricingRequestSchema() {
    assertThat(spec).contains("LosPricingRequest:", "/pricing-requests:", "requestId", "tenantId", "creditApplicationFields:", "quoteAddressDTO:");
  }

  @Test
  void pricingResponseSchema() {
    assertThat(spec).contains("LosPricingResponse:", "LosOffer:", "loanPassProduct:", "status:");
  }

  @Test
  void productCatalogSchema() {
    assertThat(spec).contains("/products:", "/products/{productId}:", "/products/search:", "searchLosProducts", "getLosProductDetail", "LosProductCatalogResponse:", "LosProductSummary:", "LosProductDetailResponse:", "authorizationStatus:", "blockedReason:", "appliedFilters", "mappingMetadataStatus:");
  }

  @Test
  void productEligibilitySchema() {
    assertThat(spec).contains("/product-eligibility:", "evaluateLosProductEligibility", "LosProductEligibilityRequest:",
        "LosProductEligibilityResponse:", "requires_more_information", "ruleConfigRefs:", "fieldMessages:",
        "Product and product-family filters are optional", "Optional product-family filter when explicit productIds are not supplied.");
    assertThat(spec).doesNotContain("required: [productIds]");
  }

  @Test
  void lockRequestSchema() {
    assertThat(spec).contains("LosLockRequest:", "LosLockResponse:", "/locks:", "/locks/{id}/extend:");
  }

  @Test
  void webhookPayloadSchema() {
    assertThat(spec).contains("LosWebhookRegistrationRequest:", "LosWebhookCallbackPayload:", "LosWebhookDeliveryReceipt:",
        "LosWebhookDeliveryStatus:", "pricing.completed", "quote.completed", "quote.failed", "callback.delivery_failed",
        "lock.confirmed", "rate.changed", "productResultRefs:", "validationMessages:", "idempotencyKey:",
        "enum: [BLOCKED, QUEUED, DELIVERED, DEAD]", "enum: [COMPLETED, FAILED]");
  }

  @Test
  void permissionScopeMatrixAndDeniedPayloadsAreDocumented() {
    assertThat(spec).contains("x-los-permission-scopes:", "requiredScope: los:pricing-request:write",
        "requiredScope: los:product-catalog:read", "requiredScope: los:product-eligibility:write",
        "requiredScope: los:webhook:write", "X-LOS-Scopes", "X-LOS-Service-Scopes",
        "Permission denied because a required LOS API scope is missing", "'403':");
  }

  private String readSpec() {
    try {
      return Files.readString(Path.of("src/main/resources/static/api-docs/los-api.yaml"));
    } catch (Exception ex) {
      throw new IllegalStateException("los-api.yaml must be published under static/api-docs", ex);
    }
  }
}
