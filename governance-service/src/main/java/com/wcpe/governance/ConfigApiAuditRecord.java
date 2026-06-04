package com.wcpe.governance;

import java.time.Instant;

public record ConfigApiAuditRecord(
    String auditId,
    String tenantId,
    String artifactId,
    String versionId,
    String actorId,
    String action,
    String beforeHash,
    String afterHash,
    String correlationId,
    Instant occurredAt) {}
