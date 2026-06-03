package com.wcpe.eligibility.cache;

import com.wcpe.eligibility.domain.models.EligibilityResult;

import java.time.Instant;

public record CachedEligibilityDecision(
    int schemaVersion,
    String tenantId,
    String ruleVersionGraphHash,
    String payloadHash,
    Instant createdAtUtc,
    Instant expiresAtUtc,
    EligibilityResult result
) {}
