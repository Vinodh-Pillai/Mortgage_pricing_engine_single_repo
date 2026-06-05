package com.wcpe.governance;

import java.time.Instant;

public record LifecycleTransition(
    String transitionId,
    String tenantId,
    String artifactId,
    String versionId,
    ConfigLifecycleState fromStatus,
    ConfigLifecycleState toStatus,
    ConfigLifecycleAction action,
    String reasonCode,
    String actorId,
    String correlationId,
    Instant createdAt,
    String policyVersionId) {}
