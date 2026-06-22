package com.wcpe.pricingbff.los;

import com.wcpe.pricingbff.los.LosApiModels.LockTerms;
import com.wcpe.pricingbff.los.LosApiModels.LosLockExtendRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosLockRequest;
import com.wcpe.pricingbff.los.LosApiModels.LosLockResponse;
import com.wcpe.pricingbff.los.LosApiModels.LosOffer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class LosLockServiceClient {
  private final RestClient restClient;
  private final String baseUrl;

  LosLockServiceClient(RestClient.Builder restClientBuilder,
      @Value("${loanweft.integrations.lock-service.base-url:}") String baseUrl) {
    this.restClient = restClientBuilder.build();
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
  }

  LosLockResponse requestLock(String tenantId, LosLockRequest request, LosOffer offer, String idempotencyKey,
      String correlationId) {
    requireBaseUrl();
    requireIdempotencyKey(idempotencyKey);
    LockServiceResponse response = restClient.post()
        .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantScopedId(tenantId) + "/locks/requests"))
        .contentType(MediaType.APPLICATION_JSON)
        .header("Idempotency-Key", idempotencyKey)
        .header("X-Correlation-Id", correlationId == null ? "" : correlationId)
        .body(LockServiceRequest.from(tenantId, request, offer))
        .retrieve()
        .body(LockServiceResponse.class);
    return response == null ? null : response.toLosResponse(request, offer);
  }

  LosLockResponse extendLock(String tenantId, LosLockResponse existing, LosLockExtendRequest request,
      String idempotencyKey, String correlationId) {
    requireBaseUrl();
    requireIdempotencyKey(idempotencyKey);
    LockExtensionServiceResponse response = restClient.post()
        .uri(URI.create(baseUrl + "/api/v1/tenants/" + tenantScopedId(tenantId) + "/locks/" + existing.lockId() + "/extensions"))
        .contentType(MediaType.APPLICATION_JSON)
        .header("Idempotency-Key", idempotencyKey)
        .header("X-Correlation-Id", correlationId == null ? "" : correlationId)
        .body(LockExtensionServiceRequest.from(existing, request))
        .retrieve()
        .body(LockExtensionServiceResponse.class);
    return response == null ? null : response.toLosResponse(existing);
  }

  private void requireBaseUrl() {
    if (baseUrl.isBlank()) {
      throw new LosValidationException("LOCK_SERVICE_INTEGRATION_REQUIRED",
          "lock-service base URL is not configured; LOS lock request was not started");
    }
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new LosValidationException("LOCK_SERVICE_IDEMPOTENCY_REQUIRED",
          "Idempotency-Key or X-Request-ID is required before forwarding an LOS lock request");
    }
  }

  private UUID tenantScopedId(String tenantId) {
    if (tenantId != null && !tenantId.isBlank()) {
      try {
        return UUID.fromString(tenantId.trim());
      } catch (IllegalArgumentException ignored) {
        return UUID.nameUUIDFromBytes(("los-tenant:" + tenantId.trim()).getBytes(StandardCharsets.UTF_8));
      }
    }
    throw new LosValidationException("LOCK_SERVICE_TENANT_REQUIRED",
        "tenant context is required before forwarding an LOS lock request to lock-service");
  }

  record LockServiceRequest(
      String requestId,
      String actorId,
      String quoteId,
      String loanId,
      String scenarioHash,
      String pricingResultHash,
      String rateSheetVersion,
      String productId,
      String investorId,
      String channel,
      int lockPeriodDays,
      Instant quotePricedAt,
      Instant requestedAt,
      boolean permissionGranted,
      boolean autoApprovalPermitted,
      boolean quoteFresh,
      boolean scenarioHashUnchanged,
      boolean pricingHashUnchanged,
      boolean rateSheetLockable,
      boolean marketSuspended,
      boolean investorSuspended,
      boolean complianceBlocking,
      boolean tenantChannelConfigPresent,
      boolean investorAmbiguous,
      String lockPolicyVersionId,
      String complianceEvidenceRef,
      Map<String, String> sourceRefs) {
    static LockServiceRequest from(String tenantId, LosLockRequest request, LosOffer offer) {
      int days = request.lockPeriodDays() == null ? 0 : request.lockPeriodDays();
      return new LockServiceRequest(
          "los-lock:" + safe(request.pricingRequestId()) + ":" + safe(request.offerId()),
          safe(request.requestedBy()),
          safe(request.offerId()),
          safe(request.pricingRequestId()),
          "los-scenario-unavailable:" + safe(request.pricingRequestId()),
          "los-pricing-result:" + safe(request.pricingRequestId()) + ":" + safe(request.offerId()),
          "los-rate-sheet-unavailable",
          offer == null ? safe(request.offerId()) : safe(offer.productId()),
          offer == null ? "los-investor-unresolved" : safe(offer.investorId()),
          "los-channel-unresolved",
          days,
          Instant.now(),
          Instant.now(),
          true,
          false,
          false,
          false,
          false,
          false,
          false,
          false,
          true,
          false,
          true,
          "los-lock-policy-unresolved",
          "los-compliance-evidence-unresolved",
          Map.of(
              "source", "pricing-bff-los",
              "tenantId", safe(tenantId),
              "pricingRequestId", safe(request.pricingRequestId()),
              "offerId", safe(request.offerId()),
              "failClosedReason", "tenant lock policy evidence is not resolved in pricing-bff"));
    }
  }

  record LockServiceResponse(String lockId, String requestId, String status, String correlationId) {
    LosLockResponse toLosResponse(LosLockRequest request, LosOffer offer) {
      return new LosLockResponse(lockId, request.pricingRequestId(), request.offerId(), status, null,
          offer == null ? null : offer.investorName(), null,
          offer == null ? new LockTerms(null, null, null) : new LockTerms(offer.noteRate(), offer.price(), null),
          correlationId);
    }
  }

  record LockExtensionServiceRequest(
      String requestId,
      String actorId,
      int expectedVersion,
      int requestedDays,
      Instant requestedAt,
      Instant requestedExpiresAt,
      String reasonCode,
      ExtensionCostSnapshot costSnapshot,
      boolean permissionGranted,
      boolean extensionPolicyResolved,
      boolean compliancePermitsAmendedTerms,
      boolean investorSupportsExtension,
      String complianceEvidenceRef,
      Map<String, String> sourceRefs) {
    static LockExtensionServiceRequest from(LosLockResponse existing, LosLockExtendRequest request) {
      Instant requestedAt = Instant.now();
      Instant requestedExpiresAt = existing.lockExpiration() == null
          ? requestedAt
          : existing.lockExpiration().plusSeconds((long) request.extendByDays() * 24L * 60L * 60L);
      return new LockExtensionServiceRequest(
          "los-lock-extension:" + safe(existing.lockId()),
          safe(request.requestedBy()),
          1,
          request.extendByDays() == null ? 0 : request.extendByDays(),
          requestedAt,
          requestedExpiresAt,
          safe(request.reason()),
          new ExtensionCostSnapshot("unresolved", "unresolved", "unresolved", "unresolved", safe(request.reason()),
              "los-extension-policy-unresolved"),
          true,
          false,
          false,
          false,
          "los-compliance-evidence-unresolved",
          Map.of(
              "source", "pricing-bff-los",
              "pricingRequestId", safe(existing.pricingRequestId()),
              "failClosedReason", "tenant extension policy evidence is not resolved in pricing-bff"));
    }
  }

  record ExtensionCostSnapshot(String priceAdjustment, String feeAmount, String payerType, String roundingMode,
      String reasonCode, String policyVersionId) {}

  record LockExtensionServiceResponse(String lockId, String status, Instant expiresAt, String correlationId) {
    LosLockResponse toLosResponse(LosLockResponse existing) {
      return new LosLockResponse(existing.lockId(), existing.pricingRequestId(), existing.offerId(), status,
          expiresAt, existing.investor(), existing.investorLockReference(), existing.terms(), correlationId);
    }
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "unresolved" : value.trim();
  }
}
