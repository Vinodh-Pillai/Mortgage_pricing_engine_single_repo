package com.wcpe.adjustment;

/**
 * Temporary mock-backed BasePriceDecision stub for PII-06.
 * Does not encode real pricing rates, LLPA grids, regulatory thresholds, or production constants.
 */
public record BasePriceDecisionStub(
    String scenarioId,
    String basePriceId,
    String baseRateBasis,
    double basePriceAmount,
    String currency,
    String source
) {
    public BasePriceDecisionStub {
        if (source == null || !source.equals("mock")) {
            throw new IllegalArgumentException("BasePriceDecisionStub source must be \"mock\"");
        }
    }
}
