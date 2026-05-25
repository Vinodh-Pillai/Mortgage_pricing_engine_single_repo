package com.wcpe.eligibility.domain.models;

public record ReasonCode(
    String code,
    String ruleCode,
    String severity,
    String category,
    String message,
    String description
) {}
