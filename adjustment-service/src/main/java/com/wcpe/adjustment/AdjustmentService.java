package com.wcpe.adjustment;

import java.util.*;

/**
 * Mock-backed LLPA adjustment calculator for PII-06.
 * Deterministic: totalAdjustment is the sum of supplied fixture adjustment factors.
 * No real LLPA tables, rates, thresholds, or regulatory values are encoded.
 */
public class AdjustmentService {

    public AdjustmentCalculationResult calculate(AdjustmentCalculationRequest request) {
        List<AdjustmentLine> lines = new ArrayList<>();

        for (AdjustmentFactor factor : request.adjustmentFactors()) {
            lines.add(new AdjustmentLine(
                factor.factorKey(),
                factor.amount(),
                factor.reason(),
                "mock"
            ));
        }

        double total = lines.stream().mapToDouble(AdjustmentLine::amount).sum();

        return new AdjustmentCalculationResult(
            request.basePriceDecision().scenarioId(),
            request.basePriceDecision().basePriceId(),
            lines,
            total,
            request.referenceDataVersion() != null ? request.referenceDataVersion() : "mock-pii-06",
            "mock"
        );
    }
}
