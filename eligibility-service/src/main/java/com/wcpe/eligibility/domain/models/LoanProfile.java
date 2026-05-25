package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;

public record LoanProfile(
    String loanPurpose,
    BigDecimal loanAmount,
    BigDecimal subordinateFinancingAmount,
    int requestedLockPeriodDays,
    String documentationType,
    String ausType,
    int lienPosition
) {}
