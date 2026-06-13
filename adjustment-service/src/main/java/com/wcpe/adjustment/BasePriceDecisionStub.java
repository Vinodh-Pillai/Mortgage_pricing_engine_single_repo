package com.wcpe.adjustment;

/**
 * Lightweight BasePriceDecision reference used by the adjustment-service slice.
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
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("BasePriceDecisionStub source is required");
        }
    }
}
