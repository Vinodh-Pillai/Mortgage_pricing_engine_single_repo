package com.wcpe.pricingbff.los;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricingbff.los.LosApiModels.LosLockExtendRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosLockRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosLockResponse;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecuteProductRequest;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecuteSummaryRequest;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionProductSummary;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionSummaryResponse;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionSummaryTotals;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassProductExecutionResult;
import com.wcpe.pricingbff.los.LosApiModels.LosOffer;
import com.wcpe.pricingbff.los.LosApiModels.LosProductCatalogResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosProductDetailResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosProductEligibilityFieldMessage;
import com.wcpe.pricingbff.los.LosApiModels.LosProductEligibilityRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosProductEligibilityResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosProductEligibilityResult;
import com.wcpe.pricingbff.los.LosApiModels.LosProductEligibilityRuleRef;
import com.wcpe.pricingbff.los.LosApiModels.LosPricingRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosPricingResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosScenario;
import com.wcpe.pricingbff.los.LosApiModels.LosWebhookRegistrationRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosWebhookRegistrationResponse;
import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationField;
import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationValue;
import com.wcpe.pricingbff.los.LosApiModels.QuoteServiceRequest;
import com.wcpe.pricingbff.los.LosApiModels.QuoteServiceResponse;
import com.wcpe.pricingbff.los.LosApiModels.WebhookEvent;
import com.wcpe.pricingbff.los.LosFeatureFlagService.LoanPassTenantFlags;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
class LosPricingService {
  static final String LOANPASS_PUBLIC_API_DOCS_URL = "https://docs.loanpass.io/public-api/index.html";
  static final String LOANPASS_PUBLIC_API_SCHEMA_URL = "https://api.loanpass.io/v1/swagger/schema.json";
  static final List<String> LOANPASS_PUBLIC_API_OPERATION_CONCEPTS = List.of("execute-summary", "execute-product");
  static final String LOANPASS_CONTRACT_FIELD_POLICY = "concept-aligned-only-no-request-response-fields-adopted";
  private static final List<String> REQUIRED_EXECUTE_METADATA_REFS = List.of(
      "productCatalogRef", "productAuthorizationMetadataRef", "ruleCatalogRef", "stipulationCatalogRef",
      "rateCatalogRef", "lockTermCatalogRef");
  private static final Set<String> SUPPORTED_PRODUCT_SEARCH_FILTERS = Set.of(
      "productFamily", "channel", "investor", "loanPurpose", "occupancy", "propertyType",
      "term", "amortization", "effectiveDate", "q", "query", "active", "page", "pageSize", "sort");
  private final ObjectMapper objectMapper;
  private final LosScenarioAdapter scenarioAdapter;
  private final LosQuoteServiceClient quoteClient;
  private final LosLockServiceClient lockClient;
  private final LosWebhookRegistry webhookRegistry;
  private final LosIdempotencyStore idempotencyStore;
  private final LosFeatureFlagService featureFlagService;

  LosPricingService(ObjectMapper objectMapper, LosScenarioAdapter scenarioAdapter, LosQuoteServiceClient quoteClient,
      LosLockServiceClient lockClient, LosWebhookRegistry webhookRegistry, LosIdempotencyStore idempotencyStore) {
    this(objectMapper, scenarioAdapter, quoteClient, lockClient, webhookRegistry, idempotencyStore, new LosFeatureFlagService());
  }

  @Autowired
  LosPricingService(ObjectMapper objectMapper, LosScenarioAdapter scenarioAdapter, LosQuoteServiceClient quoteClient,
      LosLockServiceClient lockClient, LosWebhookRegistry webhookRegistry, LosIdempotencyStore idempotencyStore,
      LosFeatureFlagService featureFlagService) {
    this.objectMapper = objectMapper;
    this.scenarioAdapter = scenarioAdapter;
    this.quoteClient = quoteClient;
    this.lockClient = lockClient;
    this.webhookRegistry = webhookRegistry;
    this.idempotencyStore = idempotencyStore;
    this.featureFlagService = featureFlagService;
  }

