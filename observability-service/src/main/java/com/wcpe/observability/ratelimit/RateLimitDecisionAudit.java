package com.wcpe.observability.ratelimit;

import java.time.Instant;
import java.util.UUID;

public record RateLimitDecisionAudit(
    UUID tenantId,
    String policyKey,
    int policyVersion,
    String principalHash,
    String endpointGroup,
    RateLimitDecision decision,
    int remaining,
    Instant resetAt,
    String correlationId,
    Instant createdAt) {}
