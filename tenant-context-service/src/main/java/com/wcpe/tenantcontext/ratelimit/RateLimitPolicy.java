package com.wcpe.tenantcontext.ratelimit;

import java.time.Instant;
import java.util.UUID;

public record RateLimitPolicy(
    String tenantId,
    UUID policyId,
    int version,
    String channel,
    String actorType,
    String endpointGroup,
    int windowSeconds,
    int maxRequests,
    int burstCapacity,
    EnforcementMode enforcementMode,
    PolicyStatus status,
    Instant effectiveFrom,
    Instant effectiveTo,
    String createdBy,
    String approvedBy,
    String reasonCode,
    RedisUnavailableMode redisUnavailableMode
) {
    public RateLimitPolicy {
        tenantId = required(tenantId, "tenantId");
        if (policyId == null) {
            throw invalid("policyId is required");
        }
        if (version < 1) {
            throw invalid("version must be positive");
        }
        channel = required(channel, "channel");
        actorType = required(actorType, "actorType");
        endpointGroup = required(endpointGroup, "endpointGroup");
        if (windowSeconds < 1 || maxRequests < 1 || burstCapacity < 0) {
            throw invalid("windowSeconds and maxRequests must be positive and burstCapacity cannot be negative");
        }
        enforcementMode = enforcementMode == null ? EnforcementMode.ENFORCE : enforcementMode;
        status = status == null ? PolicyStatus.DRAFT : status;
        if (effectiveFrom == null) {
            throw invalid("effectiveFrom is required");
        }
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw invalid("effectiveTo must be after effectiveFrom");
        }
        createdBy = required(createdBy, "createdBy");
        approvedBy = trim(approvedBy);
        reasonCode = required(reasonCode, "reasonCode");
        redisUnavailableMode = redisUnavailableMode == null ? RedisUnavailableMode.FAIL_CLOSED : redisUnavailableMode;
        if ((status == PolicyStatus.APPROVED || status == PolicyStatus.ACTIVE) && approvedBy.isBlank()) {
            throw invalid("approvedBy is required for approved or active policies");
        }
        if (!approvedBy.isBlank() && createdBy.equals(approvedBy)) {
            throw invalid("creator cannot approve their own rate-limit policy");
        }
    }

    boolean activeAt(Instant at) {
        Instant when = at == null ? Instant.now() : at;
        return status == PolicyStatus.ACTIVE
            && !when.isBefore(effectiveFrom)
            && (effectiveTo == null || when.isBefore(effectiveTo));
    }

    boolean sameSelector(RateLimitPolicy other) {
        return tenantId.equals(other.tenantId)
            && channel.equals(other.channel)
            && actorType.equals(other.actorType)
            && endpointGroup.equals(other.endpointGroup);
    }

    boolean overlaps(RateLimitPolicy other) {
        Instant thisEnd = effectiveTo == null ? Instant.MAX : effectiveTo;
        Instant otherEnd = other.effectiveTo == null ? Instant.MAX : other.effectiveTo;
        return effectiveFrom.isBefore(otherEnd) && other.effectiveFrom.isBefore(thisEnd);
    }

    private static String required(String value, String fieldName) {
        String trimmed = trim(value);
        if (trimmed.isBlank()) {
            throw invalid(fieldName + " is required");
        }
        return trimmed;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static RateLimitPolicyException invalid(String message) {
        return new RateLimitPolicyException("RATE_LIMIT_POLICY_INVALID", message);
    }
}
