package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;

public record PropertyProfile(
    String state,
    String county,
    String zip,
    String propertyType,
    int units,
    String occupancyType,
    BigDecimal purchasePrice,
    BigDecimal appraisedValue
) {}
