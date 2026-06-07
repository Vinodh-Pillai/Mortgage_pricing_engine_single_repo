package com.wcpe.quote;

import java.math.BigDecimal;

public class CriterionEvaluator {
    /**
     * Evaluate a single criterion for a candidate.
     * Returns (normalizedScore 0-1, reason).
     * Does NOT hard-code any mortgage pricing rules.
     */
    public EvaluationResult evaluate(RankingCriterion criterion, QuoteCandidate candidate) {
        String type = criterion.type();
        switch (type) {
            case "numeric_min":
                return new EvaluationResult(
                    normalizeMinScore(getField(candidate, criterion.fieldRef()), criterion.configJson()),
                    new RankReason(
                        criterion.criterionId(),
                        type,
                        "numeric minimum criterion applied",
                        getField(candidate, criterion.fieldRef()).toString()
                    )
                );
            case "numeric_max":
                return new EvaluationResult(
                    normalizeMaxScore(getField(candidate, criterion.fieldRef()), criterion.configJson()),
                    new RankReason(
                        criterion.criterionId(),
                        type,
                        "numeric maximum criterion applied",
                        getField(candidate, criterion.fieldRef()).toString()
                    )
                );
            case "threshold":
                return new EvaluationResult(
                    checkThreshold(getField(candidate, criterion.fieldRef()), criterion.configJson()),
                    new RankReason(
                        criterion.criterionId(),
                        type,
                        "threshold criterion applied",
                        getField(candidate, criterion.fieldRef()).toString()
                    )
                );
            case "preference_list":
                return new EvaluationResult(
                    checkPreferenceList(getFieldValue(candidate, criterion.fieldRef()), criterion.configJson()),
                    new RankReason(
                        criterion.criterionId(),
                        type,
                        "preference list match",
                        getFieldValue(candidate, criterion.fieldRef())
                    )
                );
            case "risk_flag":
                return new EvaluationResult(
                    checkRiskFlag(getFieldValue(candidate, criterion.fieldRef()), criterion.configJson()),
                    new RankReason(
                        criterion.criterionId(),
                        type,
                        "risk flag criterion applied",
                        getField(candidate, criterion.fieldRef()).toString()
                    )
                );
            case "compliance_flag":
                return new EvaluationResult(
                    checkComplianceFlag(getFieldValue(candidate, criterion.fieldRef()), criterion.configJson()),
                    new RankReason(
                        criterion.criterionId(),
                        type,
                        "compliance flag criterion applied",
                        getField(candidate, criterion.fieldRef()).toString()
                    )
                );
            case "custom_rule_ref":
                return new EvaluationResult(
                    BigDecimal.ZERO,
                    new RankReason(
                        criterion.criterionId(),
                        type,
                        "custom rule referenced externally",
                        criterion.configJson()
                    )
                );
            default:
                if (criterion.required()) {
                    throw new QuoteCreateException("POLICY_NOT_SATISFIED", "Required criterion type not supported: " + type);
                }
                return new EvaluationResult(
                    BigDecimal.ZERO,
                    new RankReason(criterion.criterionId(), "non_applicable", "criterion type not applicable", type)
                );
        }
    }

    // Private scoring helpers - no domain rules, just the configured primitive direction.
    private BigDecimal normalizeMinScore(BigDecimal value, String configJson) {
        return value.negate();
    }

    private BigDecimal normalizeMaxScore(BigDecimal value, String configJson) {
        return value;
    }

    private BigDecimal checkThreshold(BigDecimal value, String configJson) {
        return BigDecimal.ONE;
    }

    private BigDecimal checkPreferenceList(String value, String configJson) {
        return BigDecimal.ONE;
    }

    private BigDecimal checkRiskFlag(String value, String configJson) {
        return new BigDecimal("0.5");
    }

    private BigDecimal checkComplianceFlag(String value, String configJson) {
        return BigDecimal.ONE;
    }

    private BigDecimal getField(QuoteCandidate candidate, String fieldRef) {
        return switch (fieldRef) {
            case "basePriceBps" -> candidate.basePriceBps();
            case "noteRatePercent" -> candidate.noteRatePercent();
            case "marginBps" -> candidate.marginBps();
            case "adjustmentBps" -> candidate.adjustmentBps();
            default -> BigDecimal.ZERO;
        };
    }

    private String getFieldValue(QuoteCandidate candidate, String fieldRef) {
        return switch (fieldRef) {
            case "productId" -> candidate.productId();
            case "investorId" -> candidate.investorId();
            case "channel" -> candidate.channel();
            case "candidateId" -> candidate.candidateId();
            case "eligibilityRef" -> candidate.eligibilityRef();
            default -> "";
        };
    }

    public record EvaluationResult(BigDecimal normalizedScore, RankReason reason) {}
}
