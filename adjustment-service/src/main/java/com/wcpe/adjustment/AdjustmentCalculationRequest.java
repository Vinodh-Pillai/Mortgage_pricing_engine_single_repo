package com.wcpe.adjustment;

/**
 * Adjustment calculation input for PII-06 mock-backed foundation.
 * Values are symbolic or synthetic; no real LLPA reference data is encoded.
 */
public record AdjustmentCalculationRequest(
    BasePriceDecisionStub basePriceDecision,
    java.util.Map<String, String> loanAttributes,
    java.util.List<AdjustmentFactor> adjustmentFactors,
    String referenceDataVersion
) {}
