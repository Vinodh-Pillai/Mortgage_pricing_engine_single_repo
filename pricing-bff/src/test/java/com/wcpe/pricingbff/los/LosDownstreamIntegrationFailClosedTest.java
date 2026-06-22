package com.wcpe.pricingbff.los;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LosDownstreamIntegrationFailClosedTest {
  @Test
  void quoteClientFailsClosedWhenBaseUrlMissing() {
    LosQuoteServiceClient client = new LosQuoteServiceClient(RestClient.builder(), "");
    LosApiModels.QuoteServiceRequest request = quoteRequest("quote-missing-config");

    assertThatThrownBy(() -> client.submitQuoteJob(request))
        .isInstanceOf(LosValidationException.class)
        .hasMessageContaining("quote-service base URL is not configured")
        .extracting(ex -> ((LosValidationException) ex).code())
        .isEqualTo("QUOTE_SERVICE_INTEGRATION_REQUIRED");
  }

  @Test
  void quoteClientPostsToConfiguredQuoteService() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://quote-service.test/api/v1/los/quote-requests"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("""
            {"jobId":"job-live-1","status":"QUEUED","statusUrl":"/api/v1/los/quote-requests/job-live-1","correlationId":"corr-1"}
            """, MediaType.APPLICATION_JSON));

    LosQuoteServiceClient client = new LosQuoteServiceClient(builder, "https://quote-service.test");
    LosApiModels.QuoteServiceResponse response = client.submitQuoteJob(quoteRequest("quote-configured"));

    assertThat(response.jobId()).isEqualTo("job-live-1");
    server.verify();
  }

  @Test
  void lockClientFailsClosedWhenBaseUrlMissing() {
    LosLockServiceClient client = new LosLockServiceClient(RestClient.builder(), "");
    LosApiModels.LosLockRequest request = new LosApiModels.LosLockRequest("pricing-1", "offer-1", 30, "loan-officer");

    assertThatThrownBy(() -> client.requestLock("tenant-los", request, null, "idem-lock", "corr-lock"))
        .isInstanceOf(LosValidationException.class)
        .hasMessageContaining("lock-service base URL is not configured")
        .extracting(ex -> ((LosValidationException) ex).code())
        .isEqualTo("LOCK_SERVICE_INTEGRATION_REQUIRED");
  }

  @Test
  void lockClientPostsRequestAndExtensionToConfiguredLockService() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    UUID tenantScopedId = UUID.nameUUIDFromBytes("los-tenant:tenant-los".getBytes(StandardCharsets.UTF_8));
    server.expect(requestTo("https://lock-service.test/api/v1/tenants/" + tenantScopedId + "/locks/requests"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("""
            {"lockId":"lock-live-1","requestId":"los-lock:pricing-1:offer-1","status":"REQUESTED","correlationId":"corr-lock"}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://lock-service.test/api/v1/tenants/" + tenantScopedId + "/locks/lock-live-1/extensions"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("""
            {"lockId":"lock-live-1","status":"EXTENSION_REQUESTED","expiresAt":"2026-07-25T08:00:00Z","correlationId":"corr-extend"}
            """, MediaType.APPLICATION_JSON));

    LosLockServiceClient client = new LosLockServiceClient(builder, "https://lock-service.test");
    LosApiModels.LosLockResponse confirmed = client.requestLock(
        "tenant-los", new LosApiModels.LosLockRequest("pricing-1", "offer-1", 30, "loan-officer"), null,
        "idem-lock", "corr-lock");
    LosApiModels.LosLockResponse extended = client.extendLock(
        "tenant-los", confirmed, new LosApiModels.LosLockExtendRequest(7, "loan-officer", "borrower requested extension"),
        "idem-extend", "corr-extend");

    assertThat(confirmed.status()).isEqualTo("REQUESTED");
    assertThat(extended.lockExpiration()).isEqualTo(Instant.parse("2026-07-25T08:00:00Z"));
    server.verify();
  }

  private LosApiModels.QuoteServiceRequest quoteRequest(String requestId) {
    return new LosApiModels.QuoteServiceRequest("tenant-los", null, 0, java.util.List.of(30), java.util.Map.of("source", "LOS"),
        "los:" + requestId, "idem-" + requestId, "corr-1", true, requestId, null, null, null,
        new LosApiModels.LoanPassBorrowerInfo("Rivera", "LN-001", 1),
        new LosApiModels.LoanPassAddress(null, null, "TX", "78701", null, null, null, null),
        new java.math.BigDecimal("450000"), null, null, "purchase", "single-family", "primary-residence", 1,
        "full-documentation", null, null, null, null, 745, "conventional", "fixed", "30-year", 30, "30-day",
        "retail", java.util.List.of());
  }
}
