package com.wcpe.tenantcontext;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantRegistrationService {
    public static final String REGISTER_PERMISSION = "tenant:register";
    public static final List<String> DEFAULT_FEATURE_KEYS = List.of(
        "non_qm_pricing",
        "heloc_pricing",
        "reverse_mortgage",
        "government_products",
        "mi_pricing",
        "quick_pricer",
        "lock_management",
        "scenario_analysis",
        "partner_integrations",
        "ml_advisory",
        "loanpass_compatibility",
        "loanpass_strict_mapping",
        "loanpass_callback_delivery"
    );

    private final Clock clock;
    private final Supplier<String> tenantIdSupplier;
    private final JdbcTemplate jdbcTemplate;
    private final TenantRegistrationStore testStore;

    public TenantRegistrationService() {
        throw new IllegalStateException("TenantRegistrationService requires a JDBC DataSource-backed constructor in production; refusing in-memory store-of-record fallback");
    }

    @Autowired
    public TenantRegistrationService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.tenantIdSupplier = () -> "";
        this.jdbcTemplate = java.util.Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.testStore = null;
    }

    TenantRegistrationService(Clock clock, Supplier<String> tenantIdSupplier) {
        throw new IllegalStateException("TenantRegistrationService test memory state must be supplied by a src/test fixture; refusing src/main process-local store-of-record fallback");
    }

    TenantRegistrationService(Clock clock, Supplier<String> tenantIdSupplier, TenantRegistrationStore testStore) {
        if (clock == null || tenantIdSupplier == null) {
            throw new TenantRegistrationException(500, "CONFIGURATION_INVALID", "Tenant registration dependencies are required.");
        }
        this.clock = clock;
        this.tenantIdSupplier = tenantIdSupplier;
        this.jdbcTemplate = null;
        this.testStore = java.util.Objects.requireNonNull(testStore, "testStore is required");
    }

    public TenantDetails register(TenantRegistrationCommand command) {
        if (command == null || !TenantAccessPolicy.normalizeList(command.adminScopes()).contains(REGISTER_PERMISSION)) {
            throw new TenantRegistrationException(403, "ACCESS_DENIED", "Required tenant:register permission is missing.");
        }

        Map<String, String> fieldErrors = validateRegistration(command);
        if (!fieldErrors.isEmpty()) {
            throw new TenantRegistrationException(422, "VALIDATION_FAILED", "Tenant registration fields are invalid.", "", fieldErrors);
        }

        return createTenant(new TenantCreateRequest(
            command.name(),
            command.legalName(),
            "",
            "",
            "",
            "",
            "",
            "",
            "US",
            "",
            "",
            "",
            "",
            "registration-api"
        ), "registration-api");
    }

    public TenantDetails createTenant(TenantCreateRequest request, String createdBy) {
        requireJdbcContractOrMemoryTestMode();
        Map<String, String> fieldErrors = validateCreate(request);
        if (!fieldErrors.isEmpty()) {
            throw new TenantRegistrationException(422, "VALIDATION_FAILED", "Tenant creation fields are invalid.", "", fieldErrors);
        }

        String normalizedName = normalizeName(request.tenantName());
        TenantRegistrationStore store = requireTestStore();
        String existingTenantId = store.tenantIdByNormalizedName(normalizedName).orElse(null);
        if (existingTenantId != null) {
            throw new TenantRegistrationException(409, "TENANT_NAME_EXISTS", "Tenant name already exists.", existingTenantId, Map.of());
        }

        String tenantId = nextTenantId();
        Instant now = Instant.now(clock);
        TenantRecord record = new TenantRecord(
            tenantId,
            request.tenantName().trim(),
            optional(request.displayName()).isBlank() ? request.tenantName().trim() : optional(request.displayName()),
            TenantStatus.PENDING_ACTIVATION,
            now,
            now,
            null,
            null,
            null,
            0,
            optional(request.logoUrl()),
            optional(request.primaryColor()),
            optional(request.secondaryColor()),
            optional(request.contactEmail()),
            optional(request.contactPhone()),
            optional(request.addressLine1()),
            optional(request.city()),
            optional(request.state()),
            optional(request.postalCode()),
            optional(request.country()).isBlank() ? "US" : optional(request.country()),
            optional(request.nmlsId()),
            optional(createdBy)
        );
        store.saveTenant(normalizedName, record);
        store.saveFeatureFlags(tenantId, defaultFeatureFlags(createdBy));
        return record.toDetails();
    }

    public TenantDetails updateTenant(String tenantId, TenantUpdateRequest request, String updatedBy) {
        requireJdbcContractOrMemoryTestMode();
        TenantRecord record = requireTenant(tenantId);
        if (request == null) {
            throw new TenantRegistrationException(422, "VALIDATION_FAILED", "Tenant update request is required.");
        }
        TenantRecord updated = record.withProfile(
            coalesce(request.displayName(), record.displayName()),
            coalesce(request.logoUrl(), record.logoUrl()),
            coalesce(request.primaryColor(), record.primaryColor()),
            coalesce(request.secondaryColor(), record.secondaryColor()),
            coalesce(request.contactEmail(), record.contactEmail()),
            coalesce(request.contactPhone(), record.contactPhone()),
            coalesce(request.addressLine1(), record.addressLine1()),
            coalesce(request.city(), record.city()),
            coalesce(request.state(), record.state()),
            coalesce(request.postalCode(), record.postalCode()),
            coalesce(request.country(), record.country()),
            coalesce(request.nmlsId(), record.nmlsId()),
            optional(updatedBy),
            Instant.now(clock)
        );
        requireTestStore().saveTenant(normalizeName(updated.name()), updated);
        return updated.toDetails();
    }

    public List<TenantDetails> listTenants(TenantFilter filter) {
        requireJdbcContractOrMemoryTestMode();
        TenantFilter safeFilter = filter == null ? new TenantFilter("", "") : filter;
        String search = optional(safeFilter.search()).toLowerCase(Locale.ROOT);
        String status = optional(safeFilter.status()).toUpperCase(Locale.ROOT);
        List<TenantDetails> tenants = new java.util.ArrayList<>();
        for (TenantRecord record : requireTestStore().tenants()) {
            boolean searchMatches = search.isBlank()
                || record.name().toLowerCase(Locale.ROOT).contains(search)
                || record.displayName().toLowerCase(Locale.ROOT).contains(search);
            boolean statusMatches = status.isBlank() || record.status().name().equals(status);
            if (searchMatches && statusMatches) tenants.add(record.toDetails());
        }
        return List.copyOf(tenants);
    }

    public TenantDetails activate(String tenantId) {
        requireJdbcContractOrMemoryTestMode();
        TenantRecord record = requireTenant(tenantId);
        if (record.status() == TenantStatus.ACTIVE) return record.toDetails();
        if (record.status() == TenantStatus.DEACTIVATED) {
            throw new TenantRegistrationException(409, "TENANT_DEACTIVATED", "Deactivated tenant cannot be activated.");
        }

        TenantRecord active = record.withStatus(TenantStatus.ACTIVE, Instant.now(clock), "system");
        requireTestStore().saveTenant(normalizeName(active.name()), active);
        return active.toDetails();
    }

    public TenantDetails suspend(String tenantId) {
        requireJdbcContractOrMemoryTestMode();
        TenantRecord record = requireTenant(tenantId);
        if (record.status() != TenantStatus.ACTIVE) {
            throw new TenantRegistrationException(409, "TENANT_NOT_ACTIVE", "Only active tenants can be suspended.");
        }
        TenantRecord suspended = record.withStatus(TenantStatus.SUSPENDED, Instant.now(clock), "system");
        requireTestStore().saveTenant(normalizeName(suspended.name()), suspended);
        return suspended.toDetails();
    }

    public TenantDetails deactivate(String tenantId) {
        requireJdbcContractOrMemoryTestMode();
        TenantRecord record = requireTenant(tenantId);
        if (record.status() == TenantStatus.DEACTIVATED) return record.toDetails();
        if (record.status() == TenantStatus.PENDING_ACTIVATION) {
            throw new TenantRegistrationException(409, "TENANT_NOT_ACTIVE", "Only active or suspended tenants can be deactivated.");
        }
        TenantRecord deactivated = record.withStatus(TenantStatus.DEACTIVATED, Instant.now(clock), "system");
        requireTestStore().saveTenant(normalizeName(deactivated.name()), deactivated);
        return deactivated.toDetails();
    }

    public TenantDetails read(String tenantId) {
        requireJdbcContractOrMemoryTestMode();
        return requireTenant(tenantId).toDetails();
    }

    public TenantFeatureFlags getFeatureFlags(String tenantId) {
        requireJdbcContractOrMemoryTestMode();
        requireTenant(tenantId);
        String normalizedTenantId = tenantId.trim();
        return new TenantFeatureFlags(normalizedTenantId, Map.copyOf(featureFlagsFor(normalizedTenantId, "system")));
    }

    public TenantFeatureFlags updateFeatureFlags(String tenantId, Map<String, Boolean> flags, String updatedBy) {
        requireJdbcContractOrMemoryTestMode();
        requireTenant(tenantId);
        if (flags == null || flags.isEmpty()) {
            throw new TenantRegistrationException(422, "VALIDATION_FAILED", "At least one feature flag update is required.");
        }
        String normalizedTenantId = tenantId.trim();
        LinkedHashMap<String, FeatureFlag> existing = featureFlagsFor(normalizedTenantId, updatedBy);
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            String featureKey = optional(entry.getKey());
            if (!DEFAULT_FEATURE_KEYS.contains(featureKey)) {
                throw new TenantRegistrationException(422, "UNKNOWN_FEATURE_FLAG", "Feature flag is not configured for tenant management.");
            }
            FeatureFlag previous = existing.get(featureKey);
            int nextVersion = previous == null ? 1 : previous.version() + 1;
            existing.put(featureKey, featureFlag(featureKey, entry.getValue() != null && entry.getValue(), nextVersion, optional(updatedBy)));
        }
        requireTestStore().saveFeatureFlags(normalizedTenantId, existing);
        return new TenantFeatureFlags(normalizedTenantId, Map.copyOf(existing));
    }

    public long getUserCount(String tenantId) {
        requireJdbcContractOrMemoryTestMode();
        return requireTenant(tenantId).assignedUserCount();
    }

    public TenantDetails createAssignedUser(String tenantId) {
        requireJdbcContractOrMemoryTestMode();
        TenantRecord record = requireTenant(tenantId);
        if (record.status() == TenantStatus.SUSPENDED) {
            throw new TenantRegistrationException(409, "TENANT_SUSPENDED", "Suspended tenant cannot accept new user creation.");
        }
        if (record.status() != TenantStatus.ACTIVE) {
            throw new TenantRegistrationException(409, "TENANT_NOT_ACTIVE", "Tenant is not active.");
        }

        TenantRecord updated = record.withAssignedUserCount(record.assignedUserCount() + 1, Instant.now(clock));
        requireTestStore().saveTenant(normalizeName(updated.name()), updated);
        return updated.toDetails();
    }

    int tenantCount() {
        requireJdbcContractOrMemoryTestMode();
        return requireTestStore().tenantCount();
    }

    private void requireJdbcContractOrMemoryTestMode() {
        if (jdbcTemplate != null) {
            throw new TenantRegistrationException(503, "TENANT_REGISTRATION_PERSISTENCE_CONTRACT_MISSING",
                "tenant.tenant uses UUID tenant_id while the service contract currently exposes string tenant identifiers; refusing in-memory fallback until the datasource/schema contract is reconciled.");
        }
        requireTestStore();
    }

    private TenantRegistrationStore requireTestStore() {
        if (testStore == null) {
            throw new TenantRegistrationException(503, "TENANT_REGISTRATION_PERSISTENCE_CONTRACT_MISSING",
                "Tenant registration requires JDBC persistence or an explicit src/test fixture; refusing src/main process-local store-of-record fallback.");
        }
        return testStore;
    }

    private TenantRecord requireTenant(String tenantId) {
        String normalized = required(tenantId, "tenantId");
        TenantRecord record = requireTestStore().tenantById(normalized).orElse(null);
        if (record == null) {
            throw new TenantRegistrationException(404, "NOT_FOUND", "Tenant was not found.");
        }
        return record;
    }

    private Map<String, String> validateRegistration(TenantRegistrationCommand command) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (command.name() == null || command.name().isBlank()) {
            errors.put("name", "Tenant name is required.");
        }
        if (command.legalName() == null || command.legalName().isBlank()) {
            errors.put("legalName", "Tenant legal name is required.");
        }
        return errors;
    }

    private Map<String, String> validateCreate(TenantCreateRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request == null) {
            errors.put("request", "Tenant creation request is required.");
            return errors;
        }
        if (request.tenantName() == null || request.tenantName().isBlank()) errors.put("tenantName", "Tenant name is required.");
        if (!optional(request.primaryColor()).isBlank() && !optional(request.primaryColor()).matches("^#[0-9A-Fa-f]{6}$")) errors.put("primaryColor", "Primary color must be a hex color.");
        if (!optional(request.secondaryColor()).isBlank() && !optional(request.secondaryColor()).matches("^#[0-9A-Fa-f]{6}$")) errors.put("secondaryColor", "Secondary color must be a hex color.");
        return errors;
    }

    private String nextTenantId() {
        String tenantId = required(tenantIdSupplier.get(), "tenantId");
        if (requireTestStore().tenantById(tenantId).isPresent()) {
            throw new TenantRegistrationException(409, "TENANT_ID_EXISTS", "Generated tenant identifier already exists.");
        }
        return tenantId;
    }

    private LinkedHashMap<String, FeatureFlag> featureFlagsFor(String tenantId, String updatedBy) {
        TenantRegistrationStore store = requireTestStore();
        LinkedHashMap<String, FeatureFlag> flags = store.featureFlagsByTenantId(tenantId).orElseGet(() -> defaultFeatureFlags(updatedBy));
        store.saveFeatureFlags(tenantId, flags);
        return flags;
    }

    private static String normalizeName(String name) {
        return required(name, "name").toLowerCase(Locale.ROOT);
    }

    private LinkedHashMap<String, FeatureFlag> defaultFeatureFlags(String updatedBy) {
        LinkedHashMap<String, FeatureFlag> flags = new LinkedHashMap<>();
        for (String featureKey : DEFAULT_FEATURE_KEYS) {
            boolean enabled = switch (featureKey) {
                case "non_qm_pricing", "heloc_pricing", "government_products", "mi_pricing", "quick_pricer", "lock_management", "scenario_analysis", "loanpass_strict_mapping" -> true;
                default -> false;
            };
            flags.put(featureKey, featureFlag(featureKey, enabled, 1, optional(updatedBy)));
        }
        return flags;
    }

    private FeatureFlag featureFlag(String featureKey, boolean enabled, int version, String updatedBy) {
        String normalizedKey = required(featureKey, "featureKey");
        int safeVersion = Math.max(1, version);
        String configRef = "tenant-feature-flags:" + normalizedKey + ":v" + safeVersion;
        String auditRef = "tenant-feature-flags:audit:" + normalizedKey + ":v" + safeVersion;
        return new FeatureFlag(enabled, Map.of("configRef", configRef, "auditRef", auditRef, "version", safeVersion), Instant.now(clock), optional(updatedBy), safeVersion, configRef, auditRef);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new TenantRegistrationException(422, "VALIDATION_FAILED", fieldName + " is required.");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }

    private static String coalesce(String candidate, String fallback) {
        return candidate == null ? fallback : candidate.trim();
    }

    record TenantRecord(
        String tenantId,
        String name,
        String displayName,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant suspendedAt,
        Instant deactivatedAt,
        int assignedUserCount,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String contactEmail,
        String contactPhone,
        String addressLine1,
        String city,
        String state,
        String postalCode,
        String country,
        String nmlsId,
        String updatedBy
    ) {
        TenantDetails toDetails() {
            return new TenantDetails(
                tenantId,
                name,
                displayName,
                status,
                createdAt,
                updatedAt,
                activatedAt,
                suspendedAt,
                deactivatedAt,
                assignedUserCount,
                logoUrl,
                primaryColor,
                secondaryColor,
                contactEmail,
                contactPhone,
                addressLine1,
                city,
                state,
                postalCode,
                country,
                nmlsId,
                updatedBy
            );
        }

        TenantRecord withStatus(TenantStatus nextStatus, Instant statusTime, String actor) {
            return new TenantRecord(
                tenantId, name, displayName, nextStatus, createdAt, statusTime,
                nextStatus == TenantStatus.ACTIVE ? statusTime : activatedAt,
                nextStatus == TenantStatus.SUSPENDED ? statusTime : suspendedAt,
                nextStatus == TenantStatus.DEACTIVATED ? statusTime : deactivatedAt,
                assignedUserCount, logoUrl, primaryColor, secondaryColor, contactEmail, contactPhone, addressLine1,
                city, state, postalCode, country, nmlsId, actor
            );
        }

        TenantRecord withAssignedUserCount(int nextAssignedUserCount, Instant updatedAt) {
            return new TenantRecord(
                tenantId, name, displayName, status, createdAt, updatedAt, activatedAt, suspendedAt, deactivatedAt,
                nextAssignedUserCount, logoUrl, primaryColor, secondaryColor, contactEmail, contactPhone, addressLine1,
                city, state, postalCode, country, nmlsId, updatedBy
            );
        }

        TenantRecord withProfile(
            String displayName,
            String logoUrl,
            String primaryColor,
            String secondaryColor,
            String contactEmail,
            String contactPhone,
            String addressLine1,
            String city,
            String state,
            String postalCode,
            String country,
            String nmlsId,
            String updatedBy,
            Instant updatedAt
        ) {
            return new TenantRecord(
                tenantId, name, displayName, status, createdAt, updatedAt, activatedAt, suspendedAt, deactivatedAt,
                assignedUserCount, logoUrl, primaryColor, secondaryColor, contactEmail, contactPhone, addressLine1,
                city, state, postalCode, country, nmlsId, updatedBy
            );
        }
    }

    public enum TenantStatus {
        PENDING_ACTIVATION,
        ACTIVE,
        SUSPENDED,
        DEACTIVATED
    }

    public record TenantRegistrationCommand(String name, String legalName, List<String> adminScopes) { }

    public record TenantCreateRequest(
        String tenantName,
        String displayName,
        String contactEmail,
        String contactPhone,
        String addressLine1,
        String city,
        String state,
        String postalCode,
        String country,
        String nmlsId,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String createdBy
    ) { }

    public record TenantUpdateRequest(
        String displayName,
        String contactEmail,
        String contactPhone,
        String addressLine1,
        String city,
        String state,
        String postalCode,
        String country,
        String nmlsId,
        String logoUrl,
        String primaryColor,
        String secondaryColor
    ) { }

    public record TenantFilter(String search, String status) { }

    public record FeatureFlag(boolean enabled, Map<String, Object> config, Instant updatedAt, String updatedBy,
                              int version, String configRef, String auditRef) {
        public FeatureFlag(boolean enabled, Map<String, Object> config, Instant updatedAt, String updatedBy) {
            this(enabled, config, updatedAt, updatedBy, 1, "", "");
        }
    }

    public record TenantFeatureFlags(String tenantId, Map<String, FeatureFlag> flags) { }

    public record TenantDetails(
        String tenantId,
        String name,
        String displayName,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant suspendedAt,
        Instant deactivatedAt,
        int assignedUserCount,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String contactEmail,
        String contactPhone,
        String addressLine1,
        String city,
        String state,
        String postalCode,
        String country,
        String nmlsId,
        String updatedBy
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

    interface TenantRegistrationStore {
        Optional<TenantRecord> tenantById(String tenantId);
        Optional<String> tenantIdByNormalizedName(String normalizedName);
        List<TenantRecord> tenants();
        void saveTenant(String normalizedName, TenantRecord tenant);
        Optional<LinkedHashMap<String, FeatureFlag>> featureFlagsByTenantId(String tenantId);
        void saveFeatureFlags(String tenantId, LinkedHashMap<String, FeatureFlag> flags);
        int tenantCount();
    }
}
