package com.wcpe.eligibility.cache;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EligibilityCacheHealth(
    UUID tenantId,
    String status,
    boolean redisReachable,
    boolean dbFallbackEnabled,
    Instant lastInvalidationAtUtc,
    List<TrackedNamespace> trackedNamespaces
) {
    public record TrackedNamespace(
        String namespace,
        int ttlSeconds,
        Instant lastHitAtUtc,
        Instant lastMissAtUtc
    ) {}
}
