package com.wcpe.eligibility.domain.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EligibilityResult(
    UUID evaluationId,
    String tenantId,
    String status,
    List<RuleDecision> decisions,
    String resultHash,
    String requestHash,
    Instant evaluatedAt
) {}
