package com.wcpe.pricingbff.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationField;
import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationValue;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecuteProductRequest;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecuteSummaryRequest;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionProductSummary;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionSummaryResponse;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionSummaryTotals;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassProductExecutionResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
  private final ObjectMapper objectMapper = new ObjectMapper();

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
    return executeSummary(tenantId, runId, traceId, List.of());
  }

  LoanPassExecutionSummaryResponse executeSummary(String tenantId, String runId, String traceId,
      List<CreditApplicationField> creditApplicationFields) {
    requireConfigured("LoanPass execute-summary");
    String serviceTenantId = stableServiceTenantId(tenantId);
    LoanPassExecuteSummaryRequest request = new LoanPassExecuteSummaryRequest(serviceTenantId, null, Instant.now(),
        safeFields(creditApplicationFields), List.of(), outputFieldsFilter(), publishedVersionRequest(), runId);
    try {
      String responseBody = restClient.post()
          .uri(URI.create(baseUrl + executeSummaryPath))
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Tenant-ID", serviceTenantId)
          .header("X-Correlation-ID", traceId)
          .body(request)
          .retrieve()
          .body(String.class);
      return normalizeSummary(responseBody);
    } catch (RestClientException ex) {
      throw new QuoteServiceUnavailableException("Quote-service LoanPass execute-summary is unavailable; BFF kept browser flow fail-closed.");
    } catch (IllegalArgumentException ex) {
      throw new QuoteServiceUnavailableException("Quote-service LoanPass execute-summary response could not be normalized; BFF kept browser flow fail-closed.");
    }
  }

  LoanPassProductExecutionResult executeProduct(String tenantId, String runId, String productId, String traceId) {
    return executeProduct(tenantId, runId, productId, traceId, List.of());
  }

  LoanPassProductExecutionResult executeProduct(String tenantId, String runId, String productId, String traceId,
      List<CreditApplicationField> creditApplicationFields) {
    requireConfigured("LoanPass execute-product");
    String serviceTenantId = stableServiceTenantId(tenantId);
    LoanPassExecuteProductRequest request = new LoanPassExecuteProductRequest(serviceTenantId, productId, null, Instant.now(),
        safeFields(creditApplicationFields), List.of(), outputFieldsFilter(), publishedVersionRequest(), runId);
    try {
      String responseBody = restClient.post()
          .uri(URI.create(baseUrl + executeProductPath))
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Tenant-ID", serviceTenantId)
          .header("X-Correlation-ID", traceId)
          .body(request)
          .retrieve()
          .body(String.class);
      return normalizeProductResponse(responseBody);
    } catch (RestClientException ex) {
      throw new QuoteServiceUnavailableException("Quote-service LoanPass execute-product is unavailable; BFF kept product detail fail-closed.");
    } catch (IllegalArgumentException ex) {
      throw new QuoteServiceUnavailableException("Quote-service LoanPass execute-product response could not be normalized; BFF kept product detail fail-closed.");
    }
  }

  static String stableServiceTenantId(String tenantId) {
    String normalized = tenantId == null ? "" : tenantId.trim();
    if (normalized.isBlank()) return normalized;
    try {
      return UUID.fromString(normalized).toString();
    } catch (IllegalArgumentException ex) {
      return UUID.nameUUIDFromBytes(("pricing-bff-ui-tenant:" + normalized).getBytes(StandardCharsets.UTF_8)).toString();
    }
  }

  private List<CreditApplicationField> safeFields(List<CreditApplicationField> fields) {
    return fields == null ? List.of() : List.copyOf(fields);
  }

  private Map<String, Object> outputFieldsFilter() {
    return Map.of("type", "all", "version", "current", "includeMetadata", true);
  }

  private Map<String, Object> publishedVersionRequest() {
    return Map.of("type", "current", "includeMetadata", true);
  }

  private LoanPassExecutionSummaryResponse normalizeSummary(String responseBody) {
    JsonNode root = readJson(responseBody);
    JsonNode productsNode = root.path("products");
    List<LoanPassExecutionProductSummary> products = new ArrayList<>();
    if (productsNode.isArray()) {
      for (JsonNode product : productsNode) {
        products.add(toProductSummary(product, versionNumber(root)));
      }
    }
    return new LoanPassExecutionSummaryResponse(summaryTotals(root, products.size()), products, versionNumber(root), metadata(root));
  }

  private LoanPassProductExecutionResult normalizeProductResponse(String responseBody) {
    JsonNode root = readJson(responseBody);
    JsonNode product = root.has("product") && root.get("product").isObject() ? root.get("product") : root;
    return toProductExecution(product, metadata(root), versionNumber(root));
  }

  private LoanPassExecutionProductSummary toProductSummary(JsonNode product, String defaultVersionNumber) {
    return new LoanPassExecutionProductSummary(text(product, "productId"), text(product, "productName"),
        text(product, "productCode"), text(product, "investorName"), text(product, "investorCode"),
        productFields(product), calculatedFields(product), pricingEnabled(product), status(product),
        productVersion(product, defaultVersionNumber));
  }

  private LoanPassProductExecutionResult toProductExecution(JsonNode product, Map<String, Object> metadata,
      String defaultVersionNumber) {
    return new LoanPassProductExecutionResult(text(product, "productId"), text(product, "productName"),
        text(product, "productCode"), text(product, "investorName"), text(product, "investorCode"),
        pricingEnabled(product), productFields(product), calculatedFields(product), status(product),
        productVersion(product, defaultVersionNumber), metadata);
  }

  private LoanPassExecutionSummaryTotals summaryTotals(JsonNode root, int productCount) {
    JsonNode totals = root.path("totals");
    if (totals.isObject()) {
      return new LoanPassExecutionSummaryTotals(intValue(totals, "approved"), firstInt(totals, "reviewRequired", "review_required"),
          intValue(totals, "available"), intValue(totals, "rejected"), intValue(totals, "error"),
          firstInt(totals, "noPricing", "no_pricing"));
    }
    JsonNode counts = root.path("statusCounts");
    int available = intValue(root, "productCount");
    if (available == 0) available = intValue(counts, "available");
    if (available == 0) available = productCount;
    return new LoanPassExecutionSummaryTotals(intValue(counts, "approved"), firstInt(counts, "review_required", "reviewRequired"),
        available, intValue(counts, "rejected"), intValue(counts, "error"),
        firstInt(counts, "no_pricing", "noPricing"));
  }

  private List<CreditApplicationField> productFields(JsonNode product) {
    List<CreditApplicationField> fields = new ArrayList<>(fieldsFrom(product.path("productFields")));
    addMetadataField(fields, "field@quote-service-rules", "quote-service-rules", product.path("rules"));
    return List.copyOf(fields);
  }

  private List<CreditApplicationField> calculatedFields(JsonNode product) {
    List<CreditApplicationField> fields = new ArrayList<>(fieldsFrom(product.path("calculatedFields")));
    addMetadataField(fields, "field@quote-service-rates", "quote-service-rates", firstPresent(product, "rates", "rateOptions"));
    addMetadataField(fields, "field@quote-service-stipulations", "quote-service-stipulations", product.path("stipulations"));
    addMetadataField(fields, "field@quote-service-calculations", "quote-service-calculations", firstPresent(product, "calculations", "calculatedValues"));
    addMetadataField(fields, "field@quote-service-lock-periods", "quote-service-lock-periods", product.path("lockPeriods"));
    return List.copyOf(fields);
  }

  private List<CreditApplicationField> fieldsFrom(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<CreditApplicationField> fields = new ArrayList<>();
    for (JsonNode item : node) {
      String fieldId = text(item, "fieldId");
      if (!fieldId.isBlank()) {
        JsonNode value = item.path("value");
        CreditApplicationValue fieldValue = value.isObject()
            ? new CreditApplicationValue(text(value, "type"), jsonValue(value.path("value")), text(value, "enumTypeId"), text(value, "variantId"))
            : new CreditApplicationValue("metadata", jsonValue(value), null, null);
        fields.add(new CreditApplicationField(fieldId, fieldValue));
      }
    }
    return List.copyOf(fields);
  }

  private void addMetadataField(List<CreditApplicationField> fields, String fieldId, String metadataType, JsonNode value) {
    if (value != null && !value.isMissingNode() && !value.isNull() && !(value.isArray() && value.isEmpty())
        && !(value.isObject() && value.isEmpty()) && !(value.isTextual() && value.asText().isBlank())) {
      fields.add(new CreditApplicationField(fieldId, new CreditApplicationValue("metadata", jsonValue(value), metadataType, null)));
    }
  }

  private Map<String, Object> status(JsonNode product) {
    JsonNode status = product.path("status");
    Map<String, Object> mapped = new LinkedHashMap<>();
    if (status.isObject()) {
      mapped.putAll(map(status));
    } else if (status.isValueNode() && !status.asText().isBlank()) {
      mapped.put("type", status.asText());
    }
    if (product.has("success")) mapped.put("success", product.get("success").asBoolean(false));
    return Map.copyOf(mapped);
  }

  private Boolean pricingEnabled(JsonNode product) {
    if (product.has("isPricingEnabled")) return product.get("isPricingEnabled").asBoolean(false);
    if (product.has("success")) return product.get("success").asBoolean(false);
    return null;
  }

  private Map<String, Object> metadata(JsonNode root) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (root.path("metadata").isObject()) metadata.putAll(map(root.path("metadata")));
    putIfPresent(metadata, "operation", root.path("operation"));
    putIfPresent(metadata, "success", root.path("success"));
    putIfPresent(metadata, "statusCounts", root.path("statusCounts"));
    putIfPresent(metadata, "versionMetadata", root.path("versionMetadata"));
    putIfPresent(metadata, "source", root.path("source"));
    metadata.putIfAbsent("versionNumber", versionNumber(root));
    return Map.copyOf(metadata);
  }

  private void putIfPresent(Map<String, Object> target, String key, JsonNode value) {
    if (value != null && !value.isMissingNode() && !value.isNull()) target.put(key, jsonValue(value));
  }

  private String versionNumber(JsonNode root) {
    String version = firstText(root, "versionNumber", "version");
    if (!version.isBlank()) return version;
    JsonNode metadata = root.path("versionMetadata");
    return firstText(metadata, "versionNumber", "version", "catalogVersion");
  }

  private String productVersion(JsonNode product, String defaultVersionNumber) {
    String version = firstText(product, "versionNumber", "version");
    if (!version.isBlank()) return version;
    JsonNode metadata = product.path("versionMetadata");
    version = firstText(metadata, "versionNumber", "version", "catalogVersion");
    return version.isBlank() ? defaultVersionNumber : version;
  }

  private JsonNode readJson(String responseBody) {
    if (responseBody == null || responseBody.isBlank()) throw new IllegalArgumentException("empty quote-service response");
    try {
      return objectMapper.readTree(responseBody);
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid quote-service response", ex);
    }
  }

  private JsonNode firstPresent(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (!value.isMissingNode() && !value.isNull()) return value;
    }
    return MissingNode.getInstance();
  }

  private String firstText(JsonNode node, String... keys) {
    for (String key : keys) {
      String value = text(node, key);
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private String text(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.path(key);
    return value == null || value.isMissingNode() || value.isNull() ? "" : value.asText().trim();
  }

  private int intValue(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.path(key);
    return value == null || value.isMissingNode() || value.isNull() ? 0 : value.asInt(0);
  }

  private int firstInt(JsonNode node, String firstKey, String secondKey) {
    return firstInt(node, firstKey, secondKey, 0);
  }

  private int firstInt(JsonNode node, String firstKey, String secondKey, int fallback) {
    JsonNode first = node == null ? null : node.path(firstKey);
    if (first != null && !first.isMissingNode() && !first.isNull()) return first.asInt(fallback);
    JsonNode second = node == null ? null : node.path(secondKey);
    return second == null || second.isMissingNode() || second.isNull() ? fallback : second.asInt(fallback);
  }

  private Object jsonValue(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    return objectMapper.convertValue(node, Object.class);
  }

  private Map<String, Object> map(JsonNode node) {
    if (node == null || !node.isObject()) return Map.of();
    Map<String, Object> raw = objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    Map<String, Object> safe = new LinkedHashMap<>();
    raw.forEach((key, value) -> {
      if (key != null && value != null) safe.put(key, value);
    });
    return Map.copyOf(safe);
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
