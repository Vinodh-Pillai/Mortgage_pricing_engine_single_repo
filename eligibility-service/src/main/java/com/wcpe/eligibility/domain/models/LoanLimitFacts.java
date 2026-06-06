package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;

public record LoanLimitFacts(
    BigDecimal loanAmount,
    String propertyState,
    String propertyCounty,
    Integer units,
    String loanPurpose
) {}
