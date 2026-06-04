package com.wcpe.governance;

import java.time.Instant;
import java.util.List;

public record ConfigValidationRun(
    String tenantId,
    String runId,
    String artifactId,
    String versionId,
    String runType,
    String status,
    String inputHash,
    String policyVersionSetHash,
    String resultHash,
    Instant startedAt,
    Instant completedAt,
    String actorId,
    String auditRef,
    String replayRef,
    String correlationId,
    boolean publishEligible,
    List<ConfigValidationFinding> findings) {}
