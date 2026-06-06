package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

public record LoanLimitDecision(
    UUID productVersionId,
    String productCode,
    String investorCode,
    String ruleCode,
    String eligibilityStatus,
    String severity,
    String reasonCode,
    String message,
    BigDecimal limitAmount,
    BigDecimal actualAmount,
    UUID matchedRowId,
    UUID ruleVersionId
) {}
