package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record EligibilityDecisionPropertyType(
    UUID decisionId,
    UUID ruleSetId,
    UUID matchedRuleId,
    String propertyType,
    int units,
    String occupancyType,
    String loanPurpose,
    String projectReviewRequirement,
    String eligibilityStatus
) {}
