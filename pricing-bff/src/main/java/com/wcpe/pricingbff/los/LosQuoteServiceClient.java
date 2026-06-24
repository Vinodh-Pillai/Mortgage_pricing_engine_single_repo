package com.wcpe.pricingbff.los;

import com.wcpe.pricingbff.los.LosApiModels.QuoteServiceRequest;
import com.wcpe.pricingbff.los.LosApiModels.QuoteServiceResponse;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class LosQuoteServiceClient {
  private static final String DEFAULT_LOANPASS_SUMMARY_PATH = "/api/v1/loanpass/execute-summary";
  private static final String DEFAULT_LOANPASS_PRODUCT_PATH = "/api/v1/loanpass/execute-product";
  private final RestClient restClient;
  private final String baseUrl;
  private final String executeSummaryPath;
  private final String executeProductPath;

  @Autowired
  LosQuoteServiceClient(RestClient.Builder restClientBuilder,
      @Value("${loanweft.integrations.quote-service.base-url:}") String baseUrl,
      @Value("${loanweft.integrations.quote-service.loanpass-summary-path:" + DEFAULT_LOANPASS_SUMMARY_PATH + "}") String executeSummaryPath,
      @Value("${loanweft.integrations.quote-service.loanpass-product-path:" + DEFAULT_LOANPASS_PRODUCT_PATH + "}") String executeProductPath) {
    this.restClient = restClientBuilder.build();
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    this.executeSummaryPath = normalizePath(executeSummaryPath, DEFAULT_LOANPASS_SUMMARY_PATH);
    this.executeProductPath = normalizePath(executeProductPath, DEFAULT_LOANPASS_PRODUCT_PATH);
  }

  LosQuoteServiceClient(RestClient.Builder restClientBuilder, String baseUrl) {
    this(restClientBuilder, baseUrl, DEFAULT_LOANPASS_SUMMARY_PATH, DEFAULT_LOANPASS_PRODUCT_PATH);
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

  boolean hasDurableLoanPassExecuteIntegration() {
    return !baseUrl.isBlank();
  }

  Map<String, Object> executeSummary(Map<String, Object> request, String tenantId, String correlationId) {
    requireConfigured("LoanPass execute-summary");
    return execute(executeSummaryPath, request, tenantId, correlationId, "execute-summary");
  }

  Map<String, Object> executeProduct(Map<String, Object> request, String tenantId, String correlationId) {
    requireConfigured("LoanPass execute-product");
    return execute(executeProductPath, request, tenantId, correlationId, "execute-product");
  }

  private Map<String, Object> execute(String path, Map<String, Object> request, String tenantId, String correlationId,
      String operation) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response = restClient.post()
          .uri(URI.create(baseUrl + path))
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Tenant-ID", tenantId)
          .header("X-Correlation-ID", correlationId)
          .body(request == null ? Map.of() : request)
          .retrieve()
          .body(Map.class);
      return response == null ? Map.of() : Map.copyOf(response);
    } catch (RestClientException ex) {
      throw new LosValidationException("QUOTE_SERVICE_LOANPASS_EXECUTE_UNAVAILABLE",
          "quote-service " + operation + " is unavailable; LOS execute flow was kept fail-closed");
    }
  }

  private void requireConfigured(String operation) {
    if (baseUrl.isBlank()) {
      throw new LosValidationException("QUOTE_SERVICE_INTEGRATION_REQUIRED",
          "quote-service base URL is not configured for " + operation + "; LOS execute flow was not started");
    }
  }

  private static String normalizePath(String path, String fallback) {
    if (path == null || path.isBlank()) return fallback;
    return path.startsWith("/") ? path : "/" + path;
  }
}
