package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;

public record QuoteSubmissionRequest(
    String quoteType,
    String channel,
    BorrowerInput borrower,
    PropertyInput property,
    LoanInput loan,
    String actorId
) {
    public record BorrowerInput(
        Integer representativeFico,
        BigDecimal monthlyIncome,
        BigDecimal monthlyDebt
    ) {}

    public record PropertyInput(
        String state,
        String county,
        String zip,
        String propertyType,
        int units,
        String occupancyType,
        BigDecimal purchasePrice,
        BigDecimal appraisedValue
    ) {}

    public record LoanInput(
        String loanPurpose,
        BigDecimal loanAmount,
        BigDecimal subordinateFinancingAmount,
        Integer requestedLockPeriodDays,
        String ausType,
        String documentationType
    ) {}
}
