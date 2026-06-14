package com.wcpe.pricingbff.crm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricingbff.crm.CrmApiModels.CrmDashboardResponse;
import com.wcpe.pricingbff.crm.CrmApiModels.CrmPricingResponse;
import com.wcpe.pricingbff.crm.CrmApiModels.CrmWebhookRegistrationRequest;
import com.wcpe.pricingbff.crm.CrmApiModels.CrmWebhookRegistrationResponse;
import com.wcpe.pricingbff.crm.CrmApiModels.EligibilityBlocker;
import com.wcpe.pricingbff.crm.CrmApiModels.MissingFact;
import com.wcpe.pricingbff.crm.CrmApiModels.PipelinePromotionResponse;
import com.wcpe.pricingbff.crm.CrmApiModels.QuoteServiceRequest;
import com.wcpe.pricingbff.crm.CrmApiModels.QuoteServiceResponse;
import com.wcpe.pricingbff.crm.CrmApiModels.ScenarioSaveResponse;
import com.wcpe.pricingbff.crm.CrmApiModels.ScenarioShareResponse;
import com.wcpe.pricingbff.crm.CrmApiModels.UnsupportedField;
import com.wcpe.pricingbff.crm.CrmApiModels.WebhookDeliveryReceipt;
import com.wcpe.pricingbff.crm.CrmApiModels.WebhookEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
class CrmPricingService {
  private static final List<String> LOAN_AMOUNT_ALIASES = List.of("loanAmount", "loan_amount", "loanAmt", "requestedLoanAmount", "OB_LOAN_AMOUNT", "pollyLoanAmount", "loanpass.loan_amount");
  private static final List<String> CREDIT_SCORE_ALIASES = List.of("creditScore", "fico", "borrowerCreditScore", "OB_FICO", "pollyFico", "loanpass.fico");
  private static final List<String> PROPERTY_STATE_ALIASES = List.of("propertyState", "state", "subjectPropertyState", "OB_STATE", "pollyPropertyState", "loanpass.property_state");
  private static final List<String> LOAN_PURPOSE_ALIASES = List.of("loanPurpose", "purpose", "loan_purpose", "OB_LOAN_PURPOSE", "pollyLoanPurpose", "loanpass.loan_purpose");
  private static final List<String> OCCUPANCY_ALIASES = List.of("occupancy", "occupancyType", "OB_OCCUPANCY", "pollyOccupancy", "loanpass.occupancy");
  private static final List<String> PRODUCT_FAMILY_ALIASES = List.of("productFamily", "productType", "product_family", "loanProduct", "OB_PRODUCT", "pollyProductType", "loanpass.product");
  private static final List<String> LOCK_PERIOD_ALIASES = List.of("lockPeriodDays", "lock_period_days", "OB_LOCK_PERIOD", "pollyLockPeriod", "loanpass.lock_period_days");
  private static final List<String> EFFECTIVE_DATE_ALIASES = List.of("effectiveDate", "pricingDate", "effective_date", "OB_EFFECTIVE_DATE", "pollyEffectiveDate", "loanpass.effective_date");
  private static final Set<String> SUPPORTED_TOP_LEVEL = Set.of("requestId", "externalLeadId", "sourceSystem", "sourceRefs", "leadFacts", "loanOfficerId", "callbackUrl", "crmRecordUrl");

  private final ObjectMapper objectMapper;
  private final CrmQuoteServiceClient quoteClient;
  private final CrmWebhookRegistry webhookRegistry;
  private final Map<String, CrmPricingResponse> pricingRequests = new ConcurrentHashMap<>();
  private final Map<String, ScenarioSaveResponse> scenarios = new ConcurrentHashMap<>();

  CrmPricingService(ObjectMapper objectMapper, CrmQuoteServiceClient quoteClient, CrmWebhookRegistry webhookRegistry) {
    this.objectMapper = objectMapper;
    this.quoteClient = quoteClient;
    this.webhookRegistry = webhookRegistry;
  }

