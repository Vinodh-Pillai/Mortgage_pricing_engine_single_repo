package com.wcpe.mladvisory;

import java.time.Instant;

public record KillSwitchState(
    String id,
    String tenantId,
    boolean enabled,
    String reason,
    String activatedBy,
    Instant activatedAt,
    String correlationId,
    String approvedBy) {}
