package com.wcpe.tenantcontext.ratelimit;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.ActorRef;
import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

class RateLimitPolicyResolverTest {
    private static final Instant NOW = Instant.parse("2026-06-07T12:00:00Z");

    @Test
    void resolvesActiveTenantActorChannelEndpointPolicy() {
        InMemoryRateLimitPolicyStore store = new InMemoryRateLimitPolicyStore();
        RateLimitPolicy policy = policy("tenant-alpha", NOW.minusSeconds(60), NOW.plusSeconds(60));
        store.save(policy);

        RateLimitPolicyResolver resolver = new RateLimitPolicyResolver(store);

        assertThat(resolver.resolve(context("tenant-alpha"), "quote-api", NOW)).contains(policy);
    }

    @Test
    void rejectsOverlappingActivePoliciesForSameTenantSelector() {
        InMemoryRateLimitPolicyStore store = new InMemoryRateLimitPolicyStore();
        store.save(policy("tenant-alpha", NOW.minusSeconds(60), NOW.plusSeconds(60)));

        assertThatThrownBy(() -> store.save(policy("tenant-alpha", NOW.minusSeconds(30), NOW.plusSeconds(120))))
            .isInstanceOf(RateLimitPolicyException.class)
            .extracting(error -> ((RateLimitPolicyException) error).code())
            .isEqualTo("RATE_LIMIT_POLICY_OVERLAP");
    }

    @Test
    void allowsSameEndpointPolicyForDifferentTenantWithoutCrossTenantVisibility() {
        InMemoryRateLimitPolicyStore store = new InMemoryRateLimitPolicyStore();
        RateLimitPolicy alpha = policy("tenant-alpha", NOW.minusSeconds(60), NOW.plusSeconds(60));
        RateLimitPolicy beta = policy("tenant-beta", NOW.minusSeconds(60), NOW.plusSeconds(60));
        store.save(alpha);
        store.save(beta);

        assertThat(store.listByTenant("tenant-alpha")).containsExactly(alpha);
        assertThat(store.listByTenant("tenant-beta")).containsExactly(beta);
    }

    @Test
    void failsClosedWhenPolicyLifecycleLacksApprovalSeparation() {
        assertThatThrownBy(() -> new RateLimitPolicy(
            "tenant-alpha", UUID.randomUUID(), 1, "partner-api", "SERVICE_ACCOUNT", "quote-api",
            60, 10, 0, EnforcementMode.ENFORCE, PolicyStatus.ACTIVE, NOW, null,
            "creator", "creator", "load-shed", RedisUnavailableMode.FAIL_CLOSED))
            .isInstanceOf(RateLimitPolicyException.class)
            .extracting(error -> ((RateLimitPolicyException) error).code())
            .isEqualTo("RATE_LIMIT_POLICY_INVALID");
    }

    static RateLimitPolicy policy(String tenantId, Instant from, Instant to) {
        return new RateLimitPolicy(
            tenantId,
            UUID.randomUUID(),
            1,
            "partner-api",
            "SERVICE_ACCOUNT",
            "quote-api",
            60,
            2,
            1,
            EnforcementMode.ENFORCE,
            PolicyStatus.ACTIVE,
            from,
            to,
            "creator",
            "approver",
            "load-shed",
            RedisUnavailableMode.FAIL_CLOSED
        );
    }

    static TenantContext context(String tenantId) {
        return new TenantContext(
            tenantId,
            new RequestContext("request-1", "trace-1", "correlation-1", "cause-1", "idem-1", "partner-api"),
            new ActorRef("actor-1", "SERVICE_ACCOUNT"),
            List.of("service"),
            List.of("rate-limit:read"),
            "partner-api"
        );
    }
}
