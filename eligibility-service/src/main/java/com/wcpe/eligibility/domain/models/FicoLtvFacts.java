package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;

public record FicoLtvFacts(
    Integer representativeFico,
    BigDecimal ltv,
    BigDecimal cltv,
    String occupancyType,
    String propertyType,
    int units,
    String loanPurpose
) {}
