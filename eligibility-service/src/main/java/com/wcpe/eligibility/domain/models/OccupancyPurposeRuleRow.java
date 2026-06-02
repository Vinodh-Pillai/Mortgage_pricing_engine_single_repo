package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record OccupancyPurposeRuleRow(
    UUID ruleId,
    UUID ruleSetId,
    String loanPurpose,
    String occupancyType,
    String propertyType,
    int unitsMin,
    int unitsMax,
    String decision,
    String severity,
    String reasonCode,
    String messageTemplate,
    int priority
) {}
