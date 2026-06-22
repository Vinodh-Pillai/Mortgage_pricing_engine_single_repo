package com.wcpe.tenantcontext.ratelimit;

import com.wcpe.tenantcontext.TenantContext;

import java.time.Instant;
import java.util.Optional;

public class RateLimitPolicyResolver {
    private final RateLimitPolicyStore store;

    public RateLimitPolicyResolver(RateLimitPolicyStore store) {
        if (store == null) {
            throw new RateLimitPolicyException("RATE_LIMIT_POLICY_INVALID", "policy store is required");
        }
        this.store = store;
    }

    public Optional<RateLimitPolicy> resolve(TenantContext context, String endpointGroup, Instant at) {
        if (context == null) {
            throw new RateLimitPolicyException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        return store.activePolicy(
            context.tenantId(),
            context.channel(),
            context.actor().actorType(),
            endpointGroup,
            at
        );
    }
}
