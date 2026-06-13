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
    QuoteServiceResponse quoteJob = quoteClient.submitQuoteJob(new QuoteServiceRequest(
        scenario.tenantId(), scenario.scenarioId(), scenario.scenarioVersion(), scenario.requestedLockPeriods(),
        Map.of("source", "LOS", "losRequestId", request.requestId()), "los:" + request.requestId(), idempotencyKey,
        correlationId, scenario.effectiveDate(), true));

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
    if (request.loan() == null || request.loan().loanAmount() == null || request.loan().termMonths() == null) {
      throw new LosValidationException("LOAN_INVALID", "loanAmount and termMonths are required");
    }
    if (request.pricing() == null || request.pricing().lockPeriodDays() == null || request.pricing().effectiveDate() == null) {
      throw new LosValidationException("PRICING_INVALID", "lockPeriodDays and effectiveDate are required");
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
