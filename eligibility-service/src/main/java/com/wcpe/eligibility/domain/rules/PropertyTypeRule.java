package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PropertyTypeRule implements EligibilityRule {
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "SINGLE_FAMILY", "CONDO", "CO_OP", "2-4UNIT"
    );

    @Override
    public RuleType getRuleType() {
        return RuleType.R04;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        String propertyType = request.propertyProfile().propertyType();

        if (propertyType == null || propertyType.isBlank()) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R04", "PROPERTY_TYPE", "WARNING", "INSUFFICIENT_DATA",
                "PT01", "Property type is missing.",
                null, String.join(", ", ALLOWED_TYPES),
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        boolean pass = ALLOWED_TYPES.contains(propertyType);

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R04", "PROPERTY_TYPE",
            pass ? "PASS" : "HARD_STOP",
            pass ? "ELIGIBLE" : "INELIGIBLE",
            pass ? null : "PT01",
            pass ? "Property type is acceptable." : "Property type is not acceptable.",
            propertyType, String.join(", ", ALLOWED_TYPES),
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
