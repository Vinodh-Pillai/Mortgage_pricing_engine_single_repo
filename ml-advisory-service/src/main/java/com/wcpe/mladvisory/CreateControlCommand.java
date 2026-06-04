package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.Set;

public record CreateControlCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    Set<String> actorRoles,
    String channel,
    String productFamily,
    AdvisoryType advisoryType,
    AdvisoryMode mode,
    Instant effectiveFrom,
    Instant effectiveTo,
    String changeReason,
    String modelRiskTicket,
    String modelVersionStatus,
    String approvedBy,
    String approvalRef,
    String correlationId) {}
