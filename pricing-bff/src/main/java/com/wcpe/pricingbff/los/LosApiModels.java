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
      String productId,
      String selectedProgramId,
      String priceGroupId,
      String scenarioId,
      Integer scenarioVersion,
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

  public record LosProductCatalogResponse(
      List<LosProductSummary> products,
      int count,
      int page,
      int pageSize,
      int totalAvailable,
      String authorizationStatus,
      String blockedReason,
      Map<String, Object> metadata) {
    public LosProductCatalogResponse {
      products = List.copyOf(products == null ? List.of() : products);
      metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
  }

  public record LosProductSummary(
      String productId,
      String displayName,
      String productFamily,
      String productType,
      List<String> supportedPurposes,
      List<String> supportedTerms,
      List<String> amortizationRefs,
      List<String> investorRefs,
      List<String> channelRefs,
      String effectiveStatus,
      String detailUrl,
      Map<String, Object> loanPassMappings) {
    public LosProductSummary {
      supportedPurposes = List.copyOf(supportedPurposes == null ? List.of() : supportedPurposes);
      supportedTerms = List.copyOf(supportedTerms == null ? List.of() : supportedTerms);
      amortizationRefs = List.copyOf(amortizationRefs == null ? List.of() : amortizationRefs);
      investorRefs = List.copyOf(investorRefs == null ? List.of() : investorRefs);
      channelRefs = List.copyOf(channelRefs == null ? List.of() : channelRefs);
      loanPassMappings = Map.copyOf(loanPassMappings == null ? Map.of() : loanPassMappings);
    }
  }

  public record LosProductDetailResponse(
      String productId,
      LosProductSummary summary,
      String authorizationStatus,
      String blockedReason,
      List<LosProductFieldRequirement> requiredFields,
      List<LosProductFieldRequirement> conditionalFields,
      Map<String, Object> supportedValues,
      String mappingMetadataStatus,
      Map<String, Object> quoteCompatibility,
      Map<String, Object> metadata) {
    public LosProductDetailResponse {
      requiredFields = List.copyOf(requiredFields == null ? List.of() : requiredFields);
      conditionalFields = List.copyOf(conditionalFields == null ? List.of() : conditionalFields);
      supportedValues = Map.copyOf(supportedValues == null ? Map.of() : supportedValues);
      quoteCompatibility = Map.copyOf(quoteCompatibility == null ? Map.of() : quoteCompatibility);
      metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
  }

  public record LosProductFieldRequirement(
      String fieldId,
      String requirementType,
      String mappingStatus,
      String metadataRef,
      Map<String, Object> constraints) {
    public LosProductFieldRequirement {
      constraints = Map.copyOf(constraints == null ? Map.of() : constraints);
    }
  }

  public record LosProductEligibilityRequest(
      String tenantId,
      String clientId,
      String correlationId,
      List<String> productIds,
      String productFamily,
      String channel,
      String investor,
      Map<String, Object> loanFields,
      List<CreditApplicationField> creditApplicationFields) {
    public LosProductEligibilityRequest {
      productIds = List.copyOf(productIds == null ? List.of() : productIds);
      loanFields = Map.copyOf(loanFields == null ? Map.of() : loanFields);
      creditApplicationFields = List.copyOf(creditApplicationFields == null ? List.of() : creditApplicationFields);
    }
  }

  public record LosProductEligibilityResponse(
      String status,
      String correlationId,
      List<LosProductEligibilityResult> results,
      List<String> reasonCodes,
      Map<String, Object> metadata) {
    public LosProductEligibilityResponse {
      results = List.copyOf(results == null ? List.of() : results);
      reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
      metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
  }

  public record LosProductEligibilityResult(
      String productId,
      String eligibility,
      List<String> reasonCodes,
      List<LosProductEligibilityFieldMessage> fieldMessages,
      List<LosProductEligibilityRuleRef> ruleConfigRefs,
      String productSummaryRef) {
    public LosProductEligibilityResult {
      reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
      fieldMessages = List.copyOf(fieldMessages == null ? List.of() : fieldMessages);
      ruleConfigRefs = List.copyOf(ruleConfigRefs == null ? List.of() : ruleConfigRefs);
    }
  }

  public record LosProductEligibilityFieldMessage(
      String requestPath,
      String fieldId,
      String reasonCode,
      String message) {}

  public record LosProductEligibilityRuleRef(
      String source,
      String ref,
      String status) {}

  public record LoanPassExecuteSummaryRequest(
      String tenantId,
      String pricingProfileId,
      java.time.Instant currentTime,
      List<CreditApplicationField> creditApplicationFields,
      List<CreditApplicationField> ausFields,
      Map<String, Object> outputFieldsFilter,
      Map<String, Object> publishedVersionRequest,
      String pipelineRecordId) {
    public LoanPassExecuteSummaryRequest {
      creditApplicationFields = List.copyOf(creditApplicationFields == null ? List.of() : creditApplicationFields);
      ausFields = List.copyOf(ausFields == null ? List.of() : ausFields);
      outputFieldsFilter = Map.copyOf(outputFieldsFilter == null ? Map.of() : outputFieldsFilter);
      publishedVersionRequest = Map.copyOf(publishedVersionRequest == null ? Map.of() : publishedVersionRequest);
    }
  }

  public record LoanPassExecuteProductRequest(
      String tenantId,
      String productId,
      String pricingProfileId,
      java.time.Instant currentTime,
      List<CreditApplicationField> creditApplicationFields,
      List<CreditApplicationField> ausFields,
      Map<String, Object> outputFieldsFilter,
      Map<String, Object> publishedVersionRequest,
      String pipelineRecordId) {
    public LoanPassExecuteProductRequest {
      creditApplicationFields = List.copyOf(creditApplicationFields == null ? List.of() : creditApplicationFields);
      ausFields = List.copyOf(ausFields == null ? List.of() : ausFields);
      outputFieldsFilter = Map.copyOf(outputFieldsFilter == null ? Map.of() : outputFieldsFilter);
      publishedVersionRequest = Map.copyOf(publishedVersionRequest == null ? Map.of() : publishedVersionRequest);
    }
  }

  public record LoanPassExecutionSummaryResponse(
      LoanPassExecutionSummaryTotals totals,
      List<LoanPassExecutionProductSummary> products,
      String versionNumber,
      Map<String, Object> metadata) {
    public LoanPassExecutionSummaryResponse {
      products = List.copyOf(products == null ? List.of() : products);
      metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
  }

  public record LoanPassExecutionSummaryTotals(
      int approved,
      int reviewRequired,
      int available,
      int rejected,
      int error) {}

  public record LoanPassExecutionProductSummary(
      String productId,
      String productName,
      String productCode,
      String investorName,
      String investorCode,
      List<CreditApplicationField> productFields,
      List<CreditApplicationField> calculatedFields,
      Boolean isPricingEnabled,
      Map<String, Object> status,
      String versionNumber) {
    public LoanPassExecutionProductSummary {
      productFields = List.copyOf(productFields == null ? List.of() : productFields);
      calculatedFields = List.copyOf(calculatedFields == null ? List.of() : calculatedFields);
      status = Map.copyOf(status == null ? Map.of() : status);
    }
  }

  public record LoanPassProductExecutionResult(
      String productId,
      String productName,
      String productCode,
      String investorName,
      String investorCode,
      Boolean isPricingEnabled,
      List<CreditApplicationField> productFields,
      List<CreditApplicationField> calculatedFields,
      Map<String, Object> status,
      String versionNumber,
      Map<String, Object> metadata) {
    public LoanPassProductExecutionResult {
      productFields = List.copyOf(productFields == null ? List.of() : productFields);
      calculatedFields = List.copyOf(calculatedFields == null ? List.of() : calculatedFields);
      status = Map.copyOf(status == null ? Map.of() : status);
      metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
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

  public record LosWebhookRegistrationRequest(String url, List<String> events, String secret, String signingCredentialRef,
      String tenantId) {
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
      String lastError,
      String idempotencyKey,
      String signatureHeader,
      String auditRef) {
    public WebhookDeliveryReceipt(String deliveryId, String webhookId, String eventType, String status,
        int attemptCount, Instant nextRetryAt, String lastError) {
      this(deliveryId, webhookId, eventType, status, attemptCount, nextRetryAt, lastError, null, null, null);
    }
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
      String productId,
      String selectedProgramId,
      String priceGroupId,
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
