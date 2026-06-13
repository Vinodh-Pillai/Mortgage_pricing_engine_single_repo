package com.wcpe.margin.overlay;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OverlayRule(UUID tenantId, UUID ruleBookId, UUID ruleId, OverlayPolicyType type,
    String investorCode, String channelCode, String productFamily, String loanPurpose, String occupancy,
    String propertyType, String stateCode, String countyCode, BigDecimal adjustmentBps, BigDecimal minAdjustment,
    BigDecimal maxAdjustment, int priority, String exclusivityGroup, String reasonCode,
    Instant effectiveStart, Instant effectiveEnd, boolean enabled) {
  public OverlayRule {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(ruleBookId, "ruleBookId is required");
    Objects.requireNonNull(ruleId, "ruleId is required");
    Objects.requireNonNull(type, "type is required");
    adjustmentBps = adjustmentBps == null ? BigDecimal.ZERO : adjustmentBps;
    minAdjustment = minAdjustment == null ? adjustmentBps : minAdjustment;
    maxAdjustment = maxAdjustment == null ? adjustmentBps : maxAdjustment;
    if (minAdjustment.compareTo(maxAdjustment) > 0) {
      throw new IllegalArgumentException("overlay minAdjustment must be <= maxAdjustment");
    }
    reasonCode = reasonCode == null || reasonCode.isBlank() ? type.name() : reasonCode;
    investorCode = wildcard(investorCode);
    channelCode = wildcard(channelCode);
    productFamily = wildcard(productFamily);
    loanPurpose = wildcard(loanPurpose);
    occupancy = wildcard(occupancy);
    propertyType = wildcard(propertyType);
    stateCode = wildcard(stateCode);
    countyCode = wildcard(countyCode);
    exclusivityGroup = exclusivityGroup == null || exclusivityGroup.isBlank() ? null : exclusivityGroup;
  }

  public BigDecimal boundedAdjustmentBps() {
    return adjustmentBps.max(minAdjustment).min(maxAdjustment);
  }

  public BigDecimal boundedAdjustmentPoints() {
    return boundedAdjustmentBps().movePointLeft(2);
  }

  boolean activeAt(Instant quoteDate) {
    Instant effectiveAt = quoteDate == null ? Instant.now() : quoteDate;
    return !effectiveAt.isBefore(effectiveStart) && (effectiveEnd == null || effectiveAt.isBefore(effectiveEnd));
  }

  boolean scopeMatches(OverlayInputs inputs) {
    return matches(investorCode, inputs.investorCode()) && matches(channelCode, inputs.channelCode())
        && matches(productFamily, inputs.productFamily()) && matches(loanPurpose, inputs.loanPurpose())
        && matches(occupancy, inputs.occupancy()) && matches(propertyType, inputs.propertyType())
        && matches(stateCode, inputs.stateCode()) && matches(countyCode, inputs.countyCode());
  }

  private static boolean matches(String ruleValue, String inputValue) {
    return "*".equals(ruleValue) || inputValue == null || "*".equals(inputValue) || ruleValue.equalsIgnoreCase(inputValue);
  }

  private static String wildcard(String value) {
    return value == null || value.isBlank() ? "*" : value;
  }
}
