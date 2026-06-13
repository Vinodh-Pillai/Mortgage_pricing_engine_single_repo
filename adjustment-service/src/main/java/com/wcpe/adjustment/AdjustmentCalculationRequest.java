package com.wcpe.adjustment;

import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adjustment calculation input. Rule facts are caller supplied and symbolic; no
 * real LLPA reference data is encoded in source code.
 */
public record AdjustmentCalculationRequest(
    BasePriceDecisionStub basePriceDecision,
    Map<String, String> loanAttributes,
    List<AdjustmentFactor> adjustmentFactors,
    String referenceDataVersion,
    UUID tenantId,
    RuleBookSelector selector,
    Instant quoteDate,
    Map<String, Object> loanFacts
) {
    public AdjustmentCalculationRequest(
        BasePriceDecisionStub basePriceDecision,
        Map<String, String> loanAttributes,
        List<AdjustmentFactor> adjustmentFactors,
        String referenceDataVersion
    ) {
        this(basePriceDecision, loanAttributes, adjustmentFactors, referenceDataVersion, null, null, null, Map.of());
    }

    public AdjustmentCalculationRequest {
        loanAttributes = Map.copyOf(loanAttributes == null ? Map.of() : loanAttributes);
        adjustmentFactors = List.copyOf(adjustmentFactors == null ? List.of() : adjustmentFactors);
        loanFacts = Map.copyOf(loanFacts == null ? Map.of() : loanFacts);
    }

    public Map<String, Object> normalizedFacts() {
        LinkedHashMap<String, Object> facts = new LinkedHashMap<>();
        facts.putAll(loanAttributes);
        facts.putAll(loanFacts);
        if (selector != null) {
            facts.putIfAbsent("productFamily", selector.productFamily());
            facts.putIfAbsent("investor", selector.investor());
            facts.putIfAbsent("channel", selector.channel());
        }
        if (referenceDataVersion != null) {
            facts.putIfAbsent("referenceDataVersion", referenceDataVersion);
        }
        if (quoteDate != null) {
            facts.putIfAbsent("quoteDate", quoteDate.toString());
        }
        return Map.copyOf(facts);
    }
}
