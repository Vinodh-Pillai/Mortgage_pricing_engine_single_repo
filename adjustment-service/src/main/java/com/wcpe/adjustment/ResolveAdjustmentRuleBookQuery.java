package com.wcpe.adjustment;

import com.wcpe.adjustment.AdjustmentRuleBook.RuleBookSelector;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal quote-waterfall query for locating the published adjustment rule book
 * that applies to a quote context.
 */
public record ResolveAdjustmentRuleBookQuery(
    UUID tenantId,
    RuleBookSelector ruleBookSelector,
    Instant quoteDate,
    String correlationId
) {
    public ResolveAdjustmentRuleBookQuery {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(ruleBookSelector, "ruleBookSelector is required");
        Objects.requireNonNull(quoteDate, "quoteDate is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
    }
}
