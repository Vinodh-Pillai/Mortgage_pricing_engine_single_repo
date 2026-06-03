package com.wcpe.eligibility.domain.extension;

import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.QuoteType;

import java.util.List;

public record RegisterEligibilityRuleModuleCommand(
    ProductFamily productFamily,
    List<String> ruleCodes,
    List<QuoteType> supportedQuoteTypes
) {
    public RegisterEligibilityRuleModuleCommand {
        ruleCodes = List.copyOf(ruleCodes);
        supportedQuoteTypes = List.copyOf(supportedQuoteTypes);
    }
}