  LosPricingResponse createPricingRequest(LosPricingRequest request, String idempotencyKey, String correlationId) {
    validatePricingRequestShape(request);
    LoanPassTenantFlags flags = flagsForTenant(request.tenantId());
    requireLoanPassCompatibility(flags);
    validatePricingRequest(request, flags);
    String requestHash = hash(request);
    Optional<Object> cached = idempotencyStore.cached("POST", "/api/v1/los/pricing-requests", idempotencyKey, requestHash);
    if (cached.isPresent() && cached.get() instanceof LosPricingResponse response) {
      return response;
    }
    LosScenario scenario = scenarioAdapter.toScenario(request);
    List<CreditApplicationField> engineFields = loanPassEngineFields(request);
    Map<String, String> clientContext = quoteClientContext(request, scenario, flags);
    QuoteServiceResponse quoteJob = quoteClient.submitQuoteJob(new QuoteServiceRequest(
        scenario.tenantId(), scenario.scenarioId(), scenario.scenarioVersion(), scenario.requestedLockPeriods(),
        clientContext, "los:" + request.requestId(), idempotencyKey,
        correlationId, true, request.requestId(), request.productId(), request.selectedProgramId(), request.priceGroupId(),
        request.quoteBorrowerInfo(), request.quoteAddressDTO(), request.requestedLoanAmount(),
        request.purchasePrice(), request.propertyValue(), request.transactionType(),
        request.propertyInformationType(), request.occupancyType(), request.numberOfUnits(), request.incomeDocumentationType(),
        request.totalMonthlyIncome(), request.totalLiabilityMonthlyPayment(), request.debtToIncomeRatio(), request.monthsOfReserves(),
        request.creditScore(), request.mortgageType(), request.amortizationType(), request.loanTermType(), request.desiredRateLockPeriod(),
        request.lockPeriodType(), request.channelType(), engineFields));

    String pricingRequestId = UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.requestId()).getBytes(StandardCharsets.UTF_8)).toString();
    LosPricingResponse response = new LosPricingResponse(request.requestId(), pricingRequestId, "ACCEPTED", List.of(), List.of(),
        null, "/api/v1/los/pricing-requests/" + pricingRequestId, quoteJob.jobId(), correlationId);
    idempotencyStore.store("POST", "/api/v1/los/pricing-requests", idempotencyKey, requestHash, response);
    return response;
  }

  LoanPassExecutionSummaryResponse executeSummary(LoanPassExecuteSummaryRequest request, String headerTenantId,
      String correlationId) {
    validateExecuteSummaryRequest(request, headerTenantId);
    String tenantId = tenantId(request.tenantId(), headerTenantId);
    LoanPassTenantFlags flags = flagsForTenant(tenantId);
    requireLoanPassCompatibility(flags);
    validateCreditApplicationEnumMappings(request.creditApplicationFields(), flags);
    if (quoteClient.hasDurableLoanPassExecuteIntegration()) {
      return durableQuoteExecuteSummary(request, tenantId, correlationId);
    }
    Map<String, Object> metadata = executeMetadata("/api/v1/los/execute-summary", "execute-summary", tenantId,
        request.pricingProfileId(), request.pipelineRecordId(), request.currentTime(), request.outputFieldsFilter(),
        request.publishedVersionRequest(), request.creditApplicationFields().size(), request.ausFields().size(), correlationId, flags);
    List<String> missingMetadata = missingExecuteMetadata(request.publishedVersionRequest());
    if (!missingMetadata.isEmpty()) {
      return new LoanPassExecutionSummaryResponse(new LoanPassExecutionSummaryTotals(0, 0, 0, 0, 0), List.of(), null,
          blockedExecuteMetadata(metadata, missingMetadata));
    }
    List<LoanPassExecutionProductSummary> products = executeProducts(request.publishedVersionRequest()).stream()
        .map(product -> toExecutionSummary(product, versionNumber(request.publishedVersionRequest())))
        .toList();
    return new LoanPassExecutionSummaryResponse(new LoanPassExecutionSummaryTotals(0, 0, products.size(), 0, 0), products,
        versionNumber(request.publishedVersionRequest()), readyExecuteMetadata(metadata, request.publishedVersionRequest()));
  }

  LoanPassProductExecutionResult executeProduct(LoanPassExecuteProductRequest request, String headerTenantId,
      String correlationId) {
    validateExecuteProductRequest(request, headerTenantId);
    String tenantId = tenantId(request.tenantId(), headerTenantId);
    LoanPassTenantFlags flags = flagsForTenant(tenantId);
    requireLoanPassCompatibility(flags);
    validateCreditApplicationEnumMappings(request.creditApplicationFields(), flags);
    if (quoteClient.hasDurableLoanPassExecuteIntegration()) {
      return durableQuoteExecuteProduct(request, tenantId, correlationId);
    }
    Map<String, Object> metadata = executeMetadata("/api/v1/los/execute-product", "execute-product", tenantId,
        request.pricingProfileId(), request.pipelineRecordId(), request.currentTime(), request.outputFieldsFilter(),
        request.publishedVersionRequest(), request.creditApplicationFields().size(), request.ausFields().size(), correlationId, flags);
    List<String> missingMetadata = missingExecuteMetadata(request.publishedVersionRequest());
    if (!missingMetadata.isEmpty()) {
      return new LoanPassProductExecutionResult(request.productId().trim(), "", "", "", "", false,
          List.of(), List.of(), productExecutionBlockedStatus("TENANT_PRODUCT_AUTHORIZATION_UNAVAILABLE",
              "Catalog-backed product, rule, stipulation, rate, and lock-period metadata is required before product execution can run"),
          null, blockedExecuteMetadata(metadata, missingMetadata));
    }
    Optional<Map<String, Object>> product = executeProducts(request.publishedVersionRequest()).stream()
        .filter(candidate -> request.productId().trim().equals(text(candidate, "productId")))
        .findFirst();
    if (product.isEmpty()) {
      return new LoanPassProductExecutionResult(request.productId().trim(), "", "", "", "", false,
          List.of(), List.of(), productExecutionBlockedStatus("PRODUCT_METADATA_NOT_FOUND",
              "Product metadata was not found in the supplied catalog-backed execution metadata"),
          null, blockedExecuteMetadata(metadata, List.of("products[productId=" + request.productId().trim() + "]")));
    }
    return toProductExecution(product.get(), readyExecuteMetadata(metadata, request.publishedVersionRequest()),
        versionNumber(request.publishedVersionRequest()));
  }

  private Map<String, String> quoteClientContext(LosPricingRequest request, LosScenario scenario, LoanPassTenantFlags flags) {
    Map<String, String> context = new LinkedHashMap<>();
    context.put("source", "LOS");
    context.put("losRequestId", request.requestId());
    context.put("pipelineScenarioLinked", Boolean.toString(!blank(scenario.scenarioId())));
    context.put("requestSnapshotRef", scenario.loanFacts().getOrDefault("requestSnapshotRef", ""));
    context.put("mappingConfigRef", scenario.loanFacts().getOrDefault("mappingConfigRef", ""));
    context.put("tenantFeatureFlagConfigRef", flags.configRef());
    context.put("tenantFeatureFlagAuditRef", flags.auditRef());
    context.put("tenantFeatureFlagVersion", Integer.toString(flags.version()));
    context.put("loanPassPublicApiDocsUrl", LOANPASS_PUBLIC_API_DOCS_URL);
    context.put("loanPassPublicApiSchemaUrl", LOANPASS_PUBLIC_API_SCHEMA_URL);
    context.put("loanPassPublicApiOperationConcepts", String.join(",", LOANPASS_PUBLIC_API_OPERATION_CONCEPTS));
    context.put("loanPassContractFieldPolicy", LOANPASS_CONTRACT_FIELD_POLICY);
    context.put("productAuthorizationPolicy", "fail-closed-until-tenant-product-authorization-metadata-exists");
    if (!blank(scenario.scenarioId())) {
      context.put("scenarioRef", scenario.scenarioId());
      context.put("scenarioVersion", Integer.toString(scenario.scenarioVersion()));
    }
    return Map.copyOf(context);
  }

  private LoanPassExecutionSummaryResponse durableQuoteExecuteSummary(LoanPassExecuteSummaryRequest request, String tenantId,
      String correlationId) {
    Map<String, Object> quoteRequest = durableQuoteExecuteSummaryRequest(request);
    Map<String, Object> durable = quoteClient.executeSummary(quoteRequest, tenantId, correlationId);
    List<Map<String, Object>> quoteProducts = listOfMaps(durable.get("products"));
    List<LoanPassExecutionProductSummary> products = quoteProducts.stream()
        .map(product -> new LoanPassExecutionProductSummary(
            text(product, "productId"), text(product, "productName"), text(product, "productCode"),
            text(product, "investorName"), text(product, "investorCode"), List.of(),
            durableCalculatedFields(product), booleanValue(product.get("success")), durableStatus(product.get("status")),
            durableVersion(durable)))
        .toList();
    Map<?, ?> counts = durable.get("statusCounts") instanceof Map<?, ?> rawCounts ? rawCounts : Map.of();
    LoanPassExecutionSummaryTotals totals = new LoanPassExecutionSummaryTotals(
        number(counts.get("approved")), number(counts.get("review_required")),
        numberOrDefault(durable.get("productCount"), products.size()), number(counts.get("rejected")), number(counts.get("error")),
        number(counts.get("no_pricing")));
    return new LoanPassExecutionSummaryResponse(totals, products, durableVersion(durable),
        durableMetadata(durable, "execute-summary", "/api/v1/los/execute-summary"));
  }

  private LoanPassProductExecutionResult durableQuoteExecuteProduct(LoanPassExecuteProductRequest request, String tenantId,
      String correlationId) {
    Map<String, Object> quoteRequest = durableQuoteExecuteProductRequest(request);
    Map<String, Object> durable = quoteClient.executeProduct(quoteRequest, tenantId, correlationId);
    return new LoanPassProductExecutionResult(text(durable, "productId"), text(durable, "productName"),
        text(durable, "productCode"), text(durable, "investorName"), text(durable, "investorCode"),
        durableProductPricingEnabled(durable), durableProductFields(durable), durableCalculatedFields(durable),
        durableStatus(durable.get("status")), durableVersion(durable),
        durableMetadata(durable, "execute-product", "/api/v1/los/execute-product"));
  }

  private Map<String, Object> durableQuoteExecuteSummaryRequest(LoanPassExecuteSummaryRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    putIfPresent(payload, "tenantId", request.tenantId());
    putIfPresent(payload, "pricingProfileId", request.pricingProfileId());
    payload.put("currentTime", request.currentTime().toString());
    payload.put("creditApplicationFields", request.creditApplicationFields());
    payload.put("ausFields", request.ausFields());
    payload.put("outputFieldsFilter", request.outputFieldsFilter());
    payload.put("publishedVersionRequest", request.publishedVersionRequest());
    putIfPresent(payload, "pipelineRecordId", request.pipelineRecordId());
    return Map.copyOf(payload);
  }

  private Map<String, Object> durableQuoteExecuteProductRequest(LoanPassExecuteProductRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    putIfPresent(payload, "tenantId", request.tenantId());
    putIfPresent(payload, "productId", request.productId());
    putIfPresent(payload, "selectedProgramId", request.productId());
    putIfPresent(payload, "pricingProfileId", request.pricingProfileId());
    payload.put("currentTime", request.currentTime().toString());
    payload.put("creditApplicationFields", request.creditApplicationFields());
    payload.put("ausFields", request.ausFields());
    payload.put("outputFieldsFilter", request.outputFieldsFilter());
    payload.put("publishedVersionRequest", request.publishedVersionRequest());
    putIfPresent(payload, "pipelineRecordId", request.pipelineRecordId());
    return Map.copyOf(payload);
  }

  private List<Map<String, Object>> listOfMaps(Object value) {
    if (!(value instanceof List<?> values)) return List.of();
    List<Map<String, Object>> mapped = new ArrayList<>();
    for (Object item : values) {
      if (item instanceof Map<?, ?> raw) {
        Map<String, Object> product = new LinkedHashMap<>();
        raw.forEach((key, rawValue) -> {
          if (key != null) product.put(key.toString(), rawValue);
        });
        mapped.add(Map.copyOf(product));
      }
    }
    return List.copyOf(mapped);
  }

  private List<CreditApplicationField> durableProductFields(Map<String, Object> durable) {
    List<CreditApplicationField> fields = new ArrayList<>();
    addMetadataField(fields, "field@quote-service-rules", "quote-service-rules", durable.get("rules"));
    addMetadataField(fields, "field@quote-service-stipulations", "quote-service-stipulations", durable.get("stipulations"));
    addMetadataField(fields, "field@quote-service-rejections", "quote-service-rejections", durable.get("rejections"));
    return List.copyOf(fields);
  }

  private List<CreditApplicationField> durableCalculatedFields(Map<String, Object> durable) {
    List<CreditApplicationField> fields = new ArrayList<>();
    addMetadataField(fields, "field@quote-service-rates", "quote-service-rates", durable.get("rates"));
    addMetadataField(fields, "field@quote-service-lock-periods", "quote-service-lock-periods", durable.get("lockPeriods"));
    addMetadataField(fields, "field@quote-service-calculations", "quote-service-calculations", durable.get("calculations"));
    return List.copyOf(fields);
  }

  private Map<String, Object> durableStatus(Object status) {
    if (status instanceof Map<?, ?> rawStatus) {
      Map<String, Object> mapped = new LinkedHashMap<>();
      rawStatus.forEach((key, value) -> {
        if (key != null) mapped.put(key.toString(), value);
      });
      return Map.copyOf(mapped);
    }
    Map<String, Object> mapped = new LinkedHashMap<>();
    mapped.put("type", blankObject(status) ? "available" : status.toString());
    mapped.put("source", "quote-service");
    return Map.copyOf(mapped);
  }

  private boolean durableProductPricingEnabled(Map<String, Object> durable) {
    if (durable.containsKey("success")) return booleanValue(durable.get("success"));
    String statusType = normalizedStatusType(durable.get("status"));
    return !Set.of("rejected", "no_pricing", "error", "missing_executable_pricing").contains(statusType);
  }

  private String normalizedStatusType(Object status) {
    String raw = "";
    if (status instanceof Map<?, ?> rawStatus) {
      Object type = rawStatus.get("type");
      raw = type == null ? text(rawStatus, "status") : type.toString();
    } else if (!blankObject(status)) {
      raw = status.toString();
    }
    return raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');
  }

  private Map<String, Object> durableMetadata(Map<String, Object> durable, String operationConcept, String bffEndpoint) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("source", "quote-service");
    metadata.put("endpoint", bffEndpoint);
    metadata.put("operationConcept", operationConcept);
    metadata.put("durableQuoteServiceSource", true);
    metadata.put("clientPublishedVersionRequestSourceOfTruth", false);
    putIfPresent(metadata, "quoteServiceOperation", text(durable, "operation"));
    putIfPresent(metadata, "quoteServiceSnapshotId", text(durable, "snapshotId"));
    putIfPresent(metadata, "quoteServiceSourceSystem", text(durable, "sourceSystem"));
    if (durable.containsKey("success")) metadata.put("quoteServiceSuccess", durable.get("success"));
    if (durable.get("statusCounts") instanceof Map<?, ?> statusCounts) metadata.put("quoteServiceStatusCounts", Map.copyOf(statusCounts));
    if (durable.containsKey("synthetic")) metadata.put("quoteServiceSynthetic", durable.get("synthetic"));
    if (durable.get("versionMetadata") instanceof Map<?, ?> versionMetadata) metadata.put("quoteServiceVersionMetadata", Map.copyOf(versionMetadata));
    addLoanPassPublicApiMetadata(metadata);
    return Map.copyOf(metadata);
  }

  private String durableVersion(Map<String, Object> durable) {
    if (durable.get("versionMetadata") instanceof Map<?, ?> versionMetadata) {
      String schemaVersion = text(versionMetadata, "schemaVersion");
      if (!blank(schemaVersion)) return schemaVersion;
      String payloadHash = text(versionMetadata, "payloadHash");
      if (!blank(payloadHash)) return payloadHash;
    }
    return text(durable, "snapshotId");
  }

  private int number(Object value) {
    return numberOrDefault(value, 0);
  }

  private int numberOrDefault(Object value, int defaultValue) {
    if (value instanceof Number number) return number.intValue();
    if (value == null || value.toString().isBlank()) return defaultValue;
    return Integer.parseInt(value.toString());
  }

  private Map<String, Object> executeMetadata(String endpoint, String operationConcept, String tenantId,
      String pricingProfileId, String pipelineRecordId, Instant currentTime, Map<String, Object> outputFieldsFilter,
      Map<String, Object> publishedVersionRequest, int creditApplicationFieldCount, int ausFieldCount,
      String correlationId, LoanPassTenantFlags flags) {
    Map<String, Object> requestedContext = new LinkedHashMap<>();
    requestedContext.put("tenantId", tenantId.trim());
    putIfPresent(requestedContext, "pricingProfileId", pricingProfileId);
    putIfPresent(requestedContext, "pipelineRecordId", pipelineRecordId);
    requestedContext.put("currentTime", currentTime.toString());
    requestedContext.put("creditApplicationFieldCount", creditApplicationFieldCount);
    requestedContext.put("ausFieldCount", ausFieldCount);
    if (outputFieldsFilter != null && !outputFieldsFilter.isEmpty()) requestedContext.put("outputFieldsFilter", Map.copyOf(outputFieldsFilter));
    if (publishedVersionRequest != null && !publishedVersionRequest.isEmpty()) requestedContext.put("publishedVersionRequest", Map.copyOf(publishedVersionRequest));

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("source", "fail-closed");
    addFeatureFlagMetadata(metadata, flags);
    metadata.put("endpoint", endpoint);
    metadata.put("operationConcept", operationConcept);
    metadata.put("requestedContext", Map.copyOf(requestedContext));
    metadata.put("authorizationStatus", "BLOCKED");
    metadata.put("authorizationMetadataStatus", "UNAVAILABLE");
    metadata.put("mappingMetadataStatus", "INCOMPLETE");
    metadata.put("catalogDependency", "catalog-service:tenant-product-authorization-required");
    metadata.put("warnings", List.of("TENANT_PRODUCT_AUTHORIZATION_UNAVAILABLE", "CATALOG_METADATA_NOT_CONFIGURED"));
    putIfPresent(metadata, "correlationId", correlationId);
    addLoanPassPublicApiMetadata(metadata);
    return Map.copyOf(metadata);
  }

  private Map<String, Object> productExecutionBlockedStatus(String code, String message) {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("type", "catalog-service");
    source.put("ref", "tenant-product-authorization");
    Map<String, Object> kind = new LinkedHashMap<>();
    kind.put("type", "configuration-unavailable");
    kind.put("code", code);
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("source", Map.copyOf(source));
    error.put("kind", Map.copyOf(kind));
    error.put("message", message);
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("type", "error");
    status.put("errors", List.of(Map.copyOf(error)));
    return Map.copyOf(status);
  }

  private Map<String, Object> blockedExecuteMetadata(Map<String, Object> baseMetadata, List<String> missingRequirements) {
    Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata);
    metadata.put("missingMetadata", List.copyOf(missingRequirements));
    metadata.put("mappingMetadataStatus", "INCOMPLETE");
    metadata.put("rateMetadataStatus", missingRequirements.contains("rateCatalogRef") ? "UNAVAILABLE" : "AVAILABLE");
    metadata.put("lockPeriodMetadataStatus", missingRequirements.contains("lockTermCatalogRef") ? "UNAVAILABLE" : "AVAILABLE");
    metadata.put("warnings", missingRequirements.stream().map(item -> "MISSING_" + item).toList());
    return Map.copyOf(metadata);
  }

  private Map<String, Object> readyExecuteMetadata(Map<String, Object> baseMetadata, Map<String, Object> publishedVersionRequest) {
    Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata);
    metadata.put("source", "catalog-backed");
    metadata.put("authorizationStatus", "READY");
    metadata.put("authorizationMetadataStatus", "AVAILABLE");
    metadata.put("mappingMetadataStatus", "COMPLETE");
    metadata.put("rateMetadataStatus", "AVAILABLE");
    metadata.put("lockPeriodMetadataStatus", "AVAILABLE");
    metadata.put("catalogDependency", "supplied-publishedVersionRequest");
    metadata.put("metadataRefs", metadataRefs(publishedVersionRequest));
    metadata.put("warnings", List.of());
    return Map.copyOf(metadata);
  }

  private Map<String, Object> metadataRefs(Map<String, Object> publishedVersionRequest) {
    Map<String, Object> refs = new LinkedHashMap<>();
    for (String key : REQUIRED_EXECUTE_METADATA_REFS) {
      refs.put(key, publishedVersionRequest.get(key));
    }
    putIfPresent(refs, "catalogMetadataRef", text(publishedVersionRequest, "catalogMetadataRef"));
    return Map.copyOf(refs);
  }

  private List<String> missingExecuteMetadata(Map<String, Object> publishedVersionRequest) {
    List<String> missing = new ArrayList<>();
    if (publishedVersionRequest == null || publishedVersionRequest.isEmpty()) {
      missing.add("publishedVersionRequest");
      missing.addAll(REQUIRED_EXECUTE_METADATA_REFS);
      missing.add("products");
      return List.copyOf(missing);
    }
    for (String key : REQUIRED_EXECUTE_METADATA_REFS) {
      if (blankObject(publishedVersionRequest.get(key))) {
        missing.add(key);
      }
    }
    if (executeProducts(publishedVersionRequest).isEmpty()) {
      missing.add("products");
    }
    return List.copyOf(missing);
  }

  private List<Map<String, Object>> executeProducts(Map<String, Object> publishedVersionRequest) {
    if (publishedVersionRequest == null || !(publishedVersionRequest.get("products") instanceof List<?> products)) {
      return List.of();
    }
    List<Map<String, Object>> mapped = new ArrayList<>();
    for (Object product : products) {
      if (product instanceof Map<?, ?> rawProduct) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        rawProduct.forEach((key, value) -> {
          if (key != null) normalized.put(key.toString(), value);
        });
        if (!blank(text(normalized, "productId")) && !blank(text(normalized, "productName"))) {
          mapped.add(Map.copyOf(normalized));
        }
      }
    }
    return List.copyOf(mapped);
  }

  private LoanPassExecutionProductSummary toExecutionSummary(Map<String, Object> product, String defaultVersionNumber) {
    return new LoanPassExecutionProductSummary(text(product, "productId"), text(product, "productName"),
        text(product, "productCode"), text(product, "investorName"), text(product, "investorCode"),
        productFields(product), calculatedFields(product), booleanValue(product.get("isPricingEnabled")),
        productExecutionStatus(product), productVersion(product, defaultVersionNumber));
  }

  private LoanPassProductExecutionResult toProductExecution(Map<String, Object> product, Map<String, Object> metadata,
      String defaultVersionNumber) {
    return new LoanPassProductExecutionResult(text(product, "productId"), text(product, "productName"),
        text(product, "productCode"), text(product, "investorName"), text(product, "investorCode"),
        booleanValue(product.get("isPricingEnabled")), productFields(product), calculatedFields(product),
        productExecutionStatus(product), productVersion(product, defaultVersionNumber), metadata);
  }

  private List<CreditApplicationField> productFields(Map<String, Object> product) {
    List<CreditApplicationField> fields = new ArrayList<>(fieldsFrom(product.get("productFields")));
    addMetadataField(fields, "field@product-rules", "rule-catalog", product.get("rules"));
    addMetadataField(fields, "field@product-stipulations", "stipulation-catalog", product.get("stipulations"));
    return List.copyOf(fields);
  }

  private List<CreditApplicationField> calculatedFields(Map<String, Object> product) {
    List<CreditApplicationField> fields = new ArrayList<>(fieldsFrom(product.get("calculatedFields")));
    addMetadataField(fields, "field@rate-options", "rate-catalog", product.get("rateOptions"));
    addMetadataField(fields, "field@lock-periods", "lock-term-catalog", product.get("lockPeriods"));
    return List.copyOf(fields);
  }

  private List<CreditApplicationField> fieldsFrom(Object value) {
    if (!(value instanceof List<?> values)) return List.of();
    List<CreditApplicationField> fields = new ArrayList<>();
    for (Object item : values) {
      if (item instanceof Map<?, ?> raw && !blankObject(raw.get("fieldId"))) {
        Object rawValue = raw.get("value");
        CreditApplicationValue fieldValue = rawValue instanceof Map<?, ?> rawValueMap
            ? new CreditApplicationValue(text(rawValueMap, "type"), rawValueMap.get("value"), text(rawValueMap, "enumTypeId"), text(rawValueMap, "variantId"))
            : new CreditApplicationValue("metadata", rawValue, null, null);
        fields.add(new CreditApplicationField(raw.get("fieldId").toString(), fieldValue));
      }
    }
    return List.copyOf(fields);
  }

  private void addMetadataField(List<CreditApplicationField> fields, String fieldId, String metadataType, Object value) {
    if (!emptyValue(value)) {
      fields.add(new CreditApplicationField(fieldId, new CreditApplicationValue("metadata-list", value, metadataType, null)));
    }
  }

  private Map<String, Object> productExecutionStatus(Map<String, Object> product) {
    if (product.get("status") instanceof Map<?, ?> rawStatus) {
      Map<String, Object> status = new LinkedHashMap<>();
      rawStatus.forEach((key, value) -> {
        if (key != null) status.put(key.toString(), value);
      });
      return Map.copyOf(status);
    }
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("type", Boolean.TRUE.equals(booleanValue(product.get("isPricingEnabled"))) ? "available" : "review_required");
    putIfPresent(status, "metadataRef", text(product, "metadataRef"));
    return Map.copyOf(status);
  }

  private String productVersion(Map<String, Object> product, String defaultVersionNumber) {
    String productVersion = text(product, "versionNumber");
    return blank(productVersion) ? defaultVersionNumber : productVersion;
  }

  private String versionNumber(Map<String, Object> publishedVersionRequest) {
    String version = text(publishedVersionRequest, "versionNumber");
    return blank(version) ? text(publishedVersionRequest, "catalogMetadataRef") : version;
  }

  private Boolean booleanValue(Object value) {
    if (value instanceof Boolean bool) return bool;
    if (value instanceof String text) return Boolean.parseBoolean(text);
    return Boolean.FALSE;
  }

  private boolean emptyValue(Object value) {
    if (value == null) return true;
    if (value instanceof String text) return text.isBlank();
    if (value instanceof Collection<?> collection) return collection.isEmpty();
    if (value instanceof Map<?, ?> map) return map.isEmpty();
    return false;
  }

  private boolean blankObject(Object value) {
    return value == null || value.toString().isBlank();
  }

  private String text(Map<?, ?> map, String key) {
    if (map == null || map.get(key) == null) return "";
    return map.get(key).toString().trim();
  }

  private void validateExecuteSummaryRequest(LoanPassExecuteSummaryRequest request, String headerTenantId) {
    if (request == null) {
      throw new LosValidationException("LOANPASS_EXECUTE_SUMMARY_REQUEST_REQUIRED", "execute-summary request body is required");
    }
    validateExecuteCommon(tenantId(request.tenantId(), headerTenantId), request.currentTime(), request.creditApplicationFields(), "execute-summary");
  }

  private void validateExecuteProductRequest(LoanPassExecuteProductRequest request, String headerTenantId) {
    if (request == null) {
      throw new LosValidationException("LOANPASS_EXECUTE_PRODUCT_REQUEST_REQUIRED", "execute-product request body is required");
    }
    if (blank(request.productId())) {
      throw new LosValidationException("LOANPASS_PRODUCT_ID_REQUIRED", "productId is required before execute-product can run");
    }
    validateExecuteCommon(tenantId(request.tenantId(), headerTenantId), request.currentTime(), request.creditApplicationFields(), "execute-product");
  }

  private void validateExecuteCommon(String tenantId, Instant currentTime, List<CreditApplicationField> creditApplicationFields,
      String operation) {
    if (blank(tenantId)) {
      throw new LosValidationException("MISSING_REQUIRED_TENANT_MAPPING",
          "tenantId is required at $.tenantId or X-Tenant-ID before " + operation + " can run");
    }
    if (currentTime == null) {
      throw new LosValidationException("LOANPASS_CURRENT_TIME_REQUIRED", "currentTime is required before " + operation + " can run");
    }
    if (creditApplicationFields == null || creditApplicationFields.isEmpty()) {
      throw new LosValidationException("LOANPASS_ENGINE_FIELDS_REQUIRED", "creditApplicationFields are required before " + operation + " can run");
    }
  }

  private String tenantId(String requestTenantId, String headerTenantId) {
    return blank(requestTenantId) ? headerTenantId : requestTenantId;
  }

  List<com.wcpe.pricingbff.los.LosApiModels.WebhookDeliveryReceipt> completePricingRequest(
      String pricingRequestId,
      String quoteJobId,
      List<String> productResultRefs,
      List<String> validationMessages,
      String correlationId) {
    throw new LosValidationException("PRICING_REQUEST_PERSISTENCE_REQUIRED",
        "Pricing request completion requires a durable pricing request read model; process-local callback state is disabled");
  }

  LosPricingResponse getPricingRequest(String id) {
    throw new LosValidationException("PRICING_REQUEST_READ_MODEL_REQUIRED",
        "Pricing request status requires a durable downstream read model; process-local pricing request state is disabled");
  }

  List<LosOffer> getOffers(String pricingRequestId) {
    throw new LosValidationException("PRICING_OFFERS_READ_MODEL_REQUIRED",
        "Pricing offers require a durable downstream offer read model; process-local offer state is disabled");
  }

  LosProductCatalogResponse getProductCatalog(String tenantId, String channel, String investor,
      String productFamily, Boolean active, String effectiveDate, Integer page, Integer pageSize) {
    LoanPassTenantFlags flags = flagsForOptionalTenant(tenantId);
    int normalizedPage = page == null ? 0 : page;
    int normalizedPageSize = pageSize == null ? 50 : pageSize;
    if (normalizedPage < 0 || normalizedPageSize < 1 || normalizedPageSize > 100) {
      throw new LosValidationException("PRODUCT_CATALOG_PAGINATION_INVALID", "page must be >= 0 and pageSize must be between 1 and 100");
    }
    Map<String, Object> requestFilters = new LinkedHashMap<>();
    putIfPresent(requestFilters, "tenantId", tenantId);
    putIfPresent(requestFilters, "channel", channel);
    putIfPresent(requestFilters, "investor", investor);
    putIfPresent(requestFilters, "productFamily", productFamily);
    if (active != null) requestFilters.put("active", active);
    putIfPresent(requestFilters, "effectiveDate", effectiveDate);
    requestFilters.put("page", normalizedPage);
    requestFilters.put("pageSize", normalizedPageSize);

    String blockedReason = blank(tenantId) ? "TENANT_CONTEXT_REQUIRED" : "CATALOG_METADATA_NOT_CONFIGURED";
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("source", "fail-closed");
    addFeatureFlagMetadata(metadata, flags);
    metadata.put("requestedFilters", Map.copyOf(requestFilters));
    metadata.put("mappingMetadataStatus", "UNAVAILABLE");
    metadata.put("authorizationMetadataStatus", blank(tenantId) ? "MISSING_TENANT_CONTEXT" : "UNAVAILABLE");
    addLoanPassPublicApiMetadata(metadata);
    return new LosProductCatalogResponse(List.of(), 0, normalizedPage, normalizedPageSize, 0,
        "BLOCKED", blockedReason, metadata);
  }

  LosProductCatalogResponse searchProductCatalog(String tenantId, Map<String, String> queryParameters) {
    LoanPassTenantFlags flags = flagsForOptionalTenant(tenantId);
    Map<String, String> rawFilters = queryParameters == null ? Map.of() : queryParameters;
    List<String> unsupported = rawFilters.keySet().stream()
        .filter(key -> !SUPPORTED_PRODUCT_SEARCH_FILTERS.contains(key))
        .sorted()
        .toList();
    if (!unsupported.isEmpty()) {
      throw new LosValidationException("PRODUCT_SEARCH_FILTER_UNSUPPORTED",
          "Unsupported product search filter(s): " + String.join(", ", unsupported));
    }

    int normalizedPage = parseIntParam(rawFilters, "page", 0);
    int normalizedPageSize = parseIntParam(rawFilters, "pageSize", 50);
    if (normalizedPage < 0 || normalizedPageSize < 1 || normalizedPageSize > 100) {
      throw new LosValidationException("PRODUCT_CATALOG_PAGINATION_INVALID", "page must be >= 0 and pageSize must be between 1 and 100");
    }

    Map<String, Object> appliedFilters = new LinkedHashMap<>();
    putIfPresent(appliedFilters, "tenantId", tenantId);
    putStringFilter(rawFilters, appliedFilters, "productFamily");
    putStringFilter(rawFilters, appliedFilters, "channel");
    putStringFilter(rawFilters, appliedFilters, "investor");
    putStringFilter(rawFilters, appliedFilters, "loanPurpose");
    putStringFilter(rawFilters, appliedFilters, "occupancy");
    putStringFilter(rawFilters, appliedFilters, "propertyType");
    putStringFilter(rawFilters, appliedFilters, "amortization");
    putStringFilter(rawFilters, appliedFilters, "sort");
    putSearchText(rawFilters, appliedFilters);
    if (!blank(rawFilters.get("term"))) appliedFilters.put("term", parsePositiveIntParam(rawFilters, "term"));
    if (!blank(rawFilters.get("active"))) appliedFilters.put("active", parseBooleanParam(rawFilters.get("active"), "active"));
    if (!blank(rawFilters.get("effectiveDate"))) appliedFilters.put("effectiveDate", parseEffectiveDate(rawFilters.get("effectiveDate")).toString());
    appliedFilters.put("page", normalizedPage);
    appliedFilters.put("pageSize", normalizedPageSize);

    String blockedReason = blank(tenantId) ? "TENANT_CONTEXT_REQUIRED" : "CATALOG_METADATA_NOT_CONFIGURED";
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("source", "fail-closed");
    addFeatureFlagMetadata(metadata, flags);
    metadata.put("endpoint", "/api/v1/los/products/search");
    metadata.put("requestedFilters", Map.copyOf(rawFilters));
    metadata.put("appliedFilters", Map.copyOf(appliedFilters));
    metadata.put("warnings", List.of(blockedReason));
    metadata.put("mappingMetadataStatus", "UNAVAILABLE");
    metadata.put("authorizationMetadataStatus", blank(tenantId) ? "MISSING_TENANT_CONTEXT" : "UNAVAILABLE");
    metadata.put("searchScope", "catalog-metadata-only");
    metadata.put("supportedFilters", SUPPORTED_PRODUCT_SEARCH_FILTERS.stream().sorted().toList());
    addLoanPassPublicApiMetadata(metadata);
    return new LosProductCatalogResponse(List.of(), 0, normalizedPage, normalizedPageSize, 0,
        "BLOCKED", blockedReason, metadata);
  }

  LosProductDetailResponse getProductDetail(String productId, String tenantId) {
    if (blank(productId) || blank(tenantId)) {
      throw new LosValidationException("PRODUCT_DETAIL_NOT_FOUND",
          "Product detail was not found for the tenant context");
    }
    LoanPassTenantFlags flags = flagsForTenant(tenantId);
    requireLoanPassCompatibility(flags);

    Map<String, Object> supportedValues = new LinkedHashMap<>();
    supportedValues.put("loanPurposes", List.of());
    supportedValues.put("propertyTypes", List.of());
    supportedValues.put("occupancyTypes", List.of());
    supportedValues.put("terms", List.of());
    supportedValues.put("amortizationTypes", List.of());
    supportedValues.put("investors", List.of());
    supportedValues.put("channels", List.of());

    Map<String, Object> quoteCompatibility = new LinkedHashMap<>();
    quoteCompatibility.put("status", "BLOCKED");
    quoteCompatibility.put("reason", "CATALOG_METADATA_NOT_CONFIGURED");
    quoteCompatibility.put("pricingRequestEndpoint", "/api/v1/los/pricing-requests");
    quoteCompatibility.put("eligibilityEndpoint", "/api/v1/los/product-eligibility");

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("source", "fail-closed");
    addFeatureFlagMetadata(metadata, flags);
    metadata.put("endpoint", "/api/v1/los/products/{productId}");
    metadata.put("requestedProductId", productId.trim());
    metadata.put("fieldSupportStatus", "UNAVAILABLE");
    metadata.put("authorizationMetadataStatus", "UNAVAILABLE");
    metadata.put("mappingMetadataStatus", "INCOMPLETE");
    metadata.put("warnings", List.of("CATALOG_METADATA_NOT_CONFIGURED", "MAPPING_METADATA_INCOMPLETE"));
    addLoanPassPublicApiMetadata(metadata);

    return new LosProductDetailResponse(productId.trim(), null, "BLOCKED", "CATALOG_METADATA_NOT_CONFIGURED",
        List.of(), List.of(), supportedValues, "INCOMPLETE", quoteCompatibility, metadata);
  }

  LosProductEligibilityResponse evaluateProductEligibility(LosProductEligibilityRequest request,
      String headerTenantId, String headerCorrelationId) {
    if (request == null) {
      throw new LosValidationException("PRODUCT_ELIGIBILITY_REQUEST_REQUIRED", "Product eligibility request body is required");
    }
    String tenantId = blank(request.tenantId()) ? headerTenantId : request.tenantId();
    if (blank(tenantId)) {
      throw new LosValidationException("MISSING_REQUIRED_TENANT_MAPPING",
          "tenantId is required at $.tenantId or X-Tenant-ID before product eligibility can be evaluated");
    }
    LoanPassTenantFlags flags = flagsForTenant(tenantId);
    requireLoanPassCompatibility(flags);
    String correlationId = blank(request.correlationId()) ? headerCorrelationId : request.correlationId();
    List<String> requestedProductIds = request.productIds().stream()
        .filter(productId -> !blank(productId))
        .map(String::trim)
        .distinct()
        .toList();

    Map<String, Object> requestedContext = new LinkedHashMap<>();
    requestedContext.put("tenantId", tenantId.trim());
    putIfPresent(requestedContext, "clientId", request.clientId());
    putIfPresent(requestedContext, "productFamily", request.productFamily());
    putIfPresent(requestedContext, "channel", request.channel());
    putIfPresent(requestedContext, "investor", request.investor());
    requestedContext.put("productIds", requestedProductIds);

    List<LosProductEligibilityFieldMessage> requestFieldMessages = eligibilityRequestFieldMessages(request);
    List<String> responseReasons = requestFieldMessages.isEmpty()
        ? List.of("TENANT_PRODUCT_AUTHORIZATION_UNAVAILABLE", "ELIGIBILITY_CONFIGURATION_UNAVAILABLE")
        : eligibilityReasonCodes(requestFieldMessages);
    List<LosProductEligibilityRuleRef> refs = productEligibilityRefs();
    List<LosProductEligibilityResult> results = requestedProductIds.stream()
        .map(productId -> productEligibilityResult(productId, requestFieldMessages, refs))
        .toList();

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("source", "fail-closed");
    addFeatureFlagMetadata(metadata, flags);
    metadata.put("endpoint", "/api/v1/los/product-eligibility");
    metadata.put("requestedContext", Map.copyOf(requestedContext));
    metadata.put("authorizationMetadataStatus", "UNAVAILABLE");
    metadata.put("eligibilityConfigStatus", "UNAVAILABLE");
    metadata.put("catalogDependency", "catalog-service:tenant-product-authorization-required");
    metadata.put("eligibilityDependency", "eligibility-service:rule-config-required");
    metadata.put("warnings", responseReasons);
    metadata.put("fieldMessageCount", requestFieldMessages.size());
    addLoanPassPublicApiMetadata(metadata);
    String status = results.isEmpty() ? "BLOCKED" : results.stream().anyMatch(result -> "requires_more_information".equals(result.eligibility()))
        ? "INCOMPLETE" : "BLOCKED";
    return new LosProductEligibilityResponse(status, correlationId, results, responseReasons, metadata);
  }

  private void addLoanPassPublicApiMetadata(Map<String, Object> metadata) {
    metadata.put("loanPassPublicApiDocsUrl", LOANPASS_PUBLIC_API_DOCS_URL);
    metadata.put("loanPassPublicApiSchemaUrl", LOANPASS_PUBLIC_API_SCHEMA_URL);
    metadata.put("loanPassPublicApiOperationConcepts", LOANPASS_PUBLIC_API_OPERATION_CONCEPTS);
    metadata.put("loanPassContractFieldPolicy", LOANPASS_CONTRACT_FIELD_POLICY);
    metadata.put("loanPassContractEvidenceStatus", "public-docs-accessible-schema-url-discovered-field-shapes-not-adopted");
  }

  LosLockResponse requestLock(LosLockRequest request, String idempotencyKey, String correlationId) {
    validateLockRequest(request);
    String requestHash = hash(request);
    Optional<Object> cached = idempotencyStore.cached("POST", "/api/v1/los/locks", idempotencyKey, requestHash);
    if (cached.isPresent() && cached.get() instanceof LosLockResponse response) {
      return response;
    }
    throw new LosValidationException("LOCK_REQUEST_READ_MODEL_REQUIRED",
        "LOS lock requests require durable tenant, pricing request, and offer read models; process-local lock source-of-truth state is disabled");
  }

  LosLockResponse getLock(String id) {
    throw new LosValidationException("LOCK_READ_MODEL_REQUIRED",
        "LOS lock status requires a durable downstream lock read model; process-local lock state is disabled");
  }

  LosLockResponse extendLock(String id, LosLockExtendRequest request, String idempotencyKey, String correlationId) {
    if (request == null || request.extendByDays() == null || request.extendByDays() <= 0) {
      throw new LosValidationException("LOCK_EXTENSION_INVALID", "extendByDays must be greater than zero");
    }
    throw new LosValidationException("LOCK_READ_MODEL_REQUIRED",
        "LOS lock extension requires a durable downstream lock read model; process-local lock state is disabled");
  }

  LosWebhookRegistrationResponse registerWebhook(LosWebhookRegistrationRequest request, String fallbackTenantId) {
    String tenantId = request == null || blank(request.tenantId()) ? fallbackTenantId : request.tenantId();
    requireLoanPassCompatibility(flagsForTenant(tenantId));
    return webhookRegistry.register(request, fallbackTenantId);
  }

  private void validatePricingRequestShape(LosPricingRequest request) {
    if (request == null) {
      throw new LosValidationException("PRICING_REQUEST_REQUIRED", "Pricing request body is required");
    }
    if (blank(request.requestId()) || blank(request.tenantId())) {
      throw new LosValidationException("PRICING_REQUEST_INVALID", "requestId and tenantId are required");
    }
    if (request.quoteBorrowerInfo() == null || blank(request.quoteBorrowerInfo().borrowerLastName()) || blank(request.quoteBorrowerInfo().loanNumber())) {
      throw new LosValidationException("LOANPASS_IDENTITY_INVALID", "quoteBorrowerInfo.borrowerLastName and loanNumber are required");
    }
    if (request.requestedLoanAmount() == null || blank(request.loanTermType())) {
      throw new LosValidationException("LOANPASS_LOAN_INVALID", "requestedLoanAmount and loanTermType are required");
    }
    if (request.quoteAddressDTO() == null || blank(request.quoteAddressDTO().state()) || blank(request.quoteAddressDTO().zip())) {
      throw new LosValidationException("LOANPASS_ADDRESS_INVALID", "quoteAddressDTO.state and quoteAddressDTO.zip are required");
    }
  }

  private void validatePricingRequest(LosPricingRequest request, LoanPassTenantFlags flags) {
    if (!blank(request.productId()) || !blank(request.selectedProgramId()) || !blank(request.priceGroupId())) {
      throw new LosValidationException("LOANPASS_PRODUCT_AUTHORIZATION_UNAVAILABLE",
          "Tenant product, selectedProgramId, and priceGroup authorization metadata is not configured; pricing was not started");
    }
    validateConfiguredEnum("transactionType", request.transactionType(), flags);
    validateConfiguredEnum("propertyInformationType", request.propertyInformationType(), flags);
    validateConfiguredEnum("occupancyType", request.occupancyType(), flags);
    validateConfiguredEnum("incomeDocumentationType", request.incomeDocumentationType(), flags);
    validateConfiguredEnum("mortgageType", request.mortgageType(), flags);
    validateConfiguredEnum("amortizationType", request.amortizationType(), flags);
    validateConfiguredEnum("loanTermType", request.loanTermType(), flags);
    validateConfiguredEnum("lockPeriodType", request.lockPeriodType(), flags);
    validateCreditApplicationEnumMappings(request.creditApplicationFields(), flags);
    if (request.desiredRateLockPeriod() == null) {
      throw new LosValidationException("LOANPASS_PRICING_INVALID", "desiredRateLockPeriod is required");
    }
    if (loanPassEngineFields(request).isEmpty()) {
      throw new LosValidationException("LOANPASS_ENGINE_FIELDS_REQUIRED", "creditApplicationFields are required");
    }
  }

  private List<CreditApplicationField> loanPassEngineFields(LosPricingRequest request) {
    Map<String, CreditApplicationField> fieldsById = new LinkedHashMap<>();
    for (CreditApplicationField field : request.creditApplicationFields()) {
      if (field != null && !blank(field.fieldId())) {
        fieldsById.put(field.fieldId(), field);
      }
    }
    putNumber(fieldsById, "field@base-loan-amount", request.requestedLoanAmount());
    putEnum(fieldsById, "field@loan-purpose", "loan-purpose", request.transactionType());
    putEnum(fieldsById, "field@property-type", "property-type", request.propertyInformationType());
    putNumber(fieldsById, "field@decision-credit-score", request.creditScore());
    putEnum(fieldsById, "field@occupancy-type", "occupancy-type", request.occupancyType());
    putString(fieldsById, "field@state", request.quoteAddressDTO() == null ? null : request.quoteAddressDTO().state());
    putNumber(fieldsById, "field@number-of-units", request.numberOfUnits());
    putEnum(fieldsById, "field@documentation-type", "documentation-type", request.incomeDocumentationType());
    putNumber(fieldsById, "field@total-monthly-income", request.totalMonthlyIncome());
    putNumber(fieldsById, "field@months-of-reserves", request.monthsOfReserves());
    putEnum(fieldsById, "field@desired-mortgage-type", "mortgage-type", request.mortgageType());
    putString(fieldsById, "field@desired-loan-term", request.loanTermType());
    putNumber(fieldsById, "field@estimated-dti", request.debtToIncomeRatio());
    putEnum(fieldsById, "field@desired-amortization-type", "amortization-type", request.amortizationType());
    putNumber(fieldsById, "field@appraised-value", request.propertyValue());
    putNumber(fieldsById, "field@purchase-price", request.purchasePrice());
    return List.copyOf(fieldsById.values());
  }

  private void putNumber(Map<String, CreditApplicationField> fieldsById, String fieldId, Object value) {
    if (value != null) {
      fieldsById.putIfAbsent(fieldId, new CreditApplicationField(fieldId, new CreditApplicationValue("number", value, null, null)));
    }
  }

  private void putString(Map<String, CreditApplicationField> fieldsById, String fieldId, String value) {
    if (!blank(value)) {
      fieldsById.putIfAbsent(fieldId, new CreditApplicationField(fieldId, new CreditApplicationValue("string", value, null, null)));
    }
  }

  private void putEnum(Map<String, CreditApplicationField> fieldsById, String fieldId, String enumTypeId, String value) {
    if (!blank(value)) {
      fieldsById.putIfAbsent(fieldId, new CreditApplicationField(fieldId,
          new CreditApplicationValue("enum", value, enumTypeId, value)));
    }
  }

  private void putIfPresent(Map<String, Object> values, String key, String value) {
    if (!blank(value)) {
      values.put(key, value.trim());
    }
  }

  private void putStringFilter(Map<String, String> rawFilters, Map<String, Object> appliedFilters, String key) {
    putIfPresent(appliedFilters, key, rawFilters.get(key));
  }

  private void putSearchText(Map<String, String> rawFilters, Map<String, Object> appliedFilters) {
    String q = blank(rawFilters.get("q")) ? rawFilters.get("query") : rawFilters.get("q");
    if (!blank(q)) {
      appliedFilters.put("query", q.trim());
    }
  }

  private int parseIntParam(Map<String, String> rawFilters, String key, int defaultValue) {
    if (blank(rawFilters.get(key))) return defaultValue;
    try {
      return Integer.parseInt(rawFilters.get(key).trim());
    } catch (NumberFormatException ex) {
      throw new LosValidationException("PRODUCT_CATALOG_PAGINATION_INVALID", key + " must be an integer");
    }
  }

  private int parsePositiveIntParam(Map<String, String> rawFilters, String key) {
    try {
      int value = Integer.parseInt(rawFilters.get(key).trim());
      if (value < 1) throw new NumberFormatException("non-positive");
      return value;
    } catch (NumberFormatException ex) {
      throw new LosValidationException("PRODUCT_SEARCH_FILTER_INVALID", key + " must be a positive integer");
    }
  }

  private Boolean parseBooleanParam(String value, String key) {
    String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
    if ("true".equals(normalized)) return Boolean.TRUE;
    if ("false".equals(normalized)) return Boolean.FALSE;
    throw new LosValidationException("PRODUCT_SEARCH_FILTER_INVALID", key + " must be true or false");
  }

  private LocalDate parseEffectiveDate(String value) {
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException ex) {
      throw new LosValidationException("PRODUCT_SEARCH_FILTER_INVALID", "effectiveDate must be an ISO-8601 date");
    }
  }

  private void validateConfiguredEnum(String fieldName, String value, LoanPassTenantFlags flags) {
    if (flags.strictMappingEnabled() && !blank(value) && value.trim().matches("\\d+")) {
      throw new LosValidationException("LOANPASS_ENUM_MAPPING_REQUIRED",
          fieldName + " uses a numeric LoanPass code without configured mapping metadata");
    }
  }

  private void validateCreditApplicationEnumMappings(List<CreditApplicationField> fields, LoanPassTenantFlags flags) {
    if (!flags.strictMappingEnabled()) {
      return;
    }
    for (CreditApplicationField field : fields == null ? List.<CreditApplicationField>of() : fields) {
      if (field == null || field.value() == null || !"enum".equals(field.value().type())) {
        continue;
      }
      Object value = field.value().value();
      if (looksNumeric(value) || looksNumeric(field.value().variantId())) {
        throw new LosValidationException("LOANPASS_ENUM_MAPPING_REQUIRED",
            field.fieldId() + " uses a numeric LoanPass enum code without configured mapping metadata");
      }
    }
  }

  private List<LosProductEligibilityFieldMessage> eligibilityRequestFieldMessages(LosProductEligibilityRequest request) {
    List<LosProductEligibilityFieldMessage> messages = new ArrayList<>();
    if (request.creditApplicationFields().isEmpty() && request.loanFields().isEmpty()) {
      messages.add(new LosProductEligibilityFieldMessage("$.creditApplicationFields", null,
          "LOANPASS_MAPPED_FIELDS_REQUIRED",
          "Mapped LoanPass eligibility fields are required before configured rules can evaluate"));
    }
    for (int i = 0; i < request.creditApplicationFields().size(); i++) {
      CreditApplicationField field = request.creditApplicationFields().get(i);
      if (field == null || blank(field.fieldId())) {
        messages.add(new LosProductEligibilityFieldMessage("$.creditApplicationFields[" + i + "].fieldId", null,
            "LOANPASS_FIELD_ID_REQUIRED", "LoanPass field ID is required for eligibility mapping"));
      } else if (field.value() == null || field.value().value() == null) {
        messages.add(new LosProductEligibilityFieldMessage("$.creditApplicationFields[" + i + "].value", field.fieldId(),
            "LOANPASS_FIELD_VALUE_REQUIRED", "LoanPass field value is required for eligibility mapping"));
      } else if ("enum".equals(field.value().type())
          && (looksNumeric(field.value().value()) || looksNumeric(field.value().variantId()))) {
        messages.add(new LosProductEligibilityFieldMessage("$.creditApplicationFields[" + i + "].value", field.fieldId(),
            "LOANPASS_ENUM_MAPPING_REQUIRED",
            field.fieldId() + " uses a numeric LoanPass enum code without configured mapping metadata"));
      }
    }
    return List.copyOf(messages);
  }

  private List<String> eligibilityReasonCodes(List<LosProductEligibilityFieldMessage> fieldMessages) {
    List<String> reasonCodes = new ArrayList<>();
    for (LosProductEligibilityFieldMessage message : fieldMessages) {
      if (!reasonCodes.contains(message.reasonCode())) {
        reasonCodes.add(message.reasonCode());
      }
    }
    if (!reasonCodes.contains("ELIGIBILITY_CONFIGURATION_UNAVAILABLE")) {
      reasonCodes.add("ELIGIBILITY_CONFIGURATION_UNAVAILABLE");
    }
    return List.copyOf(reasonCodes);
  }

  private List<LosProductEligibilityRuleRef> productEligibilityRefs() {
    return List.of(
        new LosProductEligibilityRuleRef("catalog-service", "tenant-product-authorization", "UNAVAILABLE"),
        new LosProductEligibilityRuleRef("eligibility-service", "product-eligibility-rule-config", "UNAVAILABLE"));
  }

  private LosProductEligibilityResult productEligibilityResult(String productId,
      List<LosProductEligibilityFieldMessage> fieldMessages, List<LosProductEligibilityRuleRef> refs) {
    if (!fieldMessages.isEmpty()) {
      return new LosProductEligibilityResult(productId, "requires_more_information",
          eligibilityReasonCodes(fieldMessages),
          fieldMessages, refs, "catalog-service:product-summary-ref-required");
    }
    return new LosProductEligibilityResult(productId, "ineligible",
        List.of("TENANT_PRODUCT_AUTHORIZATION_UNAVAILABLE", "ELIGIBILITY_CONFIGURATION_UNAVAILABLE"),
        List.of(), refs, "catalog-service:product-summary-ref-required");
  }

  private boolean looksNumeric(Object value) {
    return value != null && value.toString().trim().matches("\\d+");
  }

  private LoanPassTenantFlags flagsForOptionalTenant(String tenantId) {
    if (blank(tenantId)) {
      return null;
    }
    LoanPassTenantFlags flags = flagsForTenant(tenantId);
    requireLoanPassCompatibility(flags);
    return flags;
  }

  private LoanPassTenantFlags flagsForTenant(String tenantId) {
    return featureFlagService.lookup(tenantId);
  }

  private void requireLoanPassCompatibility(LoanPassTenantFlags flags) {
    if (!flags.loanPassCompatibilityEnabled()) {
      throw new LosValidationException("LOS_COMPATIBILITY_DISABLED",
          "LoanPass compatibility is disabled for tenant " + flags.tenantId() + " by " + flags.configRef()
              + " auditRef=" + flags.auditRef() + " version=" + flags.version());
    }
  }

  private void addFeatureFlagMetadata(Map<String, Object> metadata, LoanPassTenantFlags flags) {
    if (flags != null) {
      metadata.put("tenantFeatureFlagConfigRef", flags.configRef());
      metadata.put("tenantFeatureFlagAuditRef", flags.auditRef());
      metadata.put("tenantFeatureFlagVersion", flags.version());
    }
  }

  private void validateLockRequest(LosLockRequest request) {
    if (request == null || blank(request.pricingRequestId()) || blank(request.offerId()) || request.lockPeriodDays() == null || blank(request.requestedBy())) {
      throw new LosValidationException("LOCK_REQUEST_INVALID", "pricingRequestId, offerId, lockPeriodDays, and requestedBy are required");
    }
  }

  private String hash(Object value) {
    try {
      String json = objectMapper.writeValueAsString(value);
      return UUID.nameUUIDFromBytes(json.getBytes(StandardCharsets.UTF_8)).toString();
    } catch (JsonProcessingException ex) {
      throw new LosValidationException("REQUEST_HASH_FAILED", "Unable to hash LOS request");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

}
