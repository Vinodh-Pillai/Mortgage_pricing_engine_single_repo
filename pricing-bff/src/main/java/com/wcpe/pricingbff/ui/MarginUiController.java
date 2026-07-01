package com.wcpe.pricingbff.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
class MarginUiController {
  private final MarginServiceProfitabilityClient marginClient;

  MarginUiController(MarginServiceProfitabilityClient marginClient) {
    this.marginClient = marginClient;
  }

  @GetMapping({
      "/api/v1/tenants/{tenantId}/margin/profitability",
      "/api/v1/tenants/{tenantId}/margins/profitability",
      "/api/v1/tenants/{tenantId}/margin-profitability"})
  ResponseEntity<?> tenantMarginProfitability(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return marginClient.profitability(tenantId, uiTraceId);
  }

  @GetMapping({
      "/api/v1/margins/profitability",
      "/api/v1/margin/profitability",
      "/api/v1/margin-profitability",
      "/margins/profitability",
      "/margin/profitability",
      "/margin-profitability"})
  ResponseEntity<?> marginProfitability(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return marginClient.profitability(tenantContext, uiTraceId);
  }
}

@Component
class MarginServiceProfitabilityClient {
  private static final String DEFAULT_PROFITABILITY_PATH = "/api/v1/margins/profitability";
  private final RestClient restClient;
  private final boolean configured;

  MarginServiceProfitabilityClient(RestClient.Builder restClientBuilder,
      @Value("${loanweft.integrations.margin-service.base-url:${MARGIN_SERVICE_BASE_URL:}}") String baseUrl) {
    String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
    this.configured = !normalizedBaseUrl.isBlank();
    this.restClient = configured ? restClientBuilder.baseUrl(normalizedBaseUrl).build() : null;
  }

  ResponseEntity<?> profitability(String tenantContext, String uiTraceId) {
    String tenant = tenantContext == null || tenantContext.isBlank() ? "ui-preview-tenant" : tenantContext;
    String trace = uiTraceId == null || uiTraceId.isBlank() ? "margin-profitability-fail-closed" : uiTraceId;
    if (!configured) {
      return failClosed(tenant, trace, "MARGIN_SERVICE_BASE_URL_NOT_CONFIGURED",
          "Configure loanweft.integrations.margin-service.base-url or MARGIN_SERVICE_BASE_URL before profitability evidence can be read.");
    }
    try {
      ResponseEntity<Object> response = restClient.get()
          .uri(DEFAULT_PROFITABILITY_PATH)
          .header("X-Tenant-Context", tenant)
          .header("X-Ui-Trace-Id", trace)
          .retrieve()
          .toEntity(Object.class);
      return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    } catch (RestClientException exception) {
      return failClosed(tenant, trace, "MARGIN_SERVICE_PROFITABILITY_CONTRACT_UNAVAILABLE",
          "Configured margin-service profitability endpoint did not return usable evidence; keep profitability UI blocked and do not fabricate evidence.");
    }
  }

  private ResponseEntity<Map<String, Object>> failClosed(String tenantContext, String uiTraceId, String code,
      String actionableBlocker) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "BLOCKED");
    body.put("code", code);
    body.put("dependencyStatus", "MARGIN_SERVICE_PROFITABILITY_CONTRACT_BLOCKED");
    body.put("tenantContext", tenantContext);
    body.put("uiTraceId", uiTraceId);
    body.put("retryable", false);
    body.put("clientAction", "SHOW_MARGIN_PROFITABILITY_CONTRACT_BLOCKER");
    body.put("actionableBlocker", actionableBlocker);
    body.put("events", List.of("MarginProfitabilityContractBlocked"));
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
  }
}
