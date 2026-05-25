package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.util.Map;
import java.util.UUID;

public class StateRule implements EligibilityRule {
    private final CatalogClient catalogClient;

    public StateRule(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @Override
    public RuleType getRuleType() {
        return RuleType.R10;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        String state = request.propertyProfile().state();
        boolean allowed = catalogClient.isStateAllowed(productCode, state);

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R10", "STATE_ALLOWED",
            allowed ? "PASS" : "HARD_STOP",
            allowed ? "ELIGIBLE" : "INELIGIBLE",
            allowed ? null : "ST01",
            allowed ? "State is allowed by product." : "State is not allowed by product.",
            state, String.join(", ", catalogClient.getAllowedStates(productCode)),
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
