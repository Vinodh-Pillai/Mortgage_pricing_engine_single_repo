package com.wcpe.pricingbff.los;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LosApiModels {
  private LosApiModels() {
  }

  public record LosPricingRequest(
      String requestId,
      String tenantId,
      String callbackUrl,
      LoanPassBorrowerInfo quoteBorrowerInfo,
      LoanPassAddress quoteAddressDTO,
      BigDecimal requestedLoanAmount,
      BigDecimal purchasePrice,
      BigDecimal propertyValue,
      String transactionType,
      String propertyInformationType,
      String occupancyType,
      Integer numberOfUnits,
      String incomeDocumentationType,
      BigDecimal totalMonthlyIncome,
      BigDecimal totalLiabilityMonthlyPayment,
      BigDecimal debtToIncomeRatio,
      Integer monthsOfReserves,
      Integer creditScore,
      String mortgageType,
      String amortizationType,
      String loanTermType,
      Integer desiredRateLockPeriod,
      String lockPeriodType,
      String channelType,
      List<CreditApplicationField> creditApplicationFields) {
    public LosPricingRequest {
      creditApplicationFields = List.copyOf(creditApplicationFields == null ? List.of() : creditApplicationFields);
    }
  }

  public record LoanPassBorrowerInfo(String borrowerLastName, String loanNumber, Integer numberOfBorrowers) {}

  public record LoanPassAddress(String street, String city, String state, String zip, String countyFips,
      String countyCode, String countyName, String searchString) {}

  public record CreditApplicationField(String fieldId, CreditApplicationValue value) {}

  public record CreditApplicationValue(String type, Object value, String enumTypeId, String variantId) {}

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
      String productId,
      String selectedProgramId,
      String priceGroupId,
      String investorId,
      String investorName,
      BigDecimal noteRate,
      BigDecimal price,
      BigDecimal apr,
      Integer desiredRateLockPeriod,
      String lockPeriodType,
      String eligibilityStatus,
      Boolean bestExecution,
      Integer rank,
      Map<String, Object> loanPassProduct) {
    public LosOffer {
      loanPassProduct = Map.copyOf(loanPassProduct == null ? Map.of() : loanPassProduct);
    }
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
      List<Integer> requestedLockPeriods) {
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
      boolean preferAsync,
      String requestId,
      LoanPassBorrowerInfo quoteBorrowerInfo,
      LoanPassAddress quoteAddressDTO,
      BigDecimal requestedLoanAmount,
      BigDecimal purchasePrice,
      BigDecimal propertyValue,
      String transactionType,
      String propertyInformationType,
      String occupancyType,
      Integer numberOfUnits,
      String incomeDocumentationType,
      BigDecimal totalMonthlyIncome,
      BigDecimal totalLiabilityMonthlyPayment,
      BigDecimal debtToIncomeRatio,
      Integer monthsOfReserves,
      Integer creditScore,
      String mortgageType,
      String amortizationType,
      String loanTermType,
      Integer desiredRateLockPeriod,
      String lockPeriodType,
      String channelType,
      List<CreditApplicationField> creditApplicationFields) {
    public QuoteServiceRequest {
      requestedLockPeriods = List.copyOf(requestedLockPeriods == null ? List.of() : requestedLockPeriods);
      clientContext = Map.copyOf(clientContext == null ? Map.of() : clientContext);
      creditApplicationFields = List.copyOf(creditApplicationFields == null ? List.of() : creditApplicationFields);
    }
  }

  public record QuoteServiceResponse(String jobId, String status, String statusUrl, String correlationId) {
  }

  public record ErrorResponse(String code, String message, String correlationId) {
  }
}
