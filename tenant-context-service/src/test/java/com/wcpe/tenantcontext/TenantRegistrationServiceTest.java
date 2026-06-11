package com.wcpe.tenantcontext;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.TenantRegistrationService.TenantDetails;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantRegistrationCommand;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantRegistrationException;
import com.wcpe.tenantcontext.TenantRegistrationService.TenantStatus;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class TenantRegistrationServiceTest {
    private final AtomicInteger idSequence = new AtomicInteger(1);
    private final TenantRegistrationService service = new TenantRegistrationService(
        Clock.fixed(Instant.parse("2026-06-10T13:00:00Z"), ZoneOffset.UTC),
        () -> "tenant-" + idSequence.getAndIncrement()
    );

    @Test
    void registersValidTenantInPendingActivationWithTenantId() {
        TenantDetails tenant = service.register(validCommand("Acme Lending"));

        assertThat(tenant.tenantId()).isEqualTo("tenant-1");
        assertThat(tenant.name()).isEqualTo("Acme Lending");
        assertThat(tenant.status()).isEqualTo(TenantStatus.PENDING_ACTIVATION);
        assertThat(tenant.createdAt()).isEqualTo(Instant.parse("2026-06-10T13:00:00Z"));
        assertThat(tenant.assignedUserCount()).isZero();
    }

    @Test
    void rejectsDuplicateTenantNameWithExistingTenantIdentifier() {
        TenantDetails original = service.register(validCommand("Acme Lending"));

        assertThatThrownBy(() -> service.register(validCommand(" acme lending ")))
            .isInstanceOf(TenantRegistrationException.class)
            .satisfies(error -> {
                TenantRegistrationException registrationError = (TenantRegistrationException) error;
                assertThat(registrationError.httpStatus()).isEqualTo(409);
                assertThat(registrationError.code()).isEqualTo("TENANT_NAME_EXISTS");
                assertThat(registrationError.existingTenantId()).isEqualTo(original.tenantId());
            });
    }

    @Test
    void rejectsMissingRequiredFieldsWithFieldLevelErrors() {
        assertThatThrownBy(() -> service.register(new TenantRegistrationCommand(" ", null, List.of("tenant:register"))))
            .isInstanceOf(TenantRegistrationException.class)
            .satisfies(error -> {
                TenantRegistrationException registrationError = (TenantRegistrationException) error;
                assertThat(registrationError.httpStatus()).isEqualTo(422);
                assertThat(registrationError.code()).isEqualTo("VALIDATION_FAILED");
                assertThat(registrationError.fieldErrors()).containsKeys("name", "legalName");
            });
    }

    @Test
    void deniesRegistrationWithoutPermissionAndCreatesNoTenantRow() {
        assertThatThrownBy(() -> service.register(new TenantRegistrationCommand("Acme Lending", "Acme Lending LLC", List.of("tenant:read"))))
            .isInstanceOf(TenantRegistrationException.class)
            .satisfies(error -> {
                TenantRegistrationException registrationError = (TenantRegistrationException) error;
                assertThat(registrationError.httpStatus()).isEqualTo(403);
                assertThat(registrationError.code()).isEqualTo("ACCESS_DENIED");
            });

        assertThat(service.tenantCount()).isZero();
    }

    @Test
    void activatesPendingTenantAndTreatsActiveActivationAsIdempotent() {
        TenantDetails pending = service.register(validCommand("Acme Lending"));

        TenantDetails active = service.activate(pending.tenantId());
        TenantDetails idempotent = service.activate(pending.tenantId());

        assertThat(active.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(idempotent.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(idempotent.tenantId()).isEqualTo(pending.tenantId());
    }

    @Test
    void returnsNotFoundForMissingActivationAndRead() {
        assertNotFound(() -> service.activate("missing-tenant"));
        assertNotFound(() -> service.read("missing-tenant"));
    }

    @Test
    void suspendsAndReactivatesTenant() {
        TenantDetails active = service.activate(service.register(validCommand("Acme Lending")).tenantId());

        TenantDetails suspended = service.suspend(active.tenantId());
        TenantDetails reactivated = service.activate(active.tenantId());

        assertThat(suspended.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(reactivated.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void blocksUserCreationForSuspendedTenantWithoutInventingCredentialBehavior() {
        TenantDetails active = service.activate(service.register(validCommand("Acme Lending")).tenantId());
        service.suspend(active.tenantId());

        assertThatThrownBy(() -> service.createAssignedUser(active.tenantId()))
            .isInstanceOf(TenantRegistrationException.class)
            .satisfies(error -> {
                TenantRegistrationException registrationError = (TenantRegistrationException) error;
                assertThat(registrationError.httpStatus()).isEqualTo(409);
                assertThat(registrationError.code()).isEqualTo("TENANT_SUSPENDED");
            });
    }

    @Test
    void readsTenantDetailsWithAssignedUserCount() {
        TenantDetails active = service.activate(service.register(validCommand("Acme Lending")).tenantId());
        service.createAssignedUser(active.tenantId());
        service.createAssignedUser(active.tenantId());

        TenantDetails read = service.read(active.tenantId());

        assertThat(read.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(read.name()).isEqualTo("Acme Lending");
        assertThat(read.createdAt()).isEqualTo(Instant.parse("2026-06-10T13:00:00Z"));
        assertThat(read.assignedUserCount()).isEqualTo(2);
    }

    private static TenantRegistrationCommand validCommand(String name) {
        return new TenantRegistrationCommand(name, name.trim() + " LLC", List.of("tenant:register"));
    }

    private static void assertNotFound(ThrowingCallable action) {
        assertThatThrownBy(action)
            .isInstanceOf(TenantRegistrationException.class)
            .satisfies(error -> {
                TenantRegistrationException registrationError = (TenantRegistrationException) error;
                assertThat(registrationError.httpStatus()).isEqualTo(404);
                assertThat(registrationError.code()).isEqualTo("NOT_FOUND");
            });
    }
}
