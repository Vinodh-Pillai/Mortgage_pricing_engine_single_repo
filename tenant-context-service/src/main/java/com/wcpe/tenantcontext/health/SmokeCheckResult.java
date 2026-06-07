package com.wcpe.tenantcontext.health;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record SmokeCheckResult(
    UUID checkId,
    String syntheticTenantId,
    String checkSuite,
    HealthStatus status,
    Instant startedAt,
    Instant completedAt,
    long durationMs,
    String correlationId,
    String summary,
    String errorCode,
    String runbookUrl
) {
    public SmokeCheckResult {
        if (checkId == null) {
            throw new HealthValidationException("SMOKE_CHECK_INVALID", "checkId is required");
        }
        syntheticTenantId = required(syntheticTenantId, "syntheticTenantId");
        checkSuite = required(checkSuite, "checkSuite");
        if (status == null) {
            throw new HealthValidationException("SMOKE_CHECK_INVALID", "status is required");
        }
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)) {
            throw new HealthValidationException("SMOKE_CHECK_INVALID", "valid smoke check timestamps are required");
        }
        durationMs = Duration.between(startedAt, completedAt).toMillis();
        correlationId = correlationId == null ? "" : correlationId.trim();
        summary = summary == null ? "" : summary.trim();
        errorCode = errorCode == null ? "" : errorCode.trim();
        runbookUrl = runbookUrl == null ? "" : runbookUrl.trim();
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new HealthValidationException("SMOKE_CHECK_INVALID", fieldName + " is required");
        }
        return value.trim();
    }
}
