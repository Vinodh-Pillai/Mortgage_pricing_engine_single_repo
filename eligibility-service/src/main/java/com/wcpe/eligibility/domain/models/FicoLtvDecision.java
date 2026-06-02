package com.wcpe.eligibility.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

public record FicoLtvDecision(
    String ruleCode,
    String eligibilityStatus,
    String severity,
    String reasonCode,
    String message,
    UUID matchedRowId,
    BigDecimal maxLtv,
    BigDecimal maxCltv,
    UUID ruleVersionId
) {}
