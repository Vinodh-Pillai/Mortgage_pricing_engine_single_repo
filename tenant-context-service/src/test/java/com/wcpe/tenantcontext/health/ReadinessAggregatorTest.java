package com.wcpe.tenantcontext.health;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

class ReadinessAggregatorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-08T03:25:00Z"), ZoneOffset.UTC);

    @Test
    void livenessIsProcessOnlyAndDoesNotInvokeDependencyProbes() {
        ReadinessAggregator aggregator = new ReadinessAggregator(Map.of(
            ReadinessComponent.POSTGRESQL, () -> { throw new AssertionError("probe should not run for liveness"); }
        ), CLOCK);

        assertThat(aggregator.liveness()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void readinessAggregatesAllNamedComponentsAndRedactsSensitiveDetails() {
        ReadinessAggregator aggregator = new ReadinessAggregator(allProbes(HealthStatus.UP, Map.of(
            ReadinessComponent.REDIS, DependencyStatus.degraded(ReadinessComponent.REDIS, "redis fallback active", "DEPENDENCY_DEGRADED", "runbooks/cache", CLOCK.instant(), Map.of("connectionString", "redis://secret-host"))
        )), CLOCK);

        ReadinessReport report = aggregator.readiness("corr-1");

        assertThat(report.status()).isEqualTo(HealthStatus.DEGRADED);
        assertThat(report.readyForTraffic()).isFalse();
        assertThat(report.components()).hasSize(ReadinessComponent.values().length);
        DependencyStatus redis = status(report, ReadinessComponent.REDIS);
        assertThat(redis.details()).containsEntry("connectionString", "[REDACTED]");
        assertThat(redis.summary()).isEqualTo("redis fallback active");
    }

    @Test
    void downDependencyMakesReadinessDownWithoutLeakingExceptionMessage() {
        ReadinessAggregator aggregator = new ReadinessAggregator(allProbes(HealthStatus.UP, Map.of(
            ReadinessComponent.OUTBOX, (DependencyProbe) () -> { throw new IllegalStateException("password=secret"); }
        )), CLOCK);

        ReadinessReport report = aggregator.readiness("corr-2");

        assertThat(report.status()).isEqualTo(HealthStatus.DOWN);
        assertThat(status(report, ReadinessComponent.OUTBOX).summary()).isEqualTo("readiness probe failed");
        assertThat(status(report, ReadinessComponent.OUTBOX).details()).containsEntry("exception", "IllegalStateException");
        assertThat(status(report, ReadinessComponent.OUTBOX).details().toString()).doesNotContain("password").doesNotContain("secret");
    }

    @Test
    void missingRequiredProbeDegradesReadinessFailClosed() {
        ReadinessAggregator aggregator = new ReadinessAggregator(Map.of(
            ReadinessComponent.POSTGRESQL, () -> DependencyStatus.up(ReadinessComponent.POSTGRESQL, "db reachable", CLOCK.instant(), Map.of())
        ), CLOCK);

        ReadinessReport report = aggregator.readiness("corr-3");

        assertThat(report.status()).isEqualTo(HealthStatus.DEGRADED);
        assertThat(status(report, ReadinessComponent.AUDIT).errorCode()).isEqualTo("HEALTH_CHECK_UNAVAILABLE");
    }

    private Map<ReadinessComponent, DependencyProbe> allProbes(HealthStatus defaultStatus, Map<ReadinessComponent, Object> overrides) {
        return Arrays.stream(ReadinessComponent.values()).collect(Collectors.toMap(component -> component, component -> {
            Object override = overrides.get(component);
            if (override instanceof DependencyProbe probe) {
                return probe;
            }
            if (override instanceof DependencyStatus status) {
                return () -> status;
            }
            return () -> new DependencyStatus(component, defaultStatus, component.name().toLowerCase() + " ok", "", "", CLOCK.instant(), Map.of());
        }));
    }

    private DependencyStatus status(ReadinessReport report, ReadinessComponent component) {
        return report.components().stream()
            .filter(item -> item.component() == component)
            .findFirst()
            .orElseThrow();
    }
}
