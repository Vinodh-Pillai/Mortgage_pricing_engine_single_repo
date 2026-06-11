package com.wcpe.tenantcontext;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class TenantRegistrationService {
    public static final String REGISTER_PERMISSION = "tenant:register";
    private final Map<String, TenantRecord> tenantsById = new LinkedHashMap<>();
    private final Map<String, String> tenantIdByNormalizedName = new LinkedHashMap<>();
    private final Clock clock;
    private final Supplier<String> tenantIdSupplier;

    public TenantRegistrationService() {
        this(Clock.systemUTC(), () -> "tenant-" + UUID.randomUUID());
    }

    TenantRegistrationService(Clock clock, Supplier<String> tenantIdSupplier) {
        if (clock == null || tenantIdSupplier == null) {
            throw new TenantRegistrationException(500, "CONFIGURATION_INVALID", "Tenant registration dependencies are required.");
        }
        this.clock = clock;
        this.tenantIdSupplier = tenantIdSupplier;
    }

    public TenantDetails register(TenantRegistrationCommand command) {
        if (command == null || !TenantAccessPolicy.normalizeList(command.adminScopes()).contains(REGISTER_PERMISSION)) {
            throw new TenantRegistrationException(403, "ACCESS_DENIED", "Required tenant:register permission is missing.");
        }

        Map<String, String> fieldErrors = validate(command);
        if (!fieldErrors.isEmpty()) {
            throw new TenantRegistrationException(422, "VALIDATION_FAILED", "Tenant registration fields are invalid.", "", fieldErrors);
        }

        String normalizedName = normalizeName(command.name());
        String existingTenantId = tenantIdByNormalizedName.get(normalizedName);
        if (existingTenantId != null) {
            throw new TenantRegistrationException(409, "TENANT_NAME_EXISTS", "Tenant name already exists.", existingTenantId, Map.of());
        }

        String tenantId = nextTenantId();
        TenantRecord record = new TenantRecord(
            tenantId,
            command.name().trim(),
            command.legalName().trim(),
            TenantStatus.PENDING_ACTIVATION,
            Instant.now(clock),
            0
        );
        tenantsById.put(tenantId, record);
        tenantIdByNormalizedName.put(normalizedName, tenantId);
        return record.toDetails();
    }

    public TenantDetails activate(String tenantId) {
        TenantRecord record = requireTenant(tenantId);
        if (record.status() == TenantStatus.ACTIVE) {
            return record.toDetails();
        }

        TenantRecord active = record.withStatus(TenantStatus.ACTIVE);
        tenantsById.put(active.tenantId(), active);
        return active.toDetails();
    }

    public TenantDetails suspend(String tenantId) {
        TenantRecord record = requireTenant(tenantId);
        TenantRecord suspended = record.withStatus(TenantStatus.SUSPENDED);
        tenantsById.put(suspended.tenantId(), suspended);
        return suspended.toDetails();
    }

    public TenantDetails read(String tenantId) {
        return requireTenant(tenantId).toDetails();
    }

    public TenantDetails createAssignedUser(String tenantId) {
        TenantRecord record = requireTenant(tenantId);
        if (record.status() == TenantStatus.SUSPENDED) {
            throw new TenantRegistrationException(409, "TENANT_SUSPENDED", "Suspended tenant cannot accept new user creation.");
        }
        if (record.status() != TenantStatus.ACTIVE) {
            throw new TenantRegistrationException(409, "TENANT_NOT_ACTIVE", "Tenant is not active.");
        }

        TenantRecord updated = record.withAssignedUserCount(record.assignedUserCount() + 1);
        tenantsById.put(updated.tenantId(), updated);
        return updated.toDetails();
    }

    int tenantCount() {
        return tenantsById.size();
    }

    private TenantRecord requireTenant(String tenantId) {
        String normalized = required(tenantId, "tenantId");
        TenantRecord record = tenantsById.get(normalized);
        if (record == null) {
            throw new TenantRegistrationException(404, "NOT_FOUND", "Tenant was not found.");
        }
        return record;
    }

    private Map<String, String> validate(TenantRegistrationCommand command) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (command.name() == null || command.name().isBlank()) {
            errors.put("name", "Tenant name is required.");
        }
        if (command.legalName() == null || command.legalName().isBlank()) {
            errors.put("legalName", "Tenant legal name is required.");
        }
        return errors;
    }

    private String nextTenantId() {
        String tenantId = required(tenantIdSupplier.get(), "tenantId");
        if (tenantsById.containsKey(tenantId)) {
            throw new TenantRegistrationException(409, "TENANT_ID_EXISTS", "Generated tenant identifier already exists.");
        }
        return tenantId;
    }

    private static String normalizeName(String name) {
        return required(name, "name").toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new TenantRegistrationException(422, "VALIDATION_FAILED", fieldName + " is required.");
        }
        return value.trim();
    }

    private record TenantRecord(
        String tenantId,
        String name,
        String legalName,
        TenantStatus status,
        Instant createdAt,
        int assignedUserCount
    ) {
        TenantDetails toDetails() {
            return new TenantDetails(tenantId, name, legalName, status, createdAt, assignedUserCount);
        }

        TenantRecord withStatus(TenantStatus status) {
            return new TenantRecord(tenantId, name, legalName, status, createdAt, assignedUserCount);
        }

        TenantRecord withAssignedUserCount(int assignedUserCount) {
            return new TenantRecord(tenantId, name, legalName, status, createdAt, assignedUserCount);
        }
    }

    public enum TenantStatus {
        PENDING_ACTIVATION,
        ACTIVE,
        SUSPENDED
    }

    public record TenantRegistrationCommand(String name, String legalName, List<String> adminScopes) { }

    public record TenantDetails(
        String tenantId,
        String name,
        String legalName,
        TenantStatus status,
        Instant createdAt,
        int assignedUserCount
    ) { }

    public static class TenantRegistrationException extends RuntimeException {
        private final int httpStatus;
        private final String code;
        private final String existingTenantId;
        private final Map<String, String> fieldErrors;

        public TenantRegistrationException(int httpStatus, String code, String message) {
            this(httpStatus, code, message, "", Map.of());
        }

        public TenantRegistrationException(
            int httpStatus,
            String code,
            String message,
            String existingTenantId,
            Map<String, String> fieldErrors
        ) {
            super(message);
            this.httpStatus = httpStatus;
            this.code = code;
            this.existingTenantId = existingTenantId == null ? "" : existingTenantId;
            this.fieldErrors = Map.copyOf(fieldErrors == null ? Map.of() : fieldErrors);
        }

        public int httpStatus() {
            return httpStatus;
        }

        public String code() {
            return code;
        }

        public String existingTenantId() {
            return existingTenantId;
        }

        public Map<String, String> fieldErrors() {
            return fieldErrors;
        }
    }
}
