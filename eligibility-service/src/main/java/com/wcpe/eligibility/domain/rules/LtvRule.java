package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

public class LtvRule implements EligibilityRule {
    private static final BigDecimal MAX_LTV = new BigDecimal("0.97");

    @Override
    public RuleType getRuleType() {
        return RuleType.R02;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        BigDecimal loanAmount = request.loanProfile().loanAmount();
        BigDecimal purchasePrice = request.propertyProfile().purchasePrice();
        BigDecimal appraisedValue = request.propertyProfile().appraisedValue();

        if (loanAmount == null || purchasePrice == null) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R02", "LTV_MAXIMUM", "WARNING", "INSUFFICIENT_DATA",
                "LT01", "Loan amount or purchase price is missing.",
                null, MAX_LTV.toPlainString(),
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        BigDecimal collateral = purchasePrice;
        if (appraisedValue != null) {
            collateral = purchasePrice.min(appraisedValue);
        }

        BigDecimal ltv = loanAmount.divide(collateral, 5, RoundingMode.HALF_UP);
        boolean pass = ltv.compareTo(MAX_LTV) <= 0;

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R02", "LTV_MAXIMUM",
            pass ? "PASS" : "HARD_STOP",
            pass ? "ELIGIBLE" : "INELIGIBLE",
            pass ? null : "LT01",
            pass ? "LTV is within acceptable limit." : "LTV exceeds maximum allowed limit.",
            ltv.toPlainString(), MAX_LTV.toPlainString(),
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
