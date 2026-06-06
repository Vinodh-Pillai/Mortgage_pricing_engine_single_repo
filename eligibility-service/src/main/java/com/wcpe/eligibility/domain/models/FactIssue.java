package com.wcpe.eligibility.domain.models;

import java.util.UUID;

public record FactIssue(
    UUID factIssueId,
    UUID tenantId,
    UUID scenarioId,
    int scenarioVersion,
    String fieldPath,
    String issueType,
    String severity,
    String actualValue,
    String expectedValue,
    String message,
    String remediationHint
) {}
