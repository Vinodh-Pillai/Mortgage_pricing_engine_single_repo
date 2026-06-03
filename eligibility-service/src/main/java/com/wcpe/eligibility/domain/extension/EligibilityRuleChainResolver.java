package com.wcpe.eligibility.domain.extension;

import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.QuoteType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EligibilityRuleChainResolver {
    private static final List<String> CONVENTIONAL_CHAIN = List.of(
        "loan-limit",
        "occupancy-purpose",
        "property-type",
        "fico-ltv",
        "investor-overlays",
        "cache-explain-hooks"
    );

    public List<String> resolve(ResolveEligibilityRuleChainCommand command) {
        if (command.productFamily() == ProductFamily.CONVENTIONAL
            && command.quoteType() == QuoteType.CONVENTIONAL_PURCHASE) {
            return CONVENTIONAL_CHAIN;
        }
        throw new UnsupportedProductFamilyException(command.productFamily(), command.quoteType());
    }
}
