package com.wcpe.tenantcontext.ratelimit;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class RateLimitEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-06-07T12:00:00Z");

    @Test
    void allowsWithinConfiguredLimitAndEmitsSafeRateHeaders() {
        RateLimitEvaluator evaluator = new RateLimitEvaluator(new InMemoryRateLimitCounter());

        RateLimitDecision decision = evaluator.evaluate(RateLimitPolicyResolverTest.policy("tenant-alpha", NOW.minusSeconds(60), NOW.plusSeconds(60)), RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.headers()).containsEntry("X-RateLimit-Limit", "3");
        assertThat(decision.headers()).containsEntry("X-RateLimit-Remaining", "2");
        assertThat(decision.headers()).containsEntry("X-Correlation-ID", "correlation-1");
    }

    @Test
    void returns429ProblemDetailWhenConfiguredLimitIsExceeded() {
        RateLimitEvaluator evaluator = new RateLimitEvaluator(new InMemoryRateLimitCounter());
        RateLimitPolicy policy = RateLimitPolicyResolverTest.policy("tenant-alpha", NOW.minusSeconds(60), NOW.plusSeconds(60));

        evaluator.evaluate(policy, RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);
        evaluator.evaluate(policy, RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);
        evaluator.evaluate(policy, RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);
        RateLimitDecision throttled = evaluator.evaluate(policy, RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);

        assertThat(throttled.allowed()).isFalse();
        assertThat(throttled.httpStatus()).isEqualTo(429);
        assertThat(throttled.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(throttled.headers()).containsKeys("Retry-After", "X-RateLimit-Reset");
    }

    @Test
    void counterKeyIncludesTenantActorEndpointAndPolicyVersion() {
        RateLimitCounterKeyBuilder keyBuilder = new RateLimitCounterKeyBuilder();
        RateLimitPolicy policy = RateLimitPolicyResolverTest.policy("tenant-alpha", NOW.minusSeconds(60), NOW.plusSeconds(60));

        String alphaKey = keyBuilder.key(policy, RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);
        String betaKey = keyBuilder.key(RateLimitPolicyResolverTest.policy("tenant-beta", NOW.minusSeconds(60), NOW.plusSeconds(60)), RateLimitPolicyResolverTest.context("tenant-beta"), NOW);

        assertThat(alphaKey).contains("tenant:tenant-alpha:rate-limit:v1").contains(":endpoint:quote-api:");
        assertThat(betaKey).contains("tenant:tenant-beta:rate-limit:v1");
        assertThat(alphaKey).isNotEqualTo(betaKey);
        assertThat(alphaKey).doesNotContain("actor-1");
    }

    @Test
    void missingPolicyFailsClosedWithoutInventingDefaults() {
        RateLimitEvaluator evaluator = new RateLimitEvaluator(new InMemoryRateLimitCounter());

        RateLimitDecision decision = evaluator.evaluate(null, RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.httpStatus()).isEqualTo(422);
        assertThat(decision.code()).isEqualTo("POLICY_NOT_SATISFIED");
    }

    @Test
    void redisUnavailableFollowsExplicitPolicyMode() {
        RateLimitCounter failingCounter = (key, windowSeconds, now) -> {
            throw new IllegalStateException("redis unavailable");
        };
        RateLimitEvaluator evaluator = new RateLimitEvaluator(failingCounter);

        RateLimitDecision failClosed = evaluator.evaluate(RateLimitPolicyResolverTest.policy("tenant-alpha", NOW.minusSeconds(60), NOW.plusSeconds(60)), RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);
        RateLimitDecision monitor = evaluator.evaluate(monitorPolicy(), RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);

        assertThat(failClosed.allowed()).isFalse();
        assertThat(failClosed.httpStatus()).isEqualTo(429);
        assertThat(monitor.allowed()).isTrue();
        assertThat(monitor.outcome()).isEqualTo(RateLimitOutcome.MONITOR_ONLY);
    }

    @Test
    void monitorModeDoesNotThrottleButStillRecordsCounterUse() {
        List<String> keys = new ArrayList<>();
        RateLimitCounter counter = (key, windowSeconds, now) -> {
            keys.add(key);
            return new CounterSnapshot(99, now.plusSeconds(windowSeconds));
        };
        RateLimitEvaluator evaluator = new RateLimitEvaluator(counter);

        RateLimitDecision decision = evaluator.evaluate(monitorPolicy(), RateLimitPolicyResolverTest.context("tenant-alpha"), NOW);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.outcome()).isEqualTo(RateLimitOutcome.MONITOR_ONLY);
        assertThat(keys).hasSize(1);
    }

    private static RateLimitPolicy monitorPolicy() {
        RateLimitPolicy base = RateLimitPolicyResolverTest.policy("tenant-alpha", NOW.minusSeconds(60), NOW.plusSeconds(60));
        return new RateLimitPolicy(
            base.tenantId(), base.policyId(), base.version(), base.channel(), base.actorType(), base.endpointGroup(),
            base.windowSeconds(), base.maxRequests(), base.burstCapacity(), EnforcementMode.MONITOR, base.status(),
            base.effectiveFrom(), base.effectiveTo(), base.createdBy(), base.approvedBy(), base.reasonCode(),
            RedisUnavailableMode.MONITOR
        );
    }
}
