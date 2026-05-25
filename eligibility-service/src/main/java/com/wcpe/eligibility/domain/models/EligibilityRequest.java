package com.wcpe.eligibility.domain.models;

public record EligibilityRequest(
    BorrowerProfile borrowerProfile,
    PropertyProfile propertyProfile,
    LoanProfile loanProfile,
    ProductCandidate productCandidate
) {}
