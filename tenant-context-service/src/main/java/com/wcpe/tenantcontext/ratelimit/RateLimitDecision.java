package com.wcpe.tenantcontext.ratelimit;

import java.time.Instant;
import java.util.Map;

public record RateLimitDecision(
    RateLimitOutcome outcome,
    String code,
    int httpStatus,
    Map<String, String> headers,
    Instant resetAt,
    String message
) {
    public RateLimitDecision {
        if (outcome == null) {
            throw new RateLimitPolicyException("RATE_LIMIT_DECISION_INVALID", "outcome is required");
        }
        code = required(code, "code");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        message = message == null ? "" : message.trim();
    }

    public boolean allowed() {
        return outcome == RateLimitOutcome.ALLOWED
            || outcome == RateLimitOutcome.MONITOR_ONLY
            || outcome == RateLimitOutcome.DISABLED;
    }

    public static RateLimitDecision missingPolicyFailClosed(String correlationId) {
        return new RateLimitDecision(
            RateLimitOutcome.MISSING_POLICY_FAIL_CLOSED,
            "POLICY_NOT_SATISFIED",
            422,
            correlationHeader(correlationId),
            null,
            "No active tenant rate-limit policy is configured."
        );
    }

    static Map<String, String> correlationHeader(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return Map.of();
        }
        return Map.of("X-Correlation-ID", correlationId.trim());
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new RateLimitPolicyException("RATE_LIMIT_DECISION_INVALID", fieldName + " is required");
        }
        return value.trim();
    }
}
