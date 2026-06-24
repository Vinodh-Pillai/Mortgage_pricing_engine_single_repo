package com.wcpe.quote;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record AdjustmentCalculationRequest(
    BasePriceDecision basePriceDecision,
    Map<String, String> loanAttributes,
    List<AdjustmentFactor> adjustmentFactors,
    String referenceDataVersion,
    UUID tenantId,
    RuleBookSelector selector,
    Instant quoteDate,
    Map<String, Object> loanFacts
) {
    AdjustmentCalculationRequest {
        loanAttributes = Map.copyOf(loanAttributes == null ? Map.of() : loanAttributes);
        adjustmentFactors = List.copyOf(adjustmentFactors == null ? List.of() : adjustmentFactors);
        loanFacts = Map.copyOf(loanFacts == null ? Map.of() : loanFacts);
    }
}

record BasePriceDecision(String scenarioId, String basePriceId, String baseRateBasis, double basePriceAmount, String currency, String source) {}

record RuleBookSelector(String productFamily, String investor, String channel) {}

record AdjustmentFactor(String factorKey, double amount, String reason) {}

record AdjustmentCalculationResult(
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
    AdjustmentCalculationResult {
        adjustments = List.copyOf(adjustments == null ? List.of() : adjustments);
        auditRefs = List.copyOf(auditRefs == null ? List.of() : auditRefs);
        totalsByType = Map.copyOf(totalsByType == null ? Map.of() : totalsByType);
    }
}

record AdjustmentLine(
    String factorKey,
    double amount,
    String reason,
    String source,
    String outputType,
    String ruleId,
    String sourceRef,
    boolean applied,
    String label,
    String auditRef,
    List<String> warnings
) {
    AdjustmentLine {
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
