package com.wcpe.eligibility.domain.models;

public record EligibilityRequest(
    BorrowerProfile borrowerProfile,
    PropertyProfile propertyProfile,
    LoanProfile loanProfile,
    ProductCandidate productCandidate,
    ProductFamily productFamily,
    QuoteType quoteType
) {
    public EligibilityRequest(
        BorrowerProfile borrowerProfile,
        PropertyProfile propertyProfile,
        LoanProfile loanProfile,
        ProductCandidate productCandidate
    ) {
        this(borrowerProfile, propertyProfile, loanProfile, productCandidate, null, null);
    }

    public ProductFamily resolvedProductFamily() {
        return productFamily == null ? ProductFamily.CONVENTIONAL : productFamily;
    }

    public QuoteType resolvedQuoteType() {
        return quoteType == null ? QuoteType.CONVENTIONAL_PURCHASE : quoteType;
    }
}
