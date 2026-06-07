package com.wcpe.tenantcontext.health;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

public class SmokeCheckService {
    private final Clock clock;

    public SmokeCheckService(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public SmokeCheckResult run(String tenantId, String checkSuite, String correlationId, boolean dryRun, Supplier<HealthStatus> check) {
        String syntheticTenantId = requireSyntheticTenant(tenantId);
        String normalizedSuite = required(checkSuite, "checkSuite");
        Instant startedAt = clock.instant();
        if (dryRun) {
            return result(syntheticTenantId, normalizedSuite, HealthStatus.UP, startedAt, "dry-run smoke check validated synthetic tenant scope", "", correlationId);
        }
        if (check == null) {
            throw new HealthValidationException("SMOKE_CHECK_INVALID", "smoke check callback is required");
        }
        HealthStatus status;
        String summary;
        String errorCode = "";
        try {
            status = check.get();
            if (status == null) {
                status = HealthStatus.DOWN;
                errorCode = "SMOKE_CHECK_FAILED";
            }
            summary = status == HealthStatus.UP ? "smoke check passed" : "smoke check did not pass";
        } catch (RuntimeException error) {
            status = HealthStatus.DOWN;
            summary = "smoke check failed";
            errorCode = "SMOKE_CHECK_FAILED";
        }
        return result(syntheticTenantId, normalizedSuite, status, startedAt, summary, errorCode, correlationId);
    }

    private SmokeCheckResult result(String tenantId, String suite, HealthStatus status, Instant startedAt, String summary, String errorCode, String correlationId) {
        return new SmokeCheckResult(UUID.nameUUIDFromBytes((tenantId + ":" + suite + ":" + startedAt).getBytes()), tenantId, suite, status, startedAt, clock.instant(), 0,
            correlationId, summary, errorCode, "runbooks/tenant-context-service/health#smoke-checks");
    }

    private String requireSyntheticTenant(String tenantId) {
        String normalized = required(tenantId, "tenantId");
        if (!normalized.toLowerCase(Locale.ROOT).startsWith("synthetic-")) {
            throw new HealthValidationException("SMOKE_TENANT_REQUIRED", "smoke checks require a synthetic tenant fixture");
        }
        return normalized;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new HealthValidationException("SMOKE_CHECK_INVALID", fieldName + " is required");
        }
        return value.trim();
    }
}
