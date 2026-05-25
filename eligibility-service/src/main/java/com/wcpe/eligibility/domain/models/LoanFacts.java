package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;

public record LoanFacts(
    String loanPurpose,
    BigDecimal loanAmount,
    BigDecimal subordinateFinancingAmount,
    int requestedLockPeriodDays,
    String ausType,
    String documentationType
) {}
