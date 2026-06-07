package com.wcpe.tenantcontext.health;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ReadinessAggregator {
    private final Map<ReadinessComponent, DependencyProbe> probes;
    private final Clock clock;
    private final HealthRedactionPolicy redactionPolicy;

    public ReadinessAggregator(Map<ReadinessComponent, DependencyProbe> probes, Clock clock) {
        this(probes, clock, new HealthRedactionPolicy());
    }

    public ReadinessAggregator(Map<ReadinessComponent, DependencyProbe> probes, Clock clock, HealthRedactionPolicy redactionPolicy) {
        if (probes == null || probes.isEmpty()) {
            throw new HealthValidationException("HEALTH_PROBES_REQUIRED", "at least one readiness probe is required");
        }
        this.probes = new EnumMap<>(ReadinessComponent.class);
        probes.forEach((component, probe) -> {
            if (component == null || probe == null) {
                throw new HealthValidationException("HEALTH_PROBES_REQUIRED", "readiness probe components cannot be null");
            }
            this.probes.put(component, probe);
        });
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.redactionPolicy = redactionPolicy == null ? new HealthRedactionPolicy() : redactionPolicy;
    }

    public HealthStatus liveness() {
        return HealthStatus.UP;
    }

    public ReadinessReport readiness(String correlationId) {
        Instant checkedAt = clock.instant();
        List<DependencyStatus> statuses = new ArrayList<>();
        for (ReadinessComponent component : ReadinessComponent.values()) {
            DependencyProbe probe = probes.get(component);
            if (probe == null) {
                statuses.add(DependencyStatus.degraded(component, "readiness probe not configured", "HEALTH_CHECK_UNAVAILABLE", runbook(component), checkedAt, Map.of()));
                continue;
            }
            try {
                statuses.add(redactionPolicy.redact(probe.check()));
            } catch (RuntimeException error) {
                statuses.add(DependencyStatus.down(component, "readiness probe failed", "HEALTH_CHECK_UNAVAILABLE", runbook(component), checkedAt, Map.of("exception", error.getClass().getSimpleName())));
            }
        }
        return new ReadinessReport(overall(statuses), checkedAt, statuses, redactionPolicy.redactText(correlationId));
    }

    private HealthStatus overall(List<DependencyStatus> statuses) {
        if (statuses.stream().anyMatch(status -> status.status() == HealthStatus.DOWN)) {
            return HealthStatus.DOWN;
        }
        if (statuses.stream().anyMatch(status -> status.status() == HealthStatus.DEGRADED)) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.UP;
    }

    private String runbook(ReadinessComponent component) {
        return "runbooks/tenant-context-service/health#" + component.name().toLowerCase().replace('_', '-');
    }
}
