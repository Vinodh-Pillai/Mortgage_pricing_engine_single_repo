package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public class LoanAmountRule implements EligibilityRule {
    private final CatalogClient catalogClient;

    public LoanAmountRule(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @Override
    public RuleType getRuleType() {
        return RuleType.R11;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        BigDecimal loanAmount = request.loanProfile().loanAmount();
        String state = request.propertyProfile().state();

        if (loanAmount == null || state == null) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R11", "LOAN_AMOUNT_LIMIT", "WARNING", "INSUFFICIENT_DATA",
                "AL01", "Loan amount or state is missing.",
                null, null,
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        Integer limit = catalogClient.getConformingLimit(state);

        if (limit == null) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R11", "LOAN_AMOUNT_LIMIT", "WARNING", "INSUFFICIENT_DATA",
                "AL02", "Conforming loan limit not found for state.",
                loanAmount.toPlainString(), null,
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        boolean pass = loanAmount.compareTo(BigDecimal.valueOf(limit)) <= 0;

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R11", "LOAN_AMOUNT_LIMIT",
            pass ? "PASS" : "HARD_STOP",
            pass ? "ELIGIBLE" : "INELIGIBLE",
            pass ? null : "AL01",
            pass ? "Loan amount is within conforming limit." : "Loan amount exceeds conforming limit.",
            loanAmount.toPlainString(), String.valueOf(limit),
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
