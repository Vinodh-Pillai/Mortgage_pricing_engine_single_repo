package com.wcpe.adjustment;

import java.util.List;
import java.util.Map;

/**
 * Adjustment calculation output for real rule-book execution.
 */
public record AdjustmentCalculationResult(
    String scenarioId,
    String basePriceId,
    List<AdjustmentLine> adjustments,
    double totalAdjustment,
    String referenceDataVersion,
    String calculationMode,
    List<String> auditRefs,
    String resultHash,
    Map<String, Object> totalsByType,
    boolean blocked
) {
    public AdjustmentCalculationResult(
        String scenarioId,
        String basePriceId,
        List<AdjustmentLine> adjustments,
        double totalAdjustment,
        String referenceDataVersion,
        String calculationMode
    ) {
        this(scenarioId, basePriceId, adjustments, totalAdjustment, referenceDataVersion, calculationMode, List.of(), "", Map.of(), false);
    }

    public AdjustmentCalculationResult {
        adjustments = List.copyOf(adjustments == null ? List.of() : adjustments);
        auditRefs = List.copyOf(auditRefs == null ? List.of() : auditRefs);
        totalsByType = Map.copyOf(totalsByType == null ? Map.of() : totalsByType);
    }
}
