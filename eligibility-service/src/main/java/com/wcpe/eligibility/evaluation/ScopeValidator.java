package com.wcpe.eligibility.evaluation;

import com.wcpe.eligibility.client.CatalogClient;
import com.wcpe.eligibility.domain.models.EligibilityStatus;
import com.wcpe.eligibility.domain.models.ReasonCode;
import com.wcpe.eligibility.domain.models.ReasonSeverity;
import com.wcpe.eligibility.domain.models.RuleActualValue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that the candidate product/channel/investor/effective window
 * is supported before any rule evaluation occurs.
 * Returns OUT_OF_SCOPE or CANNOT_DECIDE before eligibility rules run.
 */
@Component
public class ScopeValidator {

    private final CatalogClient catalogClient;

    public ScopeValidator(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    /**
     * Returns null if scope is valid, or a list of blocking reasons if scope is invalid.
     */
    public List<ScopeViolation> validate(String productCode, String investorCode, String channelCode,
                                          String state, String productFamily) {
        List<ScopeViolation> violations = new ArrayList<>();

        // Product must exist in catalog
        if (productCode == null || productCode.isBlank()) {
            violations.add(new ScopeViolation(
                EligibilityStatus.CANNOT_DECIDE,
                new ReasonCode("SCOPE_PRODUCT_MISSING", "R08", ReasonSeverity.BLOCKING.name(), "SCOPE",
                    "Product code is missing.", "Product code is required for scope validation."),
                null));
        }

        // Investor must exist. Activity is evaluated by R07 so golden fixtures
        // still receive the complete twelve-rule decision set.
        if (investorCode != null && !investorCode.isBlank()) {
            String investorStatus = catalogClient.getInvestorStatus(investorCode);
            if (investorStatus == null) {
                violations.add(new ScopeViolation(
                    EligibilityStatus.OUT_OF_SCOPE,
                    new ReasonCode("SCOPE_INVESTOR_UNKNOWN", "R07", ReasonSeverity.BLOCKING.name(), "SCOPE",
                        "Investor code not found in catalog.", "The investor code is not in the product catalog."),
                    null));
            }
        } else {
            violations.add(new ScopeViolation(
                EligibilityStatus.CANNOT_DECIDE,
                new ReasonCode("SCOPE_INVESTOR_MISSING", "R07", ReasonSeverity.BLOCKING.name(), "SCOPE",
                    "Investor code is missing.", "Investor code is required for scope validation."),
                null));
        }

        // Channel must be allowed by product
        if (productCode != null && !productCode.isBlank()) {
            boolean channelAllowed = catalogClient.isChannelAllowed(productCode, channelCode != null ? channelCode : "RETAIL");
            if (!channelAllowed) {
                violations.add(new ScopeViolation(
                    EligibilityStatus.OUT_OF_SCOPE,
                    new ReasonCode("SCOPE_CHANNEL_NOT_ALLOWED", "R09", ReasonSeverity.BLOCKING.name(), "SCOPE",
                        "Channel is not allowed by product.", "The channel is not supported for this product."),
                    new RuleActualValue("R09", "channel", channelCode, catalogClient.isChannelAllowed(productCode, "RETAIL") ? "RETAIL" : "")));
            }
        }

        // State allowability is evaluated by R10 so rule output remains complete.

        return violations;
    }

    public record ScopeViolation(
        EligibilityStatus status,
        ReasonCode reasonCode,
        RuleActualValue actualValue
    ) {}
}
