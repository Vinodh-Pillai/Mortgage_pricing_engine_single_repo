package com.wcpe.tenantcontext.ratelimit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TestOnlyInMemoryRateLimitPolicyStore implements RateLimitPolicyStore {
    private final List<RateLimitPolicy> policies = new ArrayList<>();

    @Override
    public synchronized RateLimitPolicy save(RateLimitPolicy policy) {
        for (RateLimitPolicy existing : policies) {
            if (policy.status() == PolicyStatus.ACTIVE
                && existing.status() == PolicyStatus.ACTIVE
                && policy.sameSelector(existing)
                && policy.overlaps(existing)) {
                throw new RateLimitPolicyException(
                    "RATE_LIMIT_POLICY_OVERLAP",
                    "active tenant rate-limit policies cannot overlap for the same selector"
                );
            }
        }
        policies.add(policy);
        return policy;
    }

    @Override
    public synchronized Optional<RateLimitPolicy> activePolicy(
        String tenantId,
        String channel,
        String actorType,
        String endpointGroup,
        Instant at
    ) {
        return policies.stream()
            .filter(policy -> policy.tenantId().equals(trim(tenantId)))
            .filter(policy -> policy.channel().equals(trim(channel)))
            .filter(policy -> policy.actorType().equals(trim(actorType)))
            .filter(policy -> policy.endpointGroup().equals(trim(endpointGroup)))
            .filter(policy -> policy.activeAt(at))
            .findFirst();
    }

    @Override
    public synchronized List<RateLimitPolicy> listByTenant(String tenantId) {
        String normalizedTenant = trim(tenantId);
        return policies.stream()
            .filter(policy -> policy.tenantId().equals(normalizedTenant))
            .toList();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
