package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record PropertyTypeDecision(
    String ruleCode,
    String eligibilityStatus,
    String severity,
    String reasonCode,
    String message,
    UUID ruleVersionId,
    UUID matchedRuleId,
    String projectReviewRequirement,
    UUID ruleSetId
) {}
