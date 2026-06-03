package com.wcpe.eligibility.domain.models;

import java.util.List;
import java.util.UUID;

public record InvestorOverlayRuleRow(
    UUID overlayRuleId,
    UUID overlaySetId,
    String ruleCode,
    String factPath,
    String operator,
    String comparisonValue,
    String secondaryValue,
    String valueType,
    List<Condition> conditions,
    String severity,
    String reasonCode,
    String messageTemplate,
    int priority,
    int version
) {
    public record Condition(
        String factPath,
        String operator,
        String comparisonValue,
        String secondaryValue
    ) {}
}
