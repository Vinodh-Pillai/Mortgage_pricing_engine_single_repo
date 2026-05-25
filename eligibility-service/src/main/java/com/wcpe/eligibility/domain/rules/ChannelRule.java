package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.util.Map;
import java.util.UUID;

public class ChannelRule implements EligibilityRule {
    private final CatalogClient catalogClient;

    public ChannelRule(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @Override
    public RuleType getRuleType() {
        return RuleType.R09;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        String channel = catalogClient.resolveChannel(request);
        boolean allowed = catalogClient.isChannelAllowed(productCode, channel);

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R09", "CHANNEL_ALLOWED",
            allowed ? "PASS" : "HARD_STOP",
            allowed ? "ELIGIBLE" : "INELIGIBLE",
            allowed ? null : "CH01",
            allowed ? "Channel is allowed by product." : "Channel is not allowed by product.",
            channel, "RETAIL,BROKER",
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
