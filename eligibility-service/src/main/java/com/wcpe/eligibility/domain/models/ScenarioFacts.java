package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

public record ScenarioFacts(
    UUID scenarioId,
    UUID tenantId,
    String channel,
    String loanPurpose,
    String occupancyType,
    BigDecimal loanAmount,
    BigDecimal purchasePrice,
    BigDecimal appraisedValue,
    BigDecimal subordinateFinancingAmount,
    BigDecimal ltv,
    BigDecimal cltv,
    Integer representativeFico,
    BigDecimal dti,
    String propertyState,
    String propertyCounty,
    String propertyZip,
    String propertyType,
    int units,
    int lockPeriodDays,
    String ausType,
    String documentationType,
    String factQualityStatus,
    UUID createdBy
) {}
