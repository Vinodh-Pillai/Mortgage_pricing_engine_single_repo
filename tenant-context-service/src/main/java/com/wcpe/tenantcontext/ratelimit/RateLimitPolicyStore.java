package com.wcpe.tenantcontext.ratelimit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RateLimitPolicyStore {
    RateLimitPolicy save(RateLimitPolicy policy);

    Optional<RateLimitPolicy> activePolicy(
        String tenantId,
        String channel,
        String actorType,
        String endpointGroup,
        Instant at
    );

    List<RateLimitPolicy> listByTenant(String tenantId);
}
