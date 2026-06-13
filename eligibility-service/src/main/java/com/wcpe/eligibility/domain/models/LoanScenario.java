package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;

public record LoanScenario(
    Integer fico,
    BigDecimal ltv,
    BigDecimal cltv,
    BigDecimal dti,
    String loanPurpose,
    String propertyType,
    String occupancyType,
    String state,
    String county,
    BigDecimal loanAmount
) {}
