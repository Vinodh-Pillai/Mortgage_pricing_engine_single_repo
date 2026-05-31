package com.wcpe.adjustment;

import java.util.List;

/**
 * Adjustment calculation output for PII-06 mock-backed foundation.
 * totalAdjustment is derived from supplied fixture factors, not from hardcoded production rules.
 */
public record AdjustmentCalculationResult(
    String scenarioId,
    String basePriceId,
    List<AdjustmentLine> adjustments,
    double totalAdjustment,
    String referenceDataVersion,
    String calculationMode
) {}
