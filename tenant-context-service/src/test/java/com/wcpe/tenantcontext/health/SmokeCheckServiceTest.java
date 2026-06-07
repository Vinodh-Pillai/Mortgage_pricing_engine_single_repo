package com.wcpe.tenantcontext.health;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class SmokeCheckServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-08T03:30:00Z"), ZoneOffset.UTC);
    private final SmokeCheckService service = new SmokeCheckService(CLOCK);

    @Test
    void dryRunAcceptsOnlySyntheticTenantFixture() {
        SmokeCheckResult result = service.run("synthetic-tenant-alpha", "readiness", "corr-1", true, null);

        assertThat(result.status()).isEqualTo(HealthStatus.UP);
        assertThat(result.syntheticTenantId()).isEqualTo("synthetic-tenant-alpha");
        assertThat(result.summary()).contains("synthetic tenant");
    }

    @Test
    void rejectsCustomerTenantForSmokeChecks() {
        assertThatThrownBy(() -> service.run("tenant-alpha", "readiness", "corr-2", true, null))
            .isInstanceOf(HealthValidationException.class)
            .extracting(error -> ((HealthValidationException) error).code())
            .isEqualTo("SMOKE_TENANT_REQUIRED");
    }

    @Test
    void failedCallbackRecordsDownResultWithoutThrowingRawFailure() {
        SmokeCheckResult result = service.run("synthetic-tenant-alpha", "readiness", "corr-3", false, () -> {
            throw new IllegalStateException("broker credential leaked");
        });

        assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
        assertThat(result.errorCode()).isEqualTo("SMOKE_CHECK_FAILED");
        assertThat(result.summary()).isEqualTo("smoke check failed");
        assertThat(result.summary()).doesNotContain("credential");
    }
}
