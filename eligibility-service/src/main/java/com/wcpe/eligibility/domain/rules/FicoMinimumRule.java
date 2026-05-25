package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.util.Map;
import java.util.UUID;

public class FicoMinimumRule implements EligibilityRule {
    private static final int MIN_FICO = 620;

    @Override
    public RuleType getRuleType() {
        return RuleType.R01;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        Integer fico = request.borrowerProfile().representativeFico();

        if (fico == null) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R01", "FICO_MINIMUM", "WARNING", "INSUFFICIENT_DATA",
                "FC01", "Representative FICO score is missing.",
                null, String.valueOf(MIN_FICO),
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        boolean pass = fico >= MIN_FICO;
        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R01", "FICO_MINIMUM",
            pass ? "PASS" : "HARD_STOP",
            pass ? "ELIGIBLE" : "INELIGIBLE",
            pass ? null : "FC01",
            pass ? "FICO score meets minimum threshold." : "FICO score below minimum threshold.",
            String.valueOf(fico), String.valueOf(MIN_FICO),
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
