package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InvestorEligibilityMatrixRow(
    UUID id,
    String loanPurpose,
    String propertyType,
    String occupancyType,
    Integer minFico,
    Integer maxFico,
    BigDecimal maxLtv,
    BigDecimal maxCltv,
    BigDecimal maxDti,
    BigDecimal minLoanAmount,
    BigDecimal maxLoanAmount,
    List<String> allowedStates,
    List<String> excludedCounties,
    Map<String, Object> overlays,
    LocalDate effectiveDate,
    LocalDate expirationDate,
    boolean active
) {}
