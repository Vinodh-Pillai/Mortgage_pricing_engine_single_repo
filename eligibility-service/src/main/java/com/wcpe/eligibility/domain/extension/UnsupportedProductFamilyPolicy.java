package com.wcpe.eligibility.domain.extension;

import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.QuoteType;
import org.springframework.stereotype.Component;

@Component
public class UnsupportedProductFamilyPolicy {
    public void rejectUnsupportedForPii03(EligibilityRequest request) {
        ProductFamily family = request.resolvedProductFamily();
        QuoteType quoteType = request.resolvedQuoteType();
        if (family != ProductFamily.CONVENTIONAL || quoteType != QuoteType.CONVENTIONAL_PURCHASE) {
            throw new UnsupportedProductFamilyException(family, quoteType);
        }
    }
}
