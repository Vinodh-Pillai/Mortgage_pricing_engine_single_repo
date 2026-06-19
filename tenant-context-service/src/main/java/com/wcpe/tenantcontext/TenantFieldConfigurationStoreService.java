package com.wcpe.tenantcontext;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TenantFieldConfigurationStoreService {
    private final Map<TenantFieldKey, TenantFieldConfiguration> configurations = new ConcurrentHashMap<>();
    private final Map<TenantSurfaceKey, TenantFieldConfigurationDraft> drafts = new ConcurrentHashMap<>();
    private final Map<TenantSurfaceKey, List<TenantFieldConfigurationVersion>> versions = new ConcurrentHashMap<>();
    private final Map<TenantSurfaceKey, Integer> currentVersions = new ConcurrentHashMap<>();
    private final List<TenantFieldConfigurationAuditRecord> auditRecords = Collections.synchronizedList(new ArrayList<>());
    private final Clock clock;

    public TenantFieldConfigurationStoreService() {
        this(Clock.systemUTC());
    }

    TenantFieldConfigurationStoreService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public TenantFieldConfiguration save(TenantFieldConfiguration command) {
        TenantFieldConfiguration validated = validated(command, clock.instant());
        configurations.put(key(validated.tenantId(), validated.surface(), validated.fieldId()), validated);
        return validated;
    }

    public List<TenantFieldConfiguration> replaceTenantSurface(String tenantId, String surface, Collection<TenantFieldConfiguration> commands) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        configurations.keySet().removeIf(key -> same(key.tenantId(), normalizedTenantId) && same(key.surface(), normalizedSurface));
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
        return configurations.values().stream()
            .filter(configuration -> same(configuration.tenantId(), normalizedTenantId))
            .filter(configuration -> same(configuration.surface(), normalizedSurface))
            .sorted(Comparator.comparing(TenantFieldConfiguration::fieldId))
            .toList();
    }

    public List<TenantFieldConfiguration> activeForTenantSurface(String tenantId, String surface) {
        return storedForTenantSurface(tenantId, surface).stream()
            .filter(TenantFieldConfiguration::active)
            .toList();
    }

    public TenantFieldConfigurationDraft saveDraft(String tenantId, String surface, Collection<TenantFieldConfiguration> commands, String userId) {
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
        drafts.put(surfaceKey(normalizedTenantId, normalizedSurface), draft);
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
        drafts.put(surfaceKey(draft.tenantId(), draft.surface()), draftWithConditions);
        return draftWithConditions;
    }

    public Optional<TenantFieldConfigurationDraft> draftForTenantSurface(String tenantId, String surface) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        return Optional.ofNullable(drafts.get(surfaceKey(normalizedTenantId, normalizedSurface)));
    }

    public TenantFieldConfigurationVersion publishDraft(String tenantId, String surface, String userId) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        TenantSurfaceKey tenantSurfaceKey = surfaceKey(normalizedTenantId, normalizedSurface);
        TenantFieldConfigurationDraft draft = Optional.ofNullable(drafts.get(tenantSurfaceKey))
            .orElseThrow(() -> fieldConfigError("TENANT_FIELD_DRAFT_MISSING", "draft", "A draft is required before publishing tenant field configuration."));
        validatePublishable(draft);

        List<TenantFieldConfiguration> oldPublished = storedForTenantSurface(normalizedTenantId, normalizedSurface);
        List<TenantFieldConfiguration> newPublished = draft.configurations().stream()
            .map(command -> validated(scopedToTenantSurface(normalizedTenantId, normalizedSurface, command), clock.instant()))
            .toList();
        int versionNumber = currentVersions.getOrDefault(tenantSurfaceKey, 0) + 1;
        applyPublishedSnapshot(normalizedTenantId, normalizedSurface, newPublished);
        TenantFieldConfigurationVersion version = new TenantFieldConfigurationVersion(versionNumber, normalizedTenantId, normalizedSurface, newPublished, oldPublished, clock.instant(), required(userId, "userId"));
        versions.computeIfAbsent(tenantSurfaceKey, ignored -> Collections.synchronizedList(new ArrayList<>())).add(version);
        currentVersions.put(tenantSurfaceKey, versionNumber);
        drafts.remove(tenantSurfaceKey);
        auditRecords.add(auditRecord(normalizedTenantId, required(userId, "userId"), oldPublished, newPublished, normalizedSurface, "PUBLISH"));
        return version;
    }

    public TenantFieldConfigurationVersion rollbackToPreviousVersion(String tenantId, String surface, String userId) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        TenantSurfaceKey tenantSurfaceKey = surfaceKey(normalizedTenantId, normalizedSurface);
        List<TenantFieldConfigurationVersion> history = versions.getOrDefault(tenantSurfaceKey, List.of());
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
        int rollbackVersionNumber = currentVersions.getOrDefault(tenantSurfaceKey, current.versionNumber()) + 1;
        applyPublishedSnapshot(normalizedTenantId, normalizedSurface, restored);
        TenantFieldConfigurationVersion rollbackVersion = new TenantFieldConfigurationVersion(rollbackVersionNumber, normalizedTenantId, normalizedSurface, restored, oldPublished, clock.instant(), required(userId, "userId"));
        versions.computeIfAbsent(tenantSurfaceKey, ignored -> Collections.synchronizedList(new ArrayList<>())).add(rollbackVersion);
        currentVersions.put(tenantSurfaceKey, rollbackVersionNumber);
        auditRecords.add(auditRecord(normalizedTenantId, required(userId, "userId"), oldPublished, restored, normalizedSurface, "ROLLBACK"));
        return rollbackVersion;
    }

    public List<TenantFieldConfigurationVersion> publishedVersions(String tenantId, String surface) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        return List.copyOf(versions.getOrDefault(surfaceKey(normalizedTenantId, normalizedSurface), List.of()));
    }

    public List<TenantFieldConfigurationAuditRecord> auditRecordsForTenant(String tenantId) {
        String normalizedTenantId = required(tenantId, "tenantId");
        synchronized (auditRecords) {
            return auditRecords.stream()
                .filter(record -> same(record.tenantId(), normalizedTenantId))
                .sorted(Comparator.comparing(TenantFieldConfigurationAuditRecord::timestamp))
                .toList();
        }
    }

    public Optional<TenantFieldConfiguration> activeField(String tenantId, String surface, String fieldId) {
        String normalizedTenantId = required(tenantId, "tenantId");
        String normalizedSurface = normalizedSurface(surface);
        String normalizedFieldId = required(fieldId, "fieldId");
        return Optional.ofNullable(configurations.get(key(normalizedTenantId, normalizedSurface, normalizedFieldId)))
            .filter(TenantFieldConfiguration::active);
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

    private static TenantFieldKey key(String tenantId, String surface, String fieldId) {
        return new TenantFieldKey(normalize(tenantId), normalize(surface), normalize(fieldId));
    }

    private static TenantSurfaceKey surfaceKey(String tenantId, String surface) {
        return new TenantSurfaceKey(normalize(tenantId), normalize(surface));
    }

    private void applyPublishedSnapshot(String tenantId, String surface, Collection<TenantFieldConfiguration> snapshot) {
        configurations.keySet().removeIf(key -> same(key.tenantId(), tenantId) && same(key.surface(), surface));
        snapshot.forEach(configuration -> configurations.put(key(configuration.tenantId(), configuration.surface(), configuration.fieldId()), configuration));
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

    private record TenantFieldKey(String tenantId, String surface, String fieldId) { }

    private record TenantSurfaceKey(String tenantId, String surface) { }

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
}
