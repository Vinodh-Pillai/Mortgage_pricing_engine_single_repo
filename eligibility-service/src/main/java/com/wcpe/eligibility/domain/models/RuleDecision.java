package com.wcpe.eligibility.domain.models;

import java.util.Map;
import java.util.UUID;

public record RuleDecision(
    UUID decisionId,
    String productCode,
    String investorCode,
    String ruleCode,
    String ruleName,
    String severity,
    String status,
    String reasonCode,
    String message,
    String actualValue,
    String requiredValue,
    Map<String, Object> trace
) {}
