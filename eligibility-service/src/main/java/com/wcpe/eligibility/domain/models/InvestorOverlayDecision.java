package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record InvestorOverlayDecision(
    UUID overlayRuleId,
    String ruleCode,
    String eligibilityStatus,
    String severity,
    String reasonCode,
    String message,
    String actualValue,
    String thresholdValue,
    UUID ruleVersionId
) {}
