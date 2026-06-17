package com.wcpe.pricingbff.los;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricingbff.los.LosApiModels.LosLockExtendRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosLockRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosLockResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosOffer;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
class LosPricingService {
  private final ObjectMapper objectMapper;
  private final LosScenarioAdapter scenarioAdapter;
  private final LosQuoteServiceClient quoteClient;
  private final LosLockServiceClient lockClient;
  private final LosWebhookRegistry webhookRegistry;
  private final LosIdempotencyStore idempotencyStore;
  private final Map<String, LosPricingResponse> pricingRequests = new ConcurrentHashMap<>();
  private final Map<String, List<LosOffer>> offersByPricingRequest = new ConcurrentHashMap<>();
  private final Map<String, LosLockResponse> locks = new ConcurrentHashMap<>();

  LosPricingService(ObjectMapper objectMapper, LosScenarioAdapter scenarioAdapter, LosQuoteServiceClient quoteClient,
      LosLockServiceClient lockClient, LosWebhookRegistry webhookRegistry, LosIdempotencyStore idempotencyStore) {
    this.objectMapper = objectMapper;
    this.scenarioAdapter = scenarioAdapter;
    this.quoteClient = quoteClient;
    this.lockClient = lockClient;
    this.webhookRegistry = webhookRegistry;
    this.idempotencyStore = idempotencyStore;
  }

  LosPricingResponse createPricingRequest(LosPricingRequest request, String idempotencyKey, String correlationId) {
    validatePricingRequest(request);
    String requestHash = hash(request);
    Optional<Object> cached = idempotencyStore.cached("POST", "/api/v1/los/pricing-requests", idempotencyKey, requestHash);
    if (cached.isPresent() && cached.get() instanceof LosPricingResponse response) {
      return response;
    }
    LosScenario scenario = scenarioAdapter.toScenario(request);
    List<CreditApplicationField> engineFields = loanPassEngineFields(request);
    QuoteServiceResponse quoteJob = quoteClient.submitQuoteJob(new QuoteServiceRequest(
        scenario.tenantId(), scenario.scenarioId(), scenario.scenarioVersion(), scenario.requestedLockPeriods(),
        Map.of("source", "LOS", "losRequestId", request.requestId()), "los:" + request.requestId(), idempotencyKey,
        correlationId, true, request.requestId(), request.quoteBorrowerInfo(), request.quoteAddressDTO(),
        request.requestedLoanAmount(), request.purchasePrice(), request.propertyValue(), request.transactionType(),
        request.propertyInformationType(), request.occupancyType(), request.numberOfUnits(), request.incomeDocumentationType(),
        request.totalMonthlyIncome(), request.totalLiabilityMonthlyPayment(), request.debtToIncomeRatio(), request.monthsOfReserves(),
        request.creditScore(), request.mortgageType(), request.amortizationType(), request.loanTermType(), request.desiredRateLockPeriod(),
        request.lockPeriodType(), request.channelType(), engineFields));

    String pricingRequestId = UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.requestId()).getBytes(StandardCharsets.UTF_8)).toString();
    LosPricingResponse response = new LosPricingResponse(request.requestId(), pricingRequestId, "ACCEPTED", List.of(), List.of(),
        null, "/api/v1/los/pricing-requests/" + pricingRequestId, quoteJob.jobId(), correlationId);
    pricingRequests.put(pricingRequestId, response);
    offersByPricingRequest.put(pricingRequestId, new ArrayList<>());
    idempotencyStore.store("POST", "/api/v1/los/pricing-requests", idempotencyKey, requestHash, response);
    webhookRegistry.dispatch(request.tenantId(), new WebhookEvent("pricing.completed", Map.of(
        "pricingRequestId", pricingRequestId,
        "status", response.status(),
        "offerCount", response.offers().size()), correlationId, Instant.now()));
    return response;
  }

  LosPricingResponse getPricingRequest(String id) {
    return pricingRequests.getOrDefault(id, notFoundPricingResponse(id));
  }

  List<LosOffer> getOffers(String pricingRequestId) {
    return List.copyOf(offersByPricingRequest.getOrDefault(pricingRequestId, List.of()));
  }

  LosLockResponse requestLock(LosLockRequest request, String idempotencyKey, String correlationId) {
    validateLockRequest(request);
    String requestHash = hash(request);
    Optional<Object> cached = idempotencyStore.cached("POST", "/api/v1/los/locks", idempotencyKey, requestHash);
    if (cached.isPresent() && cached.get() instanceof LosLockResponse response) {
      return response;
    }
    LosOffer offer = offersByPricingRequest.getOrDefault(request.pricingRequestId(), List.of()).stream()
        .filter(candidate -> request.offerId().equals(candidate.offerId()))
        .findFirst()
        .orElse(null);
    LosLockResponse response = lockClient.requestLock(request, offer, correlationId);
    locks.put(response.lockId(), response);
    idempotencyStore.store("POST", "/api/v1/los/locks", idempotencyKey, requestHash, response);
    webhookRegistry.dispatch(resolveTenant(request.pricingRequestId()), new WebhookEvent("lock.confirmed", Map.of(
        "lockId", response.lockId(),
        "pricingRequestId", response.pricingRequestId(),
        "expiration", response.lockExpiration().toString()), correlationId, Instant.now()));
    return response;
  }

  LosLockResponse getLock(String id) {
    LosLockResponse lock = locks.get(id);
    if (lock == null) {
      throw new LosValidationException("LOCK_NOT_FOUND", "LOS lock not found");
    }
    return lock;
  }

  LosLockResponse extendLock(String id, LosLockExtendRequest request, String idempotencyKey, String correlationId) {
    if (request == null || request.extendByDays() == null || request.extendByDays() <= 0) {
      throw new LosValidationException("LOCK_EXTENSION_INVALID", "extendByDays must be greater than zero");
    }
    LosLockResponse existing = getLock(id);
    String path = "/api/v1/los/locks/" + id + "/extend";
    String requestHash = hash(request);
    Optional<Object> cached = idempotencyStore.cached("POST", path, idempotencyKey, requestHash);
    if (cached.isPresent() && cached.get() instanceof LosLockResponse response) {
      return response;
    }
    LosLockResponse response = lockClient.extendLock(existing, request.extendByDays(), correlationId);
    locks.put(id, response);
    idempotencyStore.store("POST", path, idempotencyKey, requestHash, response);
    webhookRegistry.dispatch(resolveTenant(existing.pricingRequestId()), new WebhookEvent("lock.extended", Map.of(
        "lockId", response.lockId(), "newExpiration", response.lockExpiration().toString()), correlationId, Instant.now()));
    return response;
  }

  LosWebhookRegistrationResponse registerWebhook(LosWebhookRegistrationRequest request, String fallbackTenantId) {
    return webhookRegistry.register(request, fallbackTenantId);
  }

  private void validatePricingRequest(LosPricingRequest request) {
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

  private LosPricingResponse notFoundPricingResponse(String id) {
    return new LosPricingResponse(null, id, "NOT_FOUND", List.of(), List.of(), null, "/api/v1/los/pricing-requests/" + id, null, null);
  }

  private String resolveTenant(String pricingRequestId) {
    LosPricingResponse response = pricingRequests.get(pricingRequestId);
    if (response == null || response.requestId() == null) {
      return "unknown";
    }
    return response.requestId().contains(":") ? response.requestId().split(":")[0] : "unknown";
  }
}
