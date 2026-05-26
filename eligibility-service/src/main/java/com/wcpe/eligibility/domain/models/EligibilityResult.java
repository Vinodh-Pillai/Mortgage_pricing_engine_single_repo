package com.wcpe.eligibility.domain.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EligibilityResult(
    UUID evaluationId,
    String tenantId,
    String scenarioId,
    String status,
    List<RuleDecision> decisions,
    String resultHash,
    String requestHash,
    String productCatalogVersion,
    String eligibilityRuleSetVersion,
    String fixtureVersion,
    Instant evaluatedAt
) {}
