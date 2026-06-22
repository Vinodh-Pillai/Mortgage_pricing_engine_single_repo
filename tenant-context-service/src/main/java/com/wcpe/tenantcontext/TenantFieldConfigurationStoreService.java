package com.wcpe.tenantcontext;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantFieldConfigurationStoreService {
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;
    private final TenantFieldConfigurationStore testStore;

    public TenantFieldConfigurationStoreService() {
        throw new IllegalStateException("TenantFieldConfigurationStoreService requires a JDBC DataSource-backed constructor in production; refusing in-memory store-of-record fallback");
    }

    @Autowired
    public TenantFieldConfigurationStoreService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate is required");
        this.testStore = null;
    }

    TenantFieldConfigurationStoreService(Clock clock) {
        throw new IllegalStateException("TenantFieldConfigurationStoreService test memory state must be supplied by a src/test fixture; refusing src/main process-local store-of-record fallback");
    }

    TenantFieldConfigurationStoreService(Clock clock, TenantFieldConfigurationStore testStore) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.jdbcTemplate = null;
        this.testStore = Objects.requireNonNull(testStore, "testStore is required");
    }

    public TenantFieldConfiguration save(TenantFieldConfiguration command) {
        TenantFieldConfiguration validated = validated(command, clock.instant());
        if (jdbcBacked()) {
            jdbcTemplate.update("""
                INSERT INTO tenant.tenant_field_configuration (tenant_id, surface, field_id, configuration_id, origin, system_field_ref,
                  name_alias, description_alias, enabled, omitted, updated_at, audit_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, surface, field_id) DO UPDATE SET configuration_id = EXCLUDED.configuration_id,
                  origin = EXCLUDED.origin, system_field_ref = EXCLUDED.system_field_ref, name_alias = EXCLUDED.name_alias,
                  description_alias = EXCLUDED.description_alias, enabled = EXCLUDED.enabled, omitted = EXCLUDED.omitted,
                  updated_at = EXCLUDED.updated_at, audit_ref = EXCLUDED.audit_ref
                """,
                validated.tenantId(), validated.surface(), validated.fieldId(), validated.configurationId(), validated.origin().name(),
                validated.systemFieldRef(), validated.nameAlias(), validated.descriptionAlias(), validated.enabled(), validated.omitted(),
                java.sql.Timestamp.from(validated.updatedAt()), validated.auditRef());
            return validated;
        }
        requireTestStore().saveConfiguration(validated);
        return validated;
    }

    public List<TenantFieldConfiguration> replaceTenantSurface(String tenantId, String surface, Collection<TenantFieldConfiguration> commands) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        if (jdbcBacked()) {
            jdbcTemplate.update("DELETE FROM tenant.tenant_field_configuration WHERE tenant_id = ? AND surface = ?", normalizedTenantId, normalizedSurface);
            if (commands != null) {
                commands.stream()
                    .map(command -> scopedToTenantSurface(normalizedTenantId, normalizedSurface, command))
                    .forEach(this::save);
            }
            return storedForTenantSurface(normalizedTenantId, normalizedSurface);
        }
        requireTestStore().removeConfigurationsForTenantSurface(normalizedTenantId, normalizedSurface);
        if (commands != null) {
            commands.stream()
                .map(command -> scopedToTenantSurface(normalizedTenantId, normalizedSurface, command))
                .forEach(this::save);
        }
        return storedForTenantSurface(normalizedTenantId, normalizedSurface);
    }

    public List<TenantFieldConfiguration> storedForTenantSurface(String tenantId, String surface) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        if (jdbcBacked()) {
            return jdbcTemplate.query("""
                SELECT * FROM tenant.tenant_field_configuration WHERE tenant_id = ? AND surface = ? ORDER BY field_id
                """, (rs, rowNum) -> new TenantFieldConfiguration(
                    rs.getString("configuration_id"), rs.getString("tenant_id"), rs.getString("surface"), rs.getString("field_id"),
                    FieldOrigin.valueOf(rs.getString("origin")), rs.getString("system_field_ref"), rs.getString("name_alias"),
                    rs.getString("description_alias"), rs.getBoolean("enabled"), rs.getBoolean("omitted"),
                    rs.getTimestamp("updated_at").toInstant(), rs.getString("audit_ref")), normalizedTenantId, normalizedSurface);
        }
        return requireTestStore().configurationsForTenantSurface(normalizedTenantId, normalizedSurface);
    }

    public List<TenantFieldConfiguration> activeForTenantSurface(String tenantId, String surface) {
        return storedForTenantSurface(tenantId, surface).stream()
            .filter(TenantFieldConfiguration::active)
            .toList();
    }

    public TenantFieldConfigurationDraft saveDraft(String tenantId, String surface, Collection<TenantFieldConfiguration> commands, String userId) {
        if (jdbcBacked()) {
            throw fieldConfigError("TENANT_FIELD_PERSISTENCE_CONTRACT_MISSING", "draft", "Tenant field draft persistence schema is not implemented; refusing in-memory fallback.");
        }
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        Instant now = clock.instant();
        List<TenantFieldConfiguration> draftConfigurations = Optional.ofNullable(commands).orElseGet(List::of).stream()
            .map(command -> validated(scopedToTenantSurface(normalizedTenantId, normalizedSurface, command), now))
            .toList();
        TenantFieldConfigurationDraft draft = new TenantFieldConfigurationDraft(
            "draft:" + normalizedTenantId + ":" + normalizedSurface,
            normalizedTenantId,
            normalizedSurface,
            draftConfigurations,
            Map.of(),
            now,
            required(userId, "userId")
        );
        requireTestStore().saveDraft(normalizedTenantId, normalizedSurface, draft);
        return draft;
    }

    public TenantFieldConfigurationDraft saveDraft(String tenantId, String surface, Collection<TenantFieldConfiguration> commands, Map<String, List<String>> conditionFieldRefs, String userId) {
        TenantFieldConfigurationDraft draft = saveDraft(tenantId, surface, commands, userId);
        TenantFieldConfigurationDraft draftWithConditions = new TenantFieldConfigurationDraft(
            draft.draftId(),
            draft.tenantId(),
            draft.surface(),
            draft.configurations(),
            normalizedConditionFieldRefs(conditionFieldRefs),
            draft.savedAt(),
            draft.userId()
        );
        requireTestStore().saveDraft(draft.tenantId(), draft.surface(), draftWithConditions);
        return draftWithConditions;
    }

    public Optional<TenantFieldConfigurationDraft> draftForTenantSurface(String tenantId, String surface) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        if (jdbcBacked()) {
            throw fieldConfigError("TENANT_FIELD_PERSISTENCE_CONTRACT_MISSING", "draft", "Tenant field draft persistence schema is not implemented; refusing in-memory fallback.");
        }
        return requireTestStore().draftForTenantSurface(normalizedTenantId, normalizedSurface);
    }

    public TenantFieldConfigurationVersion publishDraft(String tenantId, String surface, String userId) {
        if (jdbcBacked()) {
            throw fieldConfigError("TENANT_FIELD_PERSISTENCE_CONTRACT_MISSING", "draft", "Tenant field publish/version persistence schema is not implemented; refusing in-memory fallback.");
        }
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        TenantFieldConfigurationDraft draft = requireTestStore().draftForTenantSurface(normalizedTenantId, normalizedSurface)
            .orElseThrow(() -> fieldConfigError("TENANT_FIELD_DRAFT_MISSING", "draft", "A draft is required before publishing tenant field configuration."));
        validatePublishable(draft);

        List<TenantFieldConfiguration> oldPublished = storedForTenantSurface(normalizedTenantId, normalizedSurface);
        List<TenantFieldConfiguration> newPublished = draft.configurations().stream()
            .map(command -> validated(scopedToTenantSurface(normalizedTenantId, normalizedSurface, command), clock.instant()))
            .toList();
        int versionNumber = requireTestStore().currentVersion(normalizedTenantId, normalizedSurface) + 1;
        applyPublishedSnapshot(normalizedTenantId, normalizedSurface, newPublished);
        TenantFieldConfigurationVersion version = new TenantFieldConfigurationVersion(versionNumber, normalizedTenantId, normalizedSurface, newPublished, oldPublished, clock.instant(), required(userId, "userId"));
        requireTestStore().appendVersion(normalizedTenantId, normalizedSurface, version);
        requireTestStore().saveCurrentVersion(normalizedTenantId, normalizedSurface, versionNumber);
        requireTestStore().removeDraft(normalizedTenantId, normalizedSurface);
        requireTestStore().appendAuditRecord(auditRecord(normalizedTenantId, required(userId, "userId"), oldPublished, newPublished, normalizedSurface, "PUBLISH"));
        return version;
    }

    public TenantFieldConfigurationVersion rollbackToPreviousVersion(String tenantId, String surface, String userId) {
        if (jdbcBacked()) {
            throw fieldConfigError("TENANT_FIELD_PERSISTENCE_CONTRACT_MISSING", "version", "Tenant field rollback persistence schema is not implemented; refusing in-memory fallback.");
        }
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        List<TenantFieldConfigurationVersion> history = requireTestStore().versionsForTenantSurface(normalizedTenantId, normalizedSurface);
        if (history.isEmpty()) {
            throw fieldConfigError("TENANT_FIELD_VERSION_MISSING", "version", "A published version is required before rollback.");
        }
        TenantFieldConfigurationVersion current = history.get(history.size() - 1);
        List<TenantFieldConfiguration> restored = current.previousConfigurations().isEmpty() && history.size() > 1
            ? history.get(history.size() - 2).configurations()
            : current.previousConfigurations();
        if (restored.isEmpty()) {
            throw fieldConfigError("TENANT_FIELD_PREVIOUS_VERSION_MISSING", "version", "A prior published version is required before rollback.");
        }
        List<TenantFieldConfiguration> oldPublished = storedForTenantSurface(normalizedTenantId, normalizedSurface);
        int rollbackVersionNumber = Math.max(requireTestStore().currentVersion(normalizedTenantId, normalizedSurface), current.versionNumber()) + 1;
        applyPublishedSnapshot(normalizedTenantId, normalizedSurface, restored);
        TenantFieldConfigurationVersion rollbackVersion = new TenantFieldConfigurationVersion(rollbackVersionNumber, normalizedTenantId, normalizedSurface, restored, oldPublished, clock.instant(), required(userId, "userId"));
        requireTestStore().appendVersion(normalizedTenantId, normalizedSurface, rollbackVersion);
        requireTestStore().saveCurrentVersion(normalizedTenantId, normalizedSurface, rollbackVersionNumber);
        requireTestStore().appendAuditRecord(auditRecord(normalizedTenantId, required(userId, "userId"), oldPublished, restored, normalizedSurface, "ROLLBACK"));
        return rollbackVersion;
    }

    public List<TenantFieldConfigurationVersion> publishedVersions(String tenantId, String surface) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        if (jdbcBacked()) {
            throw fieldConfigError("TENANT_FIELD_PERSISTENCE_CONTRACT_MISSING", "version", "Tenant field version history persistence schema is not implemented; refusing in-memory fallback.");
        }
        return requireTestStore().versionsForTenantSurface(normalizedTenantId, normalizedSurface);
    }

    public List<TenantFieldConfigurationAuditRecord> auditRecordsForTenant(String tenantId) {
        String normalizedTenantId = required(tenantId, "tenantId");
        if (jdbcBacked()) {
            throw fieldConfigError("TENANT_FIELD_PERSISTENCE_CONTRACT_MISSING", "audit", "Tenant field audit persistence schema is not implemented; refusing in-memory fallback.");
        }
        return requireTestStore().auditRecordsForTenant(normalizedTenantId);
    }

    public Optional<TenantFieldConfiguration> activeField(String tenantId, String surface, String fieldId) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        String normalizedFieldId = required(fieldId, "fieldId");
        if (jdbcBacked()) {
            return jdbcTemplate.query("""
                SELECT * FROM tenant.tenant_field_configuration WHERE tenant_id = ? AND surface = ? AND field_id = ? AND enabled = true AND omitted = false
                """, (rs, rowNum) -> new TenantFieldConfiguration(
                    rs.getString("configuration_id"), rs.getString("tenant_id"), rs.getString("surface"), rs.getString("field_id"),
                    FieldOrigin.valueOf(rs.getString("origin")), rs.getString("system_field_ref"), rs.getString("name_alias"),
                    rs.getString("description_alias"), rs.getBoolean("enabled"), rs.getBoolean("omitted"),
                    rs.getTimestamp("updated_at").toInstant(), rs.getString("audit_ref")), normalizedTenantId, normalizedSurface, normalizedFieldId)
                .stream().findFirst();
        }
        return requireTestStore().configuration(normalizedTenantId, normalizedSurface, normalizedFieldId)
            .filter(TenantFieldConfiguration::active);
    }

    private boolean jdbcBacked() {
        return jdbcTemplate != null;
    }

    private TenantFieldConfigurationStore requireTestStore() {
        if (testStore == null) {
            throw fieldConfigError("TENANT_FIELD_PERSISTENCE_CONTRACT_MISSING", "persistence", "Tenant field configuration requires JDBC persistence or an explicit src/test fixture; refusing src/main process-local store-of-record fallback.");
        }
        return testStore;
    }

    private TenantFieldConfiguration validated(TenantFieldConfiguration command, Instant now) {
        if (command == null) {
            throw fieldConfigError("TENANT_FIELD_CONFIG_REQUIRED", "tenantFieldConfiguration", "Tenant field configuration is required.");
        }
        String tenantId = required(command.tenantId(), "tenantId");
        String surface = normalizedSurface(command.surface());
        String fieldId = required(command.fieldId(), "fieldId");
        FieldOrigin origin = command.origin() == null ? FieldOrigin.INHERITED_SYSTEM : command.origin();
        if (origin == FieldOrigin.INHERITED_SYSTEM && !hasText(command.systemFieldRef())) {
            throw fieldConfigError("TENANT_FIELD_SYSTEM_REF_REQUIRED", "systemFieldRef", "systemFieldRef is required for inherited system fields.");
        }
        if (origin == FieldOrigin.NATIVE && hasText(command.systemFieldRef())) {
            throw fieldConfigError("TENANT_FIELD_NATIVE_SYSTEM_REF_FORBIDDEN", "systemFieldRef", "Native tenant fields must not change or point to a system/default catalog field.");
        }
        return new TenantFieldConfiguration(
            defaulted(command.configurationId(), tenantId + ":" + surface + ":" + fieldId),
            tenantId,
            surface,
            fieldId,
            origin,
            trimToEmpty(command.systemFieldRef()),
            optionalTrim(command.nameAlias()),
            optionalTrim(command.descriptionAlias()),
            command.enabled(),
            command.omitted(),
            command.updatedAt() == null ? now : command.updatedAt(),
            defaulted(command.auditRef(), "tenant-field-config:" + tenantId + ":" + surface + ":" + fieldId)
        );
    }

    private static TenantFieldConfiguration scopedToTenantSurface(String tenantId, String surface, TenantFieldConfiguration command) {
        if (command == null) {
            throw fieldConfigError("TENANT_FIELD_CONFIG_REQUIRED", "tenantFieldConfiguration", "Tenant field configuration is required.");
        }
        return new TenantFieldConfiguration(command.configurationId(), tenantId, surface, command.fieldId(), command.origin(), command.systemFieldRef(), command.nameAlias(), command.descriptionAlias(), command.enabled(), command.omitted(), command.updatedAt(), command.auditRef());
    }

    private void applyPublishedSnapshot(String tenantId, String surface, Collection<TenantFieldConfiguration> snapshot) {
        requireTestStore().removeConfigurationsForTenantSurface(tenantId, surface);
        snapshot.forEach(configuration -> requireTestStore().saveConfiguration(configuration));
    }

    private static void validatePublishable(TenantFieldConfigurationDraft draft) {
        Set<String> fieldIds = new HashSet<>();
        for (TenantFieldConfiguration configuration : draft.configurations()) {
            String fieldId = normalize(configuration.fieldId());
            if (!fieldIds.add(fieldId)) {
                throw fieldConfigError("TENANT_FIELD_DUPLICATE_FIELD_ID", "fieldId", "Published tenant field configuration cannot contain duplicate field IDs.");
            }
        }
        draft.conditionFieldRefs().forEach((fieldId, refs) -> {
            if (!fieldIds.contains(normalize(fieldId))) {
                throw fieldConfigError("TENANT_FIELD_CONDITION_FIELD_MISSING", "conditionFieldRefs", "Condition references must belong to fields in the draft.");
            }
            for (String ref : refs) {
                if (!fieldIds.contains(normalize(ref))) {
                    throw fieldConfigError("TENANT_FIELD_CONDITION_REFERENCE_BROKEN", "conditionFieldRefs", "Condition references must point to fields in the draft.");
                }
            }
        });
    }

    private static Map<String, List<String>> normalizedConditionFieldRefs(Map<String, List<String>> conditionFieldRefs) {
        if (conditionFieldRefs == null || conditionFieldRefs.isEmpty()) {
            return Map.of();
        }
        return conditionFieldRefs.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> required(entry.getKey(), "conditionFieldRefs"),
                entry -> Optional.ofNullable(entry.getValue()).orElseGet(List::of).stream().map(ref -> required(ref, "conditionFieldRefs")).toList()
            ));
    }

    private TenantFieldConfigurationAuditRecord auditRecord(String tenantId, String userId, List<TenantFieldConfiguration> oldPublished, List<TenantFieldConfiguration> newPublished, String surface, String action) {
        return new TenantFieldConfigurationAuditRecord(tenantId, userId, oldPublished.toString(), newPublished.toString(), surface, clock.instant(), action);
    }

    private static String normalizedSurface(String surface) {
        String normalized = required(surface, "surface").toUpperCase(Locale.ROOT).replace('-', '_');
        for (TenantFieldSurface configuredSurface : TenantFieldSurface.values()) {
            if (configuredSurface.name().equals(normalized)) {
                return configuredSurface.name();
            }
        }
        throw fieldConfigError("TENANT_FIELD_SURFACE_UNSUPPORTED", "surface", "surface must be one of the configured tenant field surfaces.");
    }

    private static String required(String value, String fieldName) {
        if (!hasText(value)) {
            throw fieldConfigError("TENANT_FIELD_CONFIG_VALIDATION_FAILED", fieldName, fieldName + " is required.");
        }
        return value.trim();
    }

    private static String defaulted(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String optionalTrim(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static TenantFieldConfigException fieldConfigError(String code, String field, String message) {
        return new TenantFieldConfigException(code, List.of(new FieldError(field, code, message)));
    }

    public enum TenantFieldSurface {
        APPLICATION_FORM,
        PRODUCT_SPEC,
        PIPELINE_SETTINGS,
        CLIENT_SETTINGS,
        NOTIFICATION
    }

    public enum FieldOrigin {
        INHERITED_SYSTEM,
        NATIVE
    }

    public record TenantFieldConfiguration(
        String configurationId,
        String tenantId,
        String surface,
        String fieldId,
        FieldOrigin origin,
        String systemFieldRef,
        String nameAlias,
        String descriptionAlias,
        boolean enabled,
        boolean omitted,
        Instant updatedAt,
        String auditRef
    ) {
        boolean active() {
            return enabled && !omitted;
        }
    }

    public record TenantFieldConfigurationDraft(
        String draftId,
        String tenantId,
        String surface,
        List<TenantFieldConfiguration> configurations,
        Map<String, List<String>> conditionFieldRefs,
        Instant savedAt,
        String userId
    ) {
        public TenantFieldConfigurationDraft {
            configurations = List.copyOf(Optional.ofNullable(configurations).orElseGet(List::of));
            conditionFieldRefs = Map.copyOf(Optional.ofNullable(conditionFieldRefs).orElseGet(Map::of));
        }
    }

    public record TenantFieldConfigurationVersion(
        int versionNumber,
        String tenantId,
        String surface,
        List<TenantFieldConfiguration> configurations,
        List<TenantFieldConfiguration> previousConfigurations,
        Instant publishedAt,
        String userId
    ) {
        public TenantFieldConfigurationVersion {
            configurations = List.copyOf(Optional.ofNullable(configurations).orElseGet(List::of));
            previousConfigurations = List.copyOf(Optional.ofNullable(previousConfigurations).orElseGet(List::of));
        }
    }

    public record TenantFieldConfigurationAuditRecord(
        String tenantId,
        String userId,
        String oldValue,
        String newValue,
        String affectedSurface,
        Instant timestamp,
        String action
    ) { }

    public record FieldError(String field, String code, String message) { }

    public static class TenantFieldConfigException extends RuntimeException {
        private final String code;
        private final List<FieldError> fieldErrors;

        TenantFieldConfigException(String code, List<FieldError> fieldErrors) {
            super(code);
            this.code = code;
            this.fieldErrors = List.copyOf(Optional.ofNullable(fieldErrors).orElseGet(ArrayList::new));
        }

        public String code() {
            return code;
        }

        public List<FieldError> fieldErrors() {
            return fieldErrors;
        }
    }

    interface TenantFieldConfigurationStore {
        void saveConfiguration(TenantFieldConfiguration configuration);
        void removeConfigurationsForTenantSurface(String tenantId, String surface);
        List<TenantFieldConfiguration> configurationsForTenantSurface(String tenantId, String surface);
        Optional<TenantFieldConfiguration> configuration(String tenantId, String surface, String fieldId);
        void saveDraft(String tenantId, String surface, TenantFieldConfigurationDraft draft);
        Optional<TenantFieldConfigurationDraft> draftForTenantSurface(String tenantId, String surface);
        void removeDraft(String tenantId, String surface);
        int currentVersion(String tenantId, String surface);
        void saveCurrentVersion(String tenantId, String surface, int version);
        void appendVersion(String tenantId, String surface, TenantFieldConfigurationVersion version);
        List<TenantFieldConfigurationVersion> versionsForTenantSurface(String tenantId, String surface);
        void appendAuditRecord(TenantFieldConfigurationAuditRecord auditRecord);
        List<TenantFieldConfigurationAuditRecord> auditRecordsForTenant(String tenantId);
    }
}
