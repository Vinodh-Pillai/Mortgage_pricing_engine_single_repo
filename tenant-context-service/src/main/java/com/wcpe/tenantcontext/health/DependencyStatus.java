package com.wcpe.tenantcontext.health;

import java.time.Instant;
import java.util.Map;

public record DependencyStatus(
    ReadinessComponent component,
    HealthStatus status,
    String summary,
    String errorCode,
    String runbookUrl,
    Instant checkedAt,
    Map<String, String> details
) {
    public DependencyStatus {
        if (component == null) {
            throw new HealthValidationException("HEALTH_COMPONENT_REQUIRED", "component is required");
        }
        if (status == null) {
            throw new HealthValidationException("HEALTH_STATUS_REQUIRED", "status is required");
        }
        summary = trim(summary);
        errorCode = trim(errorCode);
        runbookUrl = trim(runbookUrl);
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static DependencyStatus up(ReadinessComponent component, String summary, Instant checkedAt, Map<String, String> details) {
        return new DependencyStatus(component, HealthStatus.UP, summary, "", "", checkedAt, details);
    }

    public static DependencyStatus degraded(ReadinessComponent component, String summary, String errorCode, String runbookUrl, Instant checkedAt, Map<String, String> details) {
        return new DependencyStatus(component, HealthStatus.DEGRADED, summary, errorCode, runbookUrl, checkedAt, details);
    }

    public static DependencyStatus down(ReadinessComponent component, String summary, String errorCode, String runbookUrl, Instant checkedAt, Map<String, String> details) {
        return new DependencyStatus(component, HealthStatus.DOWN, summary, errorCode, runbookUrl, checkedAt, details);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
