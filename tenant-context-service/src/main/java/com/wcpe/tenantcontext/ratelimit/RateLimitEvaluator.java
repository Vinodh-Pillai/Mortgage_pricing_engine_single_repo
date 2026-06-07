package com.wcpe.tenantcontext.ratelimit;

import com.wcpe.tenantcontext.TenantContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class RateLimitEvaluator {
    private final RateLimitCounter counter;
    private final RateLimitCounterKeyBuilder keyBuilder;

    public RateLimitEvaluator(RateLimitCounter counter) {
        this(counter, new RateLimitCounterKeyBuilder());
    }

    public RateLimitEvaluator(RateLimitCounter counter, RateLimitCounterKeyBuilder keyBuilder) {
        if (counter == null || keyBuilder == null) {
            throw new RateLimitPolicyException("RATE_LIMIT_EVALUATOR_INVALID", "counter and keyBuilder are required");
        }
        this.counter = counter;
        this.keyBuilder = keyBuilder;
    }

    public RateLimitDecision evaluate(RateLimitPolicy policy, TenantContext context, Instant now) {
        if (context == null) {
            throw new RateLimitPolicyException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        String correlationId = context.request().correlationId();
        if (policy == null) {
            return RateLimitDecision.missingPolicyFailClosed(correlationId);
        }
        if (policy.enforcementMode() == EnforcementMode.DISABLED) {
            return new RateLimitDecision(RateLimitOutcome.DISABLED, "RATE_LIMIT_DISABLED", 200, RateLimitDecision.correlationHeader(correlationId), null, "Rate limiting is disabled by policy.");
        }

        CounterSnapshot snapshot;
        try {
            snapshot = counter.increment(keyBuilder.key(policy, context, now), policy.windowSeconds(), now);
        } catch (RuntimeException error) {
            if (policy.redisUnavailableMode() == RedisUnavailableMode.MONITOR) {
                return new RateLimitDecision(RateLimitOutcome.MONITOR_ONLY, "RATE_LIMIT_COUNTER_UNAVAILABLE", 200, RateLimitDecision.correlationHeader(correlationId), null, "Rate-limit counter unavailable; policy is monitor-only for this path.");
            }
            return new RateLimitDecision(RateLimitOutcome.THROTTLED, "RATE_LIMIT_COUNTER_UNAVAILABLE", 429, RateLimitDecision.correlationHeader(correlationId), null, "Rate-limit counter unavailable.");
        }

        int configuredLimit = policy.maxRequests() + policy.burstCapacity();
        int remaining = Math.max(configuredLimit - snapshot.used(), 0);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-RateLimit-Limit", Integer.toString(configuredLimit));
        headers.put("X-RateLimit-Remaining", Integer.toString(remaining));
        headers.put("X-RateLimit-Reset", Long.toString(snapshot.resetAt().getEpochSecond()));
        if (correlationId != null && !correlationId.isBlank()) {
            headers.put("X-Correlation-ID", correlationId.trim());
        }

        if (policy.enforcementMode() == EnforcementMode.MONITOR) {
            return new RateLimitDecision(RateLimitOutcome.MONITOR_ONLY, "RATE_LIMIT_MONITOR", 200, headers, snapshot.resetAt(), "Rate-limit policy is monitoring only.");
        }
        if (snapshot.used() > configuredLimit) {
            headers.put("Retry-After", Long.toString(Math.max(snapshot.resetAt().getEpochSecond() - (now == null ? Instant.now() : now).getEpochSecond(), 0)));
            return new RateLimitDecision(RateLimitOutcome.THROTTLED, "RATE_LIMIT_EXCEEDED", 429, headers, snapshot.resetAt(), "Rate limit exceeded.");
        }
        return new RateLimitDecision(RateLimitOutcome.ALLOWED, "RATE_LIMIT_ALLOWED", 200, headers, snapshot.resetAt(), "Request is within the configured rate-limit policy.");
    }
}
