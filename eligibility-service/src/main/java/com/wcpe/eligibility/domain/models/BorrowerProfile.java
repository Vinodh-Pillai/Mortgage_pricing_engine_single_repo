package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;

public record BorrowerProfile(
    Integer representativeFico,
    BigDecimal monthlyIncome,
    BigDecimal monthlyDebt
) {}
