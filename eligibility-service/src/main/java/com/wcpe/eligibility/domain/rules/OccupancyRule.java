package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class OccupancyRule implements EligibilityRule {
    private static final Set<String> ALLOWED_OCCUPANCY = Set.of(
        "PRIMARY", "SECONDARY", "INVESTMENT"
    );

    @Override
    public RuleType getRuleType() {
        return RuleType.R05;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        String occupancyType = request.propertyProfile().occupancyType();

        if (occupancyType == null || occupancyType.isBlank()) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R05", "OCCUPANCY_TYPE", "WARNING", "INSUFFICIENT_DATA",
                "OC01", "Occupancy type is missing.",
                null, String.join(", ", ALLOWED_OCCUPANCY),
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        boolean pass = ALLOWED_OCCUPANCY.contains(occupancyType);

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R05", "OCCUPANCY_TYPE",
            pass ? "PASS" : "HARD_STOP",
            pass ? "ELIGIBLE" : "INELIGIBLE",
            pass ? null : "OC01",
            pass ? "Occupancy type is acceptable." : "Occupancy type is not acceptable.",
            occupancyType, String.join(", ", ALLOWED_OCCUPANCY),
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
