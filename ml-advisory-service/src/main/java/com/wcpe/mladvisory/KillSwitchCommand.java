package com.wcpe.mladvisory;

import java.util.Set;

public record KillSwitchCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    Set<String> actorRoles,
    String reason,
    String correlationId,
    String approvedBy) {}
