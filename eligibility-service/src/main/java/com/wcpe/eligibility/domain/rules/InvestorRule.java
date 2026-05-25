package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.util.Map;
import java.util.UUID;

public class InvestorRule implements EligibilityRule {
    private final CatalogClient catalogClient;

    public InvestorRule(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @Override
    public RuleType getRuleType() {
        return RuleType.R07;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        String status = catalogClient.getInvestorStatus(investorCode);

        if (status == null) {
            return new RuleDecision(
                UUID.randomUUID(), productCode, investorCode,
                "R07", "INVESTOR_STATUS", "WARNING", "INSUFFICIENT_DATA",
                "IV01", "Investor code not found in catalog.",
                investorCode, "ACTIVE",
                Map.of("ruleSetVersion", 3, "deterministic", true)
            );
        }

        boolean pass = "ACTIVE".equals(status);

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R07", "INVESTOR_STATUS",
            pass ? "PASS" : "HARD_STOP",
            pass ? "ELIGIBLE" : "INELIGIBLE",
            pass ? null : "IV01",
            pass ? "Investor is active." : "Investor is not active.",
            status, "ACTIVE",
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
