package com.wcpe.eligibility.domain.rules;

import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.RuleDecision;
import java.util.Map;
import java.util.UUID;

public class ProductRule implements EligibilityRule {
    private final CatalogClient catalogClient;

    public ProductRule(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @Override
    public RuleType getRuleType() {
        return RuleType.R08;
    }

    @Override
    public RuleDecision evaluate(EligibilityRequest request, String productCode, String investorCode) {
        boolean supportsInvestor = catalogClient.doesProductSupportInvestor(productCode, investorCode);

        return new RuleDecision(
            UUID.randomUUID(), productCode, investorCode,
            "R08", "PRODUCT_INVESTOR",
            supportsInvestor ? "PASS" : "HARD_STOP",
            supportsInvestor ? "ELIGIBLE" : "INELIGIBLE",
            supportsInvestor ? null : "PR01",
            supportsInvestor ? "Product supports the investor." : "Product does not support the investor.",
            String.valueOf(supportsInvestor), "true",
            Map.of("ruleSetVersion", 3, "deterministic", true)
        );
    }
}