  CrmPricingResponse createPricingRequest(String tenantId, String sourceSystem, Map<String, Object> payload,
      String idempotencyKey, String correlationId) {
    Map<String, Object> body = payload == null ? Map.of() : payload;
    Map<String, Object> facts = flattenFacts(body);
    String externalLeadId = firstString(body, "externalLeadId", "leadId", "crmLeadId").orElse("crm-lead-unspecified");
    String requestId = firstString(body, "requestId", "pricingRequestId")
        .orElseGet(() -> UUID.nameUUIDFromBytes((tenantId + ":" + sourceSystem + ":" + externalLeadId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString());
    String scenarioId = scenarioId(tenantId, requestId);
    String productFamily = firstString(facts, PRODUCT_FAMILY_ALIASES).orElse("NON_QM");
    List<MissingFact> missing = missingFacts(facts);
    List<EligibilityBlocker> blockers = eligibilityBlockers(productFamily);
    List<UnsupportedField> unsupported = unsupportedFields(body, facts);
    QuoteServiceResponse quoteJob = null;
    if (missing.isEmpty() && blockers.isEmpty()) {
      quoteJob = quoteClient.submitQuoteJob(new QuoteServiceRequest(tenantId, scenarioId, 1,
          List.of(optionalInt(facts, LOCK_PERIOD_ALIASES).orElseThrow()), Map.of("source", "CRM", "crmSystem", sourceSystem,
              "externalLeadId", externalLeadId), "crm:" + sourceSystem + ":" + externalLeadId, idempotencyKey,
          correlationId, optionalDate(facts, EFFECTIVE_DATE_ALIASES).orElseThrow(), true));
    }
    Map<String, String> replayRefs = Map.of(
        "crmRequestRef", requestId,
        "scenarioRef", scenarioId,
        "pricingBoundary", "quote-service",
        "correlationRef", correlationId == null ? "" : correlationId);
    Map<String, String> sourceRefs = sourceRefs(body, sourceSystem, externalLeadId);
    String pricingRequestId = UUID.nameUUIDFromBytes((tenantId + ":crm:" + sourceSystem + ":" + requestId).getBytes(StandardCharsets.UTF_8)).toString();
    CrmPricingResponse response = new CrmPricingResponse(requestId, tenantId, sourceSystem, externalLeadId,
        missing.isEmpty() && blockers.isEmpty() ? "QUOTE_JOB_QUEUED" : "NEEDS_FACTS", quoteSummary(productFamily, quoteJob, missing, blockers),
        missing, blockers, unsupported, replayRefs, sourceRefs,
        "/api/v1/tenants/" + tenantId + "/integrations/crm/pricing-requests/" + pricingRequestId,
        quoteJob == null ? null : quoteJob.jobId(), Instant.now(), correlationId);
    pricingRequests.put(key(tenantId, pricingRequestId), response);
    dispatchPricingUpdate(response, "crm.pricing.updated", correlationId);
    return response;
  }

  CrmPricingResponse getPricingRequest(String tenantId, String requestId) {
    return pricingRequests.getOrDefault(key(tenantId, requestId), notFound(tenantId, requestId));
  }

  PipelinePromotionResponse continueRequest(String tenantId, String requestId, String correlationId) {
    CrmPricingResponse response = getExisting(tenantId, requestId);
    if (!response.missingFacts().isEmpty() || !response.eligibilityBlockers().isEmpty()) {
      return new PipelinePromotionResponse(requestId, tenantId, "BLOCKED", null, response.missingFacts(), response.eligibilityBlockers(), correlationId);
    }
    String pipelineRef = "pipeline:intake:" + UUID.nameUUIDFromBytes((tenantId + ":" + requestId).getBytes(StandardCharsets.UTF_8));
    return new PipelinePromotionResponse(requestId, tenantId, "PROMOTED", pipelineRef, List.of(), List.of(), correlationId);
  }

  CrmWebhookRegistrationResponse registerWebhook(String tenantId, String sourceSystem, CrmWebhookRegistrationRequest request) {
    return webhookRegistry.register(request, tenantId, sourceSystem);
  }

  List<WebhookDeliveryReceipt> pushPricingUpdate(String tenantId, String sourceSystem, String requestId, String correlationId) {
    CrmPricingResponse response = getExisting(tenantId, requestId);
    return dispatchPricingUpdate(response, "crm.pricing.updated", correlationId == null ? response.correlationId() : correlationId);
  }

  CrmDashboardResponse dashboard(String tenantId, String sourceSystem) {
    List<CrmPricingResponse> requests = pricingRequests.entrySet().stream()
        .filter(entry -> entry.getKey().startsWith(tenantId + ":"))
        .map(Map.Entry::getValue)
        .filter(response -> sourceSystem == null || sourceSystem.equalsIgnoreCase(response.sourceSystem()))
        .toList();
    long needsFacts = requests.stream().filter(response -> !response.missingFacts().isEmpty()).count();
    long quoteJobs = requests.stream().filter(response -> response.quoteJobId() != null).count();
    Map<String, Object> summary = Map.of("productFamily", "NON_QM", "requestCount", requests.size(),
        "needsFactsCount", needsFacts, "quoteJobCount", quoteJobs, "pricingBoundary", "quote-service");
    return new CrmDashboardResponse(tenantId, sourceSystem, requests, summary, Instant.now());
  }

  ScenarioSaveResponse saveScenario(String tenantId, String requestId) {
    CrmPricingResponse response = getExisting(tenantId, requestId);
    String scenarioId = response.replayRefs().get("scenarioRef");
    ScenarioSaveResponse saved = new ScenarioSaveResponse(scenarioId, requestId, tenantId, "SAVED", response.replayRefs(), Instant.now());
    scenarios.put(key(tenantId, scenarioId), saved);
    return saved;
  }

  ScenarioShareResponse shareScenario(String tenantId, String scenarioId, String correlationId) {
    if (!scenarios.containsKey(key(tenantId, scenarioId))) {
      throw new CrmValidationException("CRM_SCENARIO_NOT_FOUND", "CRM pricing scenario not found");
    }
    String shareRef = "/api/v1/tenants/" + tenantId + "/integrations/crm/scenarios/" + scenarioId + "/shared";
    return new ScenarioShareResponse(scenarioId, shareRef, "SHARE_READY", "tenant-configured-expiry-required", correlationId);
  }

  List<WebhookDeliveryReceipt> deliveries() {
    return webhookRegistry.deliveries();
  }

  private List<WebhookDeliveryReceipt> dispatchPricingUpdate(CrmPricingResponse response, String eventType, String correlationId) {
    return webhookRegistry.dispatch(response.tenantId(), response.sourceSystem(), new WebhookEvent(eventType, Map.of(
        "requestId", response.requestId(),
        "externalLeadId", response.externalLeadId(),
        "status", response.status(),
        "quoteSummary", response.quoteSummary(),
        "missingFacts", response.missingFacts(),
        "eligibilityBlockers", response.eligibilityBlockers(),
        "replayRefs", response.replayRefs()), correlationId, Instant.now()));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> flattenFacts(Map<String, Object> body) {
    Map<String, Object> facts = new LinkedHashMap<>();
    Object leadFacts = body.get("leadFacts");
    if (leadFacts instanceof Map<?, ?> nested) {
      nested.forEach((k, v) -> facts.put(String.valueOf(k), v));
    }
    body.forEach((k, v) -> {
      if (!(v instanceof Map<?, ?>) && !(v instanceof List<?>)) {
        facts.putIfAbsent(k, v);
      }
    });
    Object sourceRefs = body.get("sourceRefs");
    if (sourceRefs instanceof Map<?, ?> refs) {
      refs.forEach((k, v) -> facts.putIfAbsent(String.valueOf(k), v));
    }
    return facts;
  }

  private List<MissingFact> missingFacts(Map<String, Object> facts) {
    List<MissingFact> missing = new ArrayList<>();
    require(facts, LOAN_AMOUNT_ALIASES, "loanAmount", missing);
    require(facts, CREDIT_SCORE_ALIASES, "representativeCreditScore", missing);
    require(facts, PROPERTY_STATE_ALIASES, "propertyState", missing);
    require(facts, LOAN_PURPOSE_ALIASES, "loanPurpose", missing);
    require(facts, OCCUPANCY_ALIASES, "occupancy", missing);
    require(facts, LOCK_PERIOD_ALIASES, "lockPeriodDays", missing);
    require(facts, EFFECTIVE_DATE_ALIASES, "effectiveDate", missing);
    return missing;
  }

  private void require(Map<String, Object> facts, List<String> aliases, String canonical, List<MissingFact> missing) {
    if (firstString(facts, aliases).isEmpty()) {
      missing.add(new MissingFact(canonical, "Required before full CRM quote launch; quick-pricer response still returns replay refs", aliases));
    }
  }

  private List<EligibilityBlocker> eligibilityBlockers(String productFamily) {
    String normalized = productFamily == null ? "" : productFamily.replace("-", "_").toUpperCase();
    if (normalized.contains("NON_QM") || normalized.contains("NONQM")) {
      return List.of();
    }
    return List.of(new EligibilityBlocker("CRM_NON_QM_PRODUCT_REQUIRED", "CRM pricing API only exposes Non-QM pricing for this story", "pricing-bff"));
  }

  private Map<String, Object> quoteSummary(String productFamily, QuoteServiceResponse quoteJob, List<MissingFact> missing, List<EligibilityBlocker> blockers) {
    return Map.of(
        "productFamily", productFamily == null ? "NON_QM" : productFamily,
        "pricingBoundary", "quick-pricer-to-quote-service",
        "quickPricerStatus", missing.isEmpty() && blockers.isEmpty() ? "FULL_QUOTE_SUBMITTED" : "MISSING_FACTS_RETURNED",
        "quoteJobStatus", quoteJob == null ? "NOT_SUBMITTED" : quoteJob.status(),
        "quoteJobId", quoteJob == null ? "" : quoteJob.jobId(),
        "rateValuesIncluded", false,
        "rateValuePolicy", "Rates/prices are returned only from configured pricing engines; no fallback rates are invented by pricing-bff");
  }

  private List<UnsupportedField> unsupportedFields(Map<String, Object> body, Map<String, Object> facts) {
    Set<String> aliases = Set.of("loanAmount", "loan_amount", "loanAmt", "requestedLoanAmount", "OB_LOAN_AMOUNT", "pollyLoanAmount", "loanpass.loan_amount",
        "creditScore", "fico", "borrowerCreditScore", "OB_FICO", "pollyFico", "loanpass.fico", "propertyState", "state", "subjectPropertyState", "OB_STATE", "pollyPropertyState", "loanpass.property_state",
        "loanPurpose", "purpose", "loan_purpose", "OB_LOAN_PURPOSE", "pollyLoanPurpose", "loanpass.loan_purpose", "occupancy", "occupancyType", "OB_OCCUPANCY", "pollyOccupancy", "loanpass.occupancy",
        "productFamily", "productType", "product_family", "loanProduct", "OB_PRODUCT", "pollyProductType", "loanpass.product",
        "effectiveDate", "pricingDate", "effective_date", "OB_EFFECTIVE_DATE", "pollyEffectiveDate", "loanpass.effective_date",
        "lockPeriodDays", "lock_period_days", "OB_LOCK_PERIOD", "pollyLockPeriod", "loanpass.lock_period_days", "leadId", "crmLeadId", "pricingRequestId");
    List<UnsupportedField> unsupported = new ArrayList<>();
    for (String field : body.keySet()) {
      if (!SUPPORTED_TOP_LEVEL.contains(field) && !aliases.contains(field) && !"leadFacts".equals(field) && !"sourceRefs".equals(field)) {
        unsupported.add(new UnsupportedField(field, "Unsupported CRM field ignored; supported pricing facts were still processed"));
      }
    }
    for (String field : facts.keySet()) {
      if (!aliases.contains(field) && !SUPPORTED_TOP_LEVEL.contains(field) && !"requestId".equals(field)) {
        unsupported.add(new UnsupportedField(field, "Unsupported CRM lead fact ignored; supported aliases were still processed"));
      }
    }
    return unsupported.stream().distinct().toList();
  }

  private Map<String, String> sourceRefs(Map<String, Object> body, String sourceSystem, String externalLeadId) {
    Map<String, String> refs = new LinkedHashMap<>();
    refs.put("sourceSystem", sourceSystem);
    refs.put("externalLeadId", externalLeadId);
    Object sourceRefs = body.get("sourceRefs");
    if (sourceRefs instanceof Map<?, ?> rawRefs) {
      rawRefs.forEach((key, value) -> refs.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
    }
    firstString(body, "crmRecordUrl").ifPresent(value -> refs.put("crmRecordUrl", value));
    return refs;
  }

  private Optional<String> firstString(Map<String, Object> values, String... keys) {
    return firstString(values, List.of(keys));
  }

  private Optional<String> firstString(Map<String, Object> values, List<String> keys) {
    for (String key : keys) {
      Object value = values.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return Optional.of(String.valueOf(value));
      }
    }
    return Optional.empty();
  }

  private Optional<Integer> optionalInt(Map<String, Object> values, String key) {
    Object value = values.get(key);
    if (value instanceof Number number) {
      return Optional.of(number.intValue());
    }
    if (value != null && !String.valueOf(value).isBlank()) {
      return Optional.of(new BigDecimal(String.valueOf(value)).intValue());
    }
    return Optional.empty();
  }

  private Optional<Integer> optionalInt(Map<String, Object> values, List<String> keys) {
    for (String key : keys) {
      Optional<Integer> value = optionalInt(values, key);
      if (value.isPresent()) {
        return value;
      }
    }
    return Optional.empty();
  }

  private Optional<LocalDate> optionalDate(Map<String, Object> values, List<String> keys) {
    for (String key : keys) {
      Object value = values.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return Optional.of(LocalDate.parse(String.valueOf(value)));
      }
    }
    return Optional.empty();
  }

  private CrmPricingResponse getExisting(String tenantId, String requestId) {
    CrmPricingResponse response = pricingRequests.get(key(tenantId, requestId));
    if (response == null) {
      throw new CrmValidationException("CRM_PRICING_REQUEST_NOT_FOUND", "CRM pricing request not found");
    }
    return response;
  }

  private CrmPricingResponse notFound(String tenantId, String requestId) {
    return new CrmPricingResponse(requestId, tenantId, null, null, "NOT_FOUND", Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(),
        "/api/v1/tenants/" + tenantId + "/integrations/crm/pricing-requests/" + requestId, null, Instant.now(), null);
  }

  private String scenarioId(String tenantId, String requestId) {
    return UUID.nameUUIDFromBytes((tenantId + ":crm-scenario:" + requestId).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String key(String tenantId, String requestId) {
    return tenantId + ":" + requestId;
  }

  String hash(Object value) {
    try {
      return UUID.nameUUIDFromBytes(objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)).toString();
    } catch (JsonProcessingException ex) {
      throw new CrmValidationException("CRM_REQUEST_HASH_FAILED", "Unable to hash CRM request");
    }
  }
}
