package com.wcpe.pricingbff.crm;

import com.wcpe.pricingbff.crm.CrmApiModels.QuoteServiceRequest;
import com.wcpe.pricingbff.crm.CrmApiModels.QuoteServiceResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class CrmQuoteServiceClient {
  private final RestClient restClient;
  private final String baseUrl;

  CrmQuoteServiceClient(RestClient.Builder restClientBuilder,
      @Value("${loanweft.integrations.quote-service.base-url:}") String baseUrl) {
    this.restClient = restClientBuilder.build();
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
  }

  QuoteServiceResponse submitQuoteJob(QuoteServiceRequest request) {
    if (!baseUrl.isBlank()) {
      return restClient.post()
          .uri(URI.create(baseUrl + "/api/v1/los/quote-requests"))
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Request-ID", request.idempotencyKey())
          .header("X-Correlation-ID", request.correlationId())
          .body(request)
          .retrieve()
          .body(QuoteServiceResponse.class);
    }
    String jobId = UUID.nameUUIDFromBytes((request.tenantId() + ":crm:" + request.scenarioId() + ":" + request.idempotencyKey()).getBytes(StandardCharsets.UTF_8)).toString();
    return new QuoteServiceResponse(jobId, "QUEUED", "/api/v1/los/quote-requests/" + jobId, request.correlationId());
  }
}
