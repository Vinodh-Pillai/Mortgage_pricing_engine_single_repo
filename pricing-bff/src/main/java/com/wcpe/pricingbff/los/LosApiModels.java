package com.wcpe.pricingbff.los;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class LosApiModels {
  private LosApiModels() {
  }

  public record LosPricingRequest(
      String requestId,
      LosLoan loan,
      LosPricing pricing,
      String tenantId,
      String callbackUrl) {
  }

  public record LosLoan(
      String loanPurpose,
      BigDecimal loanAmount,
      String loanType,
      Integer termMonths,
      String amortizationType,
      LosProperty property,
      List<LosBorrower> borrowers) {
    public LosLoan {
      borrowers = List.copyOf(borrowers == null ? List.of() : borrowers);
    }
  }

  public record LosProperty(
      String address,
      String city,
      String state,
      String zip,
      String county,
      String propertyType,
      String occupancy,
      Integer units,
      BigDecimal purchasePrice,
      BigDecimal appraisedValue) {
  }

  public record LosBorrower(
      String borrowerId,
      Integer creditScore,
      String creditScoreSource,
      LocalDate creditReportDate,
      BorrowerIncome income,
      BorrowerAssets assets,
      BorrowerLiabilities liabilities) {
  }

  public record BorrowerIncome(BigDecimal monthly, String type) {
  }

  public record BorrowerAssets(BigDecimal liquid, BigDecimal retirement) {
  }

  public record BorrowerLiabilities(BigDecimal monthlyDebt) {
  }

  public record LosPricing(
      String channel,
      String productFamily,
      List<String> investorPreference,
      Integer lockPeriodDays,
      LocalDate effectiveDate) {
    public LosPricing {
      investorPreference = List.copyOf(investorPreference == null ? List.of() : investorPreference);
    }
  }

  public record LosPricingResponse(
      String requestId,
      String pricingRequestId,
      String status,
      List<LosOffer> offers,
      List<WaterfallStep> waterfall,
      Instant completedAt,
      String statusUrl,
      String quoteJobId,
      String correlationId) {
    public LosPricingResponse {
      offers = List.copyOf(offers == null ? List.of() : offers);
      waterfall = List.copyOf(waterfall == null ? List.of() : waterfall);
    }
  }

  public record LosOffer(
      String offerId,
      String investor,
      String investorName,
      BigDecimal rate,
      BigDecimal price,
      BigDecimal points,
      BigDecimal apr,
      Integer lockPeriodDays,
      LocalDate lockExpiration,
      List<LosLlpa> llpas,
      BigDecimal totalAdjustmentPoints,
      BigDecimal margin,
      Integer marginBps,
      String eligibility,
      Boolean bestExecution,
      Integer rank) {
    public LosOffer {
      llpas = List.copyOf(llpas == null ? List.of() : llpas);
    }
  }

  public record LosLlpa(String code, String description, BigDecimal points) {
  }

  public record WaterfallStep(String step, BigDecimal input, String operation, BigDecimal output) {
  }

  public record LosLockRequest(
      String pricingRequestId,
      String offerId,
      Integer lockPeriodDays,
      String requestedBy) {
  }

  public record LosLockExtendRequest(Integer extendByDays, String requestedBy, String reason) {
  }

  public record LosLockResponse(
      String lockId,
      String pricingRequestId,
      String offerId,
      String status,
      Instant lockExpiration,
      String investor,
      String investorLockReference,
      LockTerms terms,
      String correlationId) {
  }

  public record LockTerms(BigDecimal rate, BigDecimal price, BigDecimal points) {
  }

  public record LosWebhookRegistrationRequest(String url, List<String> events, String secret, String tenantId) {
    public LosWebhookRegistrationRequest {
      events = List.copyOf(events == null ? List.of() : events);
    }
  }

  public record LosWebhookRegistrationResponse(
      String webhookId,
      String tenantId,
      String url,
      List<String> events,
      String status,
      Instant registeredAt) {
    public LosWebhookRegistrationResponse {
      events = List.copyOf(events == null ? List.of() : events);
    }
  }

  public record WebhookEvent(String eventType, Map<String, Object> payload, String correlationId, Instant occurredAt) {
    public WebhookEvent {
      payload = Map.copyOf(payload == null ? Map.of() : payload);
    }
  }

  public record WebhookDeliveryReceipt(
      String deliveryId,
      String webhookId,
      String eventType,
      String status,
      int attemptCount,
      Instant nextRetryAt,
      String lastError) {
  }

  public record LosScenario(
      String scenarioId,
      String tenantId,
      int scenarioVersion,
      Map<String, String> loanFacts,
      List<Integer> requestedLockPeriods,
      LocalDate effectiveDate) {
    public LosScenario {
      loanFacts = Map.copyOf(loanFacts == null ? Map.of() : loanFacts);
      requestedLockPeriods = List.copyOf(requestedLockPeriods == null ? List.of() : requestedLockPeriods);
    }
  }

  public record QuoteServiceRequest(
      String tenantId,
      String scenarioId,
      int scenarioVersion,
      List<Integer> requestedLockPeriods,
      Map<String, String> clientContext,
      String actorId,
      String idempotencyKey,
      String correlationId,
      LocalDate effectiveDate,
      boolean preferAsync) {
    public QuoteServiceRequest {
      requestedLockPeriods = List.copyOf(requestedLockPeriods == null ? List.of() : requestedLockPeriods);
      clientContext = Map.copyOf(clientContext == null ? Map.of() : clientContext);
    }
  }

  public record QuoteServiceResponse(String jobId, String status, String statusUrl, String correlationId) {
  }

  public record ErrorResponse(String code, String message, String correlationId) {
  }
}
