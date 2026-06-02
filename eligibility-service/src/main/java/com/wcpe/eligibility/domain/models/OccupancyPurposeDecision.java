package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record OccupancyPurposeDecision(
    String ruleCode,
    String eligibilityStatus,
    String severity,
    String reasonCode,
    String message,
    String matchedRuleSetId,
    String matchedRuleId,
    String precedence,
    UUID ruleVersionId
) {}
