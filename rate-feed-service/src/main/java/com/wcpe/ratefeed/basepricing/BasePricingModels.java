package com.wcpe.ratefeed.basepricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BasePricingModels {
  private BasePricingModels() {}

  public enum DecisionStatus { PRICED, NO_PRICE }

  public enum NoPriceCode {
    MISSING_REQUIRED_IDENTITY,
    MISSING_LOOKUP_POLICY,
    MISSING_ROUNDING_CONFIGURATION,
    NO_ACTIVE_SOURCE,
    AMBIGUOUS_ACTIVE_SOURCE,
    NO_EXACT_GRID_CELL,
    UNSUPPORTED_LOCK_PERIOD,
    UNSUPPORTED_SCOPE
  }

  public record LookupPolicy(String policyVersion, boolean exactOnly) {}

  public record RoundingPolicy(String ruleVersion, int scale, RoundingMode mode) {}

  public record BasePricingRequest(
      UUID tenantId,
      UUID investorId,
      UUID channelId,
      String productCode,
      BigDecimal noteRate,
      Integer lockPeriod,
      Instant asOf,
      String correlationId,
      String requestId,
      LookupPolicy lookupPolicy,
      RoundingPolicy roundingPolicy
  ) {}

  public record RateStackRequest(
      UUID tenantId,
      UUID investorId,
      UUID channelId,
      String productCode,
      List<RateStackOption> requestedOptions,
      Instant asOf,
      String correlationId,
      String requestId,
      LookupPolicy lookupPolicy,
      RoundingPolicy roundingPolicy
  ) {}

  public record RateStackOption(BigDecimal noteRate, Integer lockPeriod) {}

  public record BasePricingSourceDetails(
      UUID rateSheetId,
      int rateSheetVersion,
      String gridVersion,
      String gridCellId,
      BigDecimal noteRate,
      Integer lockPeriod,
      Instant activationTimestamp,
      Instant asOf,
      String lookupPolicyVersion,
      String roundingRuleVersion,
      String checksum
  ) {}

  public record RoundingDetails(String ruleVersion, BigDecimal input, BigDecimal output) {}

  public record NoPriceReason(
      NoPriceCode code,
      String message,
      boolean retryable,
      String field,
      String evidenceId
  ) {}

  public record ReplayEvidence(String replayHash, List<String> inputs) {}

  public record BasePricingDecision(
      DecisionStatus status,
      BigDecimal basePrice,
      BigDecimal noteRate,
      Integer lockPeriod,
      BasePricingSourceDetails sourceDetails,
      RoundingDetails roundingDetails,
      NoPriceReason noPriceReason,
      ReplayEvidence replayEvidence,
      String correlationId,
      String requestId,
      Instant timestampUtc,
      String contractVersion
  ) {}

  public record RateStack(List<BasePricingDecision> decisions) {}
}
