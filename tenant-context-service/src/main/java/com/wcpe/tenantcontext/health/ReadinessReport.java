package com.wcpe.tenantcontext.health;

import java.time.Instant;
import java.util.List;

public record ReadinessReport(
    HealthStatus status,
    Instant checkedAt,
    List<DependencyStatus> components,
    String correlationId
) {
    public ReadinessReport {
        if (status == null) {
            throw new HealthValidationException("HEALTH_STATUS_REQUIRED", "readiness status is required");
        }
        components = components == null ? List.of() : List.copyOf(components);
        correlationId = correlationId == null ? "" : correlationId.trim();
    }

    public boolean readyForTraffic() {
        return status == HealthStatus.UP;
    }
}
