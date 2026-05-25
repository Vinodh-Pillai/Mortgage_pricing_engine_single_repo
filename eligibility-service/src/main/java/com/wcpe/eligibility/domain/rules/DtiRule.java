package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

public class DtiRule implements EligibilityRule {
    private static final BigDecimal MAX_DTI = new BigDecimal("0.43");

    @Override
    public RuleType getRuleType() {
        return RuleType.R03;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        BigDecimal monthlyIncome = request.borrowerProfile().monthlyIncome();
        BigDecimal monthlyDebt = request.borrowerProfile().monthlyDebt();

        if (monthlyIncome == null || monthlyDebt == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R03", "DTI_MAXIMUM", "WARNING", "INSUFFICIENT_DATA",
                "DT01", "Monthly income or monthly debt is missing or invalid.",
                null, MAX_DTI.toPlainString(),
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        BigDecimal dti = monthlyDebt.divide(monthlyIncome, 5, RoundingMode.HALF_UP);
        boolean pass = dti.compareTo(MAX_DTI) <= 0;

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R03", "DTI_MAXIMUM",
            pass ? "PASS" : "HARD_STOP",
            pass ? "ELIGIBLE" : "INELIGIBLE",
            pass ? null : "DT01",
            pass ? "DTI is within acceptable limit." : "DTI exceeds maximum allowed limit.",
            dti.toPlainString(), MAX_DTI.toPlainString(),
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
