package com.wcpe.pricingbff.ui;

import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecuteProductRequest;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecuteSummaryRequest;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionSummaryResponse;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassProductExecutionResult;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class PricingBffQuoteServiceLoanPassClient {
  private static final String DEFAULT_LOANPASS_SUMMARY_PATH = "/api/v1/loanpass/execute-summary";
  private static final String DEFAULT_LOANPASS_PRODUCT_PATH = "/api/v1/loanpass/execute-product";
  private final RestClient restClient;
  private final String baseUrl;
  private final String executeSummaryPath;
  private final String executeProductPath;

  PricingBffQuoteServiceLoanPassClient(RestClient.Builder restClientBuilder,
      @Value("${loanweft.integrations.quote-service.base-url:}") String baseUrl,
      @Value("${loanweft.integrations.quote-service.loanpass-summary-path:" + DEFAULT_LOANPASS_SUMMARY_PATH + "}") String executeSummaryPath,
      @Value("${loanweft.integrations.quote-service.loanpass-product-path:" + DEFAULT_LOANPASS_PRODUCT_PATH + "}") String executeProductPath) {
    this.restClient = restClientBuilder.build();
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    this.executeSummaryPath = normalizePath(executeSummaryPath, DEFAULT_LOANPASS_SUMMARY_PATH);
    this.executeProductPath = normalizePath(executeProductPath, DEFAULT_LOANPASS_PRODUCT_PATH);
  }

  LoanPassExecutionSummaryResponse executeSummary(String tenantId, String runId, String traceId) {
    requireConfigured("LoanPass execute-summary");
    LoanPassExecuteSummaryRequest request = new LoanPassExecuteSummaryRequest(tenantId, null, Instant.now(),
        List.of(), List.of(), Map.of(), Map.of(), runId);
    try {
      return restClient.post()
          .uri(URI.create(baseUrl + executeSummaryPath))
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Tenant-ID", tenantId)
          .header("X-Correlation-ID", traceId)
          .body(request)
          .retrieve()
          .body(LoanPassExecutionSummaryResponse.class);
    } catch (RestClientException ex) {
      throw new QuoteServiceUnavailableException("Quote-service LoanPass execute-summary is unavailable; BFF kept browser flow fail-closed.");
    }
  }

  LoanPassProductExecutionResult executeProduct(String tenantId, String runId, String productId, String traceId) {
    requireConfigured("LoanPass execute-product");
    LoanPassExecuteProductRequest request = new LoanPassExecuteProductRequest(tenantId, productId, null, Instant.now(),
        List.of(), List.of(), Map.of(), Map.of(), runId);
    try {
      return restClient.post()
          .uri(URI.create(baseUrl + executeProductPath))
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Tenant-ID", tenantId)
          .header("X-Correlation-ID", traceId)
          .body(request)
          .retrieve()
          .body(LoanPassProductExecutionResult.class);
    } catch (RestClientException ex) {
      throw new QuoteServiceUnavailableException("Quote-service LoanPass execute-product is unavailable; BFF kept product detail fail-closed.");
    }
  }

  private void requireConfigured(String operation) {
    if (baseUrl.isBlank()) {
      throw new QuoteServiceUnavailableException("Quote-service base URL is not configured for " + operation + "; BFF kept browser flow fail-closed.");
    }
  }

  private static String normalizePath(String path, String fallback) {
    if (path == null || path.isBlank()) return fallback;
    return path.startsWith("/") ? path : "/" + path;
  }

  static class QuoteServiceUnavailableException extends RuntimeException {
    QuoteServiceUnavailableException(String message) {
      super(message);
    }
  }
}
