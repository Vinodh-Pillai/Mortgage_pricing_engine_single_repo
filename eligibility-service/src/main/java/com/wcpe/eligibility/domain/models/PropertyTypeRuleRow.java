package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record PropertyTypeRuleRow(
    UUID ruleId,
    UUID ruleSetId,
    String propertyType,
    int unitsMin,
    int unitsMax,
    String occupancyType,
    String loanPurpose,
    String projectReviewRequirement,
    String decision,
    String severity,
    String reasonCode,
    String messageTemplate,
    int priority
) {}
