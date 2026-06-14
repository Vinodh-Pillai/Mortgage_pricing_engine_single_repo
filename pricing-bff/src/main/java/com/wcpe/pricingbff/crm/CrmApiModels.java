package com.wcpe.pricingbff.crm;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

final class CrmApiModels {
  private CrmApiModels() {
  }

  record CrmPricingResponse(
      String requestId,
      String tenantId,
      String sourceSystem,
      String externalLeadId,
      String status,
      Map<String, Object> quoteSummary,
      List<MissingFact> missingFacts,
      List<EligibilityBlocker> eligibilityBlockers,
      List<UnsupportedField> unsupportedFields,
      Map<String, String> replayRefs,
      Map<String, String> sourceRefs,
      String statusUrl,
      String quoteJobId,
      Instant updatedAt,
      String correlationId) {
    CrmPricingResponse {
      quoteSummary = Map.copyOf(quoteSummary == null ? Map.of() : quoteSummary);
      missingFacts = List.copyOf(missingFacts == null ? List.of() : missingFacts);
      eligibilityBlockers = List.copyOf(eligibilityBlockers == null ? List.of() : eligibilityBlockers);
      unsupportedFields = List.copyOf(unsupportedFields == null ? List.of() : unsupportedFields);
      replayRefs = Map.copyOf(replayRefs == null ? Map.of() : replayRefs);
      sourceRefs = Map.copyOf(sourceRefs == null ? Map.of() : sourceRefs);
    }
  }

  record MissingFact(String field, String reason, List<String> acceptedAliases) {
    MissingFact {
      acceptedAliases = List.copyOf(acceptedAliases == null ? List.of() : acceptedAliases);
    }
  }

  record EligibilityBlocker(String code, String message, String source) {
  }

  record UnsupportedField(String field, String reason) {
  }

  record CrmWebhookRegistrationRequest(String url, List<String> events, String secret, String tenantId, String sourceSystem) {
    CrmWebhookRegistrationRequest {
      events = List.copyOf(events == null ? List.of() : events);
    }
  }

  record CrmWebhookRegistrationResponse(String webhookId, String tenantId, String sourceSystem, String url,
      List<String> events, String status, Instant registeredAt) {
    CrmWebhookRegistrationResponse {
      events = List.copyOf(events == null ? List.of() : events);
    }
  }

  record WebhookEvent(String eventType, Map<String, Object> payload, String correlationId, Instant occurredAt) {
    WebhookEvent {
      payload = Map.copyOf(payload == null ? Map.of() : payload);
    }
  }

  record WebhookDeliveryReceipt(String deliveryId, String webhookId, String eventType, String status,
      int attemptCount, Instant nextRetryAt, String lastError) {
  }

  record PipelinePromotionResponse(String requestId, String tenantId, String status, String pipelineRef,
      List<MissingFact> missingFacts, List<EligibilityBlocker> eligibilityBlockers, String correlationId) {
    PipelinePromotionResponse {
      missingFacts = List.copyOf(missingFacts == null ? List.of() : missingFacts);
      eligibilityBlockers = List.copyOf(eligibilityBlockers == null ? List.of() : eligibilityBlockers);
    }
  }

  record CrmDashboardResponse(String tenantId, String sourceSystem, List<CrmPricingResponse> pricingRequests,
      Map<String, Object> nonQmPricingSummary, Instant generatedAt) {
    CrmDashboardResponse {
      pricingRequests = List.copyOf(pricingRequests == null ? List.of() : pricingRequests);
      nonQmPricingSummary = Map.copyOf(nonQmPricingSummary == null ? Map.of() : nonQmPricingSummary);
    }
  }

  record ScenarioSaveResponse(String scenarioId, String requestId, String tenantId, String status,
      Map<String, String> replayRefs, Instant savedAt) {
    ScenarioSaveResponse {
      replayRefs = Map.copyOf(replayRefs == null ? Map.of() : replayRefs);
    }
  }

  record ScenarioShareResponse(String scenarioId, String shareRef, String status, String expiresPolicy,
      String correlationId) {
  }

  record QuoteServiceRequest(String tenantId, String scenarioId, int scenarioVersion, List<Integer> requestedLockPeriods,
      Map<String, String> clientContext, String actorId, String idempotencyKey, String correlationId,
      LocalDate effectiveDate, boolean preferAsync) {
    QuoteServiceRequest {
      requestedLockPeriods = List.copyOf(requestedLockPeriods == null ? List.of() : requestedLockPeriods);
      clientContext = Map.copyOf(clientContext == null ? Map.of() : clientContext);
    }
  }

  record QuoteServiceResponse(String jobId, String status, String statusUrl, String correlationId) {
  }
}
