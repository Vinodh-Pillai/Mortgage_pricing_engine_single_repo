package com.wcpe.quote.los;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LosQuoteModels {
  private LosQuoteModels() {
  }

  public record LosQuoteRequest(
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
      Object requestedLoanAmount,
      Object purchasePrice,
      Object propertyValue,
      String transactionType,
      String propertyInformationType,
      String occupancyType,
      Integer numberOfUnits,
      String incomeDocumentationType,
      Object totalMonthlyIncome,
      Object totalLiabilityMonthlyPayment,
      Object debtToIncomeRatio,
      Integer monthsOfReserves,
      Integer creditScore,
      String mortgageType,
      String amortizationType,
      String loanTermType,
      Integer desiredRateLockPeriod,
      String lockPeriodType,
      String channelType,
      List<CreditApplicationField> creditApplicationFields) {
    public LosQuoteRequest {
      requestedLockPeriods = List.copyOf(requestedLockPeriods == null ? List.of() : requestedLockPeriods);
      clientContext = Map.copyOf(clientContext == null ? Map.of() : clientContext);
      creditApplicationFields = List.copyOf(creditApplicationFields == null ? List.of() : creditApplicationFields);
    }
  }

  public record LoanPassBorrowerInfo(String borrowerLastName, String loanNumber, Integer numberOfBorrowers) {}

  public record LoanPassAddress(String street, String city, String state, String zip, String countyFips,
      String countyCode, String countyName, String searchString) {}

  public record CreditApplicationField(String fieldId, CreditApplicationValue value) {}

  public record CreditApplicationValue(String type, Object value, String enumTypeId, String variantId) {}

  public record LosQuoteResponse(
      String jobId,
      String status,
      String statusUrl,
      String correlationId,
      Instant acceptedAt,
      Map<String, String> progress) {
    public LosQuoteResponse {
      progress = Map.copyOf(progress == null ? Map.of() : progress);
    }
  }

  public record LosQuoteError(String code, String message, String correlationId) {
  }
}
