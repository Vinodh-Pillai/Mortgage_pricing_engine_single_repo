package com.wcpe.mladvisory;

import java.time.Instant;

public record MlAdvisoryAuditRecord(
    String auditId,
    String tenantId,
    String actorId,
    String action,
    String beforeSummary,
    String afterSummary,
    String correlationId,
    Instant recordedAt) {}
