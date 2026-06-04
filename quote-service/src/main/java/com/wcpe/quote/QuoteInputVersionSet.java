package com.wcpe.quote;

import java.util.Map;

public record QuoteInputVersionSet(
    int scenarioVersion,
    String eligibilityVersion,
    String pricingVersion,
    String adjustmentVersion,
    String marginVersion,
    String rankingPolicyVersion
) {
    public Map<String, String> asMap() {
        return Map.of(
            "scenarioVersion", Integer.toString(scenarioVersion),
            "eligibilityVersion", eligibilityVersion,
            "pricingVersion", pricingVersion,
            "adjustmentVersion", adjustmentVersion,
            "marginVersion", marginVersion,
            "rankingPolicyVersion", rankingPolicyVersion
        );
    }
}
