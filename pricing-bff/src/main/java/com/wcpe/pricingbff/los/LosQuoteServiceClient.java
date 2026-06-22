package com.wcpe.pricingbff.los;

import com.wcpe.pricingbff.los.LosApiModels.QuoteServiceRequest;
import com.wcpe.pricingbff.los.LosApiModels.QuoteServiceResponse;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class LosQuoteServiceClient {
  private final RestClient restClient;
  private final String baseUrl;

  LosQuoteServiceClient(RestClient.Builder restClientBuilder,
      @Value("${loanweft.integrations.quote-service.base-url:}") String baseUrl) {
    this.restClient = restClientBuilder.build();
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
  }

  QuoteServiceResponse submitQuoteJob(QuoteServiceRequest request) {
    if (baseUrl.isBlank()) {
      throw new LosValidationException("QUOTE_SERVICE_INTEGRATION_REQUIRED",
          "quote-service base URL is not configured; LOS pricing request was not started");
    }
    return restClient.post()
        .uri(URI.create(baseUrl + "/api/v1/los/quote-requests"))
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Request-ID", request.idempotencyKey())
        .header("X-Correlation-ID", request.correlationId())
        .body(request)
        .retrieve()
        .body(QuoteServiceResponse.class);
  }
}
