package com.wcpe.eligibility.domain.models;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditPackage(
    UUID auditPackageId,
    UUID tenantId,
    UUID aggregateId,
    String actorId,
    String correlationId,
    String causationId,
    String requestHash,
    String resultHash,
    Integer scenarioVersion,
    List<String> ruleVersions,
    Instant createdAt
) {}
