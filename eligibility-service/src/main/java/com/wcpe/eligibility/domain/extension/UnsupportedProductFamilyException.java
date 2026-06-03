package com.wcpe.eligibility.domain.extension;

import com.wcpe.eligibility.domain.models.ProductFamily;
import com.wcpe.eligibility.domain.models.QuoteType;

public class UnsupportedProductFamilyException extends RuntimeException {
    private final ProductFamily productFamily;
    private final QuoteType quoteType;

    public UnsupportedProductFamilyException(ProductFamily productFamily, QuoteType quoteType) {
        super(productFamily + " " + quoteType + " is not enabled in PII-03.");
        this.productFamily = productFamily;
        this.quoteType = quoteType;
    }

    public ProductFamily productFamily() {
        return productFamily;
    }

    public QuoteType quoteType() {
        return quoteType;
    }
}
