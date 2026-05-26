package com.wcpe.eligibility.domain.models;

/**
 * Actual value captured during rule evaluation for audit/replay.
 */
public record RuleActualValue(
    String ruleCode,
    String evaluatedField,
    String actualValue,
    String expectedValue
) {}
