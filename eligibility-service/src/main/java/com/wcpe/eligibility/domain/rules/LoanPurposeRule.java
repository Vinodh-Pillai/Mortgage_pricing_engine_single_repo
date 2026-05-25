package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LoanPurposeRule implements EligibilityRule {
    private static final Set<String> ALLOWED_PURPOSES = Set.of(
        "PURCHASE", "CASH_OUT_REFINANCE", "RATE_TERM_REFINANCE"
    );

    @Override
    public RuleType getRuleType() {
        return RuleType.R06;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        String loanPurpose = request.loanProfile().loanPurpose();

        if (loanPurpose == null || loanPurpose.isBlank()) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R06", "LOAN_PURPOSE", "WARNING", "INSUFFICIENT_DATA",
                "LP01", "Loan purpose is missing.",
                null, String.join(", ", ALLOWED_PURPOSES),
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        boolean pass = ALLOWED_PURPOSES.contains(loanPurpose);

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R06", "LOAN_PURPOSE",
            pass ? "PASS" : "HARD_STOP",
            pass ? "ELIGIBLE" : "INELIGIBLE",
            pass ? null : "LP01",
            pass ? "Loan purpose is acceptable." : "Loan purpose is not acceptable.",
            loanPurpose, String.join(", ", ALLOWED_PURPOSES),
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
