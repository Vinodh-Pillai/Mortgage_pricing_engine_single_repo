package com.wcpe.quote;

import java.math.BigDecimal;
import java.util.Map;

public record QuoteCandidate(
    String candidateId,
    String eligibilityRef,
    String productId,
    String investorId,
    String channel,
    int lockPeriodDays,
    BigDecimal noteRatePercent,
    BigDecimal basePriceBps,
    BigDecimal adjustmentBps,
    BigDecimal marginBps,
    Map<String, String> upstreamRefs,
    AdjustmentCalculationResult adjustmentResult
) {
    public QuoteCandidate(String candidateId, String eligibilityRef, String productId, String investorId, String channel,
            int lockPeriodDays, BigDecimal noteRatePercent, BigDecimal basePriceBps, BigDecimal adjustmentBps,
            BigDecimal marginBps, Map<String, String> upstreamRefs) {
        this(candidateId, eligibilityRef, productId, investorId, channel, lockPeriodDays, noteRatePercent, basePriceBps,
                adjustmentBps, marginBps, upstreamRefs, null);
    }

    public QuoteCandidate {
        upstreamRefs = Map.copyOf(upstreamRefs == null ? Map.of() : upstreamRefs);
    }

    public QuoteCandidate withAdjustmentResult(AdjustmentCalculationResult result, BigDecimal calculatedAdjustmentBps) {
        Map<String, String> refs = new java.util.LinkedHashMap<>(upstreamRefs);
        if (result != null) {
            if (result.resultHash() != null && !result.resultHash().isBlank()) {
                refs.put("adjustmentResultHash", result.resultHash());
            }
            if (result.referenceDataVersion() != null && !result.referenceDataVersion().isBlank()) {
                refs.put("adjustmentVersion", result.referenceDataVersion());
            }
            if (!result.auditRefs().isEmpty()) {
                refs.put("adjustmentAuditRefs", String.join(",", result.auditRefs()));
            }
        }
        return new QuoteCandidate(candidateId, eligibilityRef, productId, investorId, channel, lockPeriodDays,
                noteRatePercent, basePriceBps, calculatedAdjustmentBps, marginBps, refs, result);
    }
}
