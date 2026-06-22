package com.wcpe.pricing.calculationtables;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant-scoped management API for calculation data-table lookups.
 *
 * <p>The API stores governed lookup metadata and caller-supplied option values only. Runtime lookup returns a
 * blocked/missing-data result when no published option matches; it never derives or invents mortgage pricing values.</p>
 */
public final class CalculationDataTableLookupApi {
    public static final String LOOKUP_ADMIN_WRITE_PERMISSION = "pricing.calculation-lookups.write";
    public static final String LOOKUP_ADMIN_PUBLISH_PERMISSION = "pricing.calculation-lookups.publish";
    public static final String LOOKUP_READ_PERMISSION = "pricing.calculation-lookups.read";

    private final CalculationDataTableLookupRepository repository;

    public CalculationDataTableLookupApi(CalculationDataTableLookupRepository repository) {
        this.repository = Objects.requireNonNull(repository, "calculation data table repository is required");
    }

    public LookupCreateResponse createLookup(String tenantId, LookupHeaders headers, CreateLookupRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, LOOKUP_ADMIN_WRITE_PERMISSION);
        validateCreateRequest(request);

        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();
        int versionNumber = repository.nextVersionNumber(tenantId, request.tableId());
        CalculationDataTableVersion version = new CalculationDataTableVersion(
                versionId,
                tenantId,
                request.tableId(),
                requireNonBlank(request.displayName(), "lookup display_name is required"),
                request.tenantScope(),
                request.keyFields(),
                versionNumber,
                LookupVersionStatus.DRAFT,
                toOptions(request.keyFields(), request.options()),
                request.description(),
                headers.actorId(),
                null,
                now,
                now);
        repository.saveVersion(version);
        saveEvent(tenantId, request.tableId(), versionId, "pricing.calculation-lookup-created.v1", headers,
                Map.of("versionNumber", String.valueOf(versionNumber), "optionCount", String.valueOf(version.options().size())));

        return new LookupCreateResponse(request.tableId(), versionId, versionNumber, LookupVersionStatus.DRAFT,
                request.keyFields(), request.tenantScope(), version.options().size(), "audit:" + versionId,
                headers.correlationId());
    }

    public LookupDraftResponse editLookupOptions(String tenantId, LookupHeaders headers, String tableId,
            EditLookupOptionsRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, LOOKUP_ADMIN_WRITE_PERMISSION);
        tableId = requireNonBlank(tableId, "lookup table_id is required");
        Objects.requireNonNull(request, "lookup option edit request is required");
        CalculationDataTableVersion base = repository.findLatestVersion(tenantId, tableId)
                .orElseThrow(() -> new CalculationDataTableLookupException("LOOKUP_TABLE_NOT_FOUND"));
        validateOptions(base.keyFields(), request.options());

        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();
        int versionNumber = repository.nextVersionNumber(tenantId, tableId);
        CalculationDataTableVersion edited = new CalculationDataTableVersion(
                versionId,
                tenantId,
                tableId,
                base.displayName(),
                base.tenantScope(),
                base.keyFields(),
                versionNumber,
                LookupVersionStatus.DRAFT,
                toOptions(base.keyFields(), request.options()),
                request.description() == null ? base.description() : request.description(),
                headers.actorId(),
                null,
                now,
                now);
        repository.saveVersion(edited);
        saveEvent(tenantId, tableId, versionId, "pricing.calculation-lookup-options-edited.v1", headers,
                Map.of("versionNumber", String.valueOf(versionNumber), "optionCount", String.valueOf(edited.options().size())));
        return new LookupDraftResponse(tableId, versionId, versionNumber, LookupVersionStatus.DRAFT,
                edited.options().size(), "audit:" + versionId, headers.correlationId());
    }

    public LookupPublishResponse publishLookupOptions(String tenantId, LookupHeaders headers, UUID versionId) {
        requireTenant(tenantId);
        requirePermission(headers, LOOKUP_ADMIN_PUBLISH_PERMISSION);
        CalculationDataTableVersion version = repository.findVersion(tenantId, versionId)
                .orElseThrow(() -> new CalculationDataTableLookupException("LOOKUP_VERSION_NOT_FOUND"));
        if (version.status() == LookupVersionStatus.PUBLISHED) {
            throw new CalculationDataTableLookupException("LOOKUP_VERSION_ALREADY_PUBLISHED");
        }

        CalculationDataTableVersion published = version.withPublication(headers.actorId(), Instant.now());
        repository.replaceVersion(published);
        saveEvent(tenantId, published.tableId(), versionId, "pricing.calculation-lookup-published.v1", headers,
                Map.of("versionNumber", String.valueOf(published.versionNumber()),
                        "optionCount", String.valueOf(published.options().size())));
        return new LookupPublishResponse(published.tableId(), versionId, published.versionNumber(),
                LookupVersionStatus.PUBLISHED, published.options().size(), "audit:" + versionId,
                headers.correlationId());
    }

    public LookupPublishResponse deactivateLookupTable(String tenantId, LookupHeaders headers, String tableId) {
        requireTenant(tenantId);
        requirePermission(headers, LOOKUP_ADMIN_PUBLISH_PERMISSION);
        tableId = requireNonBlank(tableId, "lookup table_id is required");
        CalculationDataTableVersion published = repository.findLatestPublishedVersion(tenantId, tableId)
                .orElseThrow(() -> new CalculationDataTableLookupException("LOOKUP_PUBLISHED_VERSION_NOT_FOUND"));

        CalculationDataTableVersion inactive = published.withStatus(LookupVersionStatus.INACTIVE, headers.actorId(), Instant.now());
        repository.replaceVersion(inactive);
        saveEvent(tenantId, tableId, inactive.versionId(), "pricing.calculation-lookup-deactivated.v1", headers,
                Map.of("versionNumber", String.valueOf(inactive.versionNumber()),
                        "optionCount", String.valueOf(inactive.options().size())));
        return new LookupPublishResponse(tableId, inactive.versionId(), inactive.versionNumber(), LookupVersionStatus.INACTIVE,
                inactive.options().size(), "audit:" + inactive.versionId(), headers.correlationId());
    }

    public LookupReferenceValidationResult validateCalculationReferences(String tenantId, LookupHeaders headers,
            CalculationLookupValidationRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, LOOKUP_READ_PERMISSION);
        Objects.requireNonNull(request, "calculation lookup validation request is required");

        List<String> errors = new ArrayList<>();
        for (CalculationLookupReference reference : request.references()) {
            String tableId = requireNonBlank(reference.tableId(), "lookup reference table_id is required");
            Optional<CalculationDataTableVersion> published = repository.findLatestPublishedVersion(tenantId, tableId);
            if (published.isEmpty()) {
                if (repository.findLatestVersion(tenantId, tableId).isPresent()) {
                    errors.add("LOOKUP_DEPENDENCY_ERROR:TABLE_INACTIVE_OR_UNPUBLISHED:" + tableId);
                } else {
                    errors.add("TABLE_NOT_FOUND:" + tableId);
                }
                continue;
            }
            Set<String> suppliedKeys = normalizedKeySet(reference.requiredKeyFields());
            List<String> missingKeys = published.get().keyFields().stream()
                    .filter(key -> !suppliedKeys.contains(key))
                    .sorted()
                    .toList();
            if (!missingKeys.isEmpty()) {
                errors.add("LOOKUP_DEPENDENCY_ERROR:MISSING_REQUIRED_KEYS:" + tableId + ":" + String.join(",", missingKeys));
            }
        }
        LookupReferenceValidationStatus status = errors.isEmpty()
                ? LookupReferenceValidationStatus.VALID
                : LookupReferenceValidationStatus.INVALID;
        return new LookupReferenceValidationResult(request.calculationId(), status, errors, headers.correlationId());
    }

    public LookupRuntimeResult lookupValue(String tenantId, LookupHeaders headers, LookupValueRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, LOOKUP_READ_PERMISSION);
        Objects.requireNonNull(request, "lookup value request is required");
        String tableId = requireNonBlank(request.tableId(), "lookup table_id is required");
        Optional<CalculationDataTableVersion> published = repository.findLatestPublishedVersion(tenantId, tableId);
        if (published.isEmpty()) {
            String reason = repository.findLatestVersion(tenantId, tableId).isPresent()
                    ? "LOOKUP_DEPENDENCY_ERROR:TABLE_INACTIVE_OR_UNPUBLISHED"
                    : "LOOKUP_TABLE_NOT_FOUND";
            LookupRuntimeResult result = LookupRuntimeResult.missing(tableId, null, reason, headers.correlationId());
            auditLookupIfEnabled(tenantId, tableId, null, request.keyValues(), null, result, request.auditEnabled(), headers,
                    "pricing.calculation-lookup-executed.v1");
            return result;
        }

        return lookupAgainstVersion(tenantId, headers, tableId, published.get(), request.keyValues(), request.auditEnabled(),
                "pricing.calculation-lookup-executed.v1");
    }

    public LookupRuntimeResult lookupHistoricalValue(String tenantId, LookupHeaders headers, HistoricalLookupValueRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, LOOKUP_READ_PERMISSION);
        Objects.requireNonNull(request, "historical lookup value request is required");
        String tableId = requireNonBlank(request.tableId(), "lookup table_id is required");
        CalculationDataTableVersion version = repository.findVersion(tenantId, request.versionId())
                .orElseThrow(() -> new CalculationDataTableLookupException("LOOKUP_VERSION_NOT_FOUND"));
        if (!version.tableId().equals(tableId)) {
            throw new CalculationDataTableLookupException("LOOKUP_VERSION_TABLE_MISMATCH");
        }
        if (version.status() == LookupVersionStatus.DRAFT) {
            LookupRuntimeResult result = LookupRuntimeResult.missing(tableId, version.versionId(),
                    "LOOKUP_DEPENDENCY_ERROR:VERSION_NOT_PUBLISHED", headers.correlationId());
            auditLookupIfEnabled(tenantId, tableId, version.versionId(), request.keyValues(), null, result,
                    request.auditEnabled(), headers, "pricing.calculation-lookup-historical-replayed.v1");
            return result;
        }

        return lookupAgainstVersion(tenantId, headers, tableId, version, request.keyValues(), request.auditEnabled(),
                "pricing.calculation-lookup-historical-replayed.v1");
    }

    private LookupRuntimeResult lookupAgainstVersion(String tenantId, LookupHeaders headers, String tableId,
            CalculationDataTableVersion version, Map<String, String> keyValues, boolean auditEnabled, String auditEventType) {
        List<String> missingRequestKeys = version.keyFields().stream()
                .filter(key -> keyValues == null || !keyValues.containsKey(key)
                        || keyValues.get(key) == null || keyValues.get(key).isBlank())
                .toList();
        if (!missingRequestKeys.isEmpty()) {
            LookupRuntimeResult result = LookupRuntimeResult.missing(tableId, version.versionId(),
                    "LOOKUP_DEPENDENCY_ERROR:MISSING_REQUIRED_KEYS:" + String.join(",", missingRequestKeys),
                    headers.correlationId());
            auditLookupIfEnabled(tenantId, tableId, version.versionId(), keyValues, null, result, auditEnabled, headers,
                    auditEventType);
            return result;
        }

        String optionKey = optionKey(version.keyFields(), keyValues);
        LookupOption option = version.options().get(optionKey);
        if (option == null) {
            LookupRuntimeResult result = LookupRuntimeResult.missing(tableId, version.versionId(), "LOOKUP_VALUE_MISSING",
                    headers.correlationId());
            auditLookupIfEnabled(tenantId, tableId, version.versionId(), keyValues, null, result, auditEnabled, headers,
                    auditEventType);
            return result;
        }
        LookupRuntimeResult result = new LookupRuntimeResult(tableId, version.versionId(), LookupRuntimeStatus.FOUND, option.value(), null,
                headers.correlationId());
        auditLookupIfEnabled(tenantId, tableId, version.versionId(), keyValues, optionKey, result, auditEnabled, headers,
                auditEventType);
        return result;
    }

    private void auditLookupIfEnabled(String tenantId, String tableId, UUID versionId, Map<String, String> keyValues,
            String matchedRow, LookupRuntimeResult result, boolean auditEnabled, LookupHeaders headers, String eventType) {
        if (!auditEnabled) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("keyInputs", new TreeMap<>(keyValues == null ? Map.of() : keyValues).toString());
        details.put("matchedRow", matchedRow == null ? "" : matchedRow);
        details.put("tableVersion", versionId == null ? "" : versionId.toString());
        details.put("result", result.status().name());
        if (result.missingReason() != null) {
            details.put("missingReason", result.missingReason());
        }
        saveEvent(tenantId, tableId, versionId, eventType, headers, details);
    }

    private void saveEvent(String tenantId, String tableId, UUID versionId, String eventType, LookupHeaders headers,
            Map<String, String> details) {
        repository.saveEvent(new LookupAuditEvent(UUID.randomUUID(), tenantId, tableId, versionId, eventType,
                headers.actorId(), headers.correlationId(), Instant.now(), details));
    }

    private static void validateCreateRequest(CreateLookupRequest request) {
        Objects.requireNonNull(request, "lookup create request is required");
        requireNonBlank(request.tableId(), "lookup table_id is required");
        requireNonBlank(request.tenantScope(), "lookup tenant_scope is required");
        validateKeyFields(request.keyFields());
        validateOptions(request.keyFields(), request.options());
    }

    private static void validateKeyFields(List<String> keyFields) {
        if (keyFields == null || keyFields.isEmpty()) {
            throw new CalculationDataTableLookupException("LOOKUP_KEY_FIELDS_REQUIRED");
        }
        Set<String> unique = new HashSet<>();
        for (String keyField : keyFields) {
            String normalized = requireNonBlank(keyField, "lookup key field is required");
            if (!unique.add(normalized)) {
                throw new CalculationDataTableLookupException("LOOKUP_DUPLICATE_KEY_FIELD");
            }
        }
    }

    private static void validateOptions(List<String> keyFields, List<LookupOptionDraft> options) {
        if (options == null || options.isEmpty()) {
            throw new CalculationDataTableLookupException("LOOKUP_OPTIONS_REQUIRED");
        }
        Set<String> uniqueKeys = new HashSet<>();
        for (LookupOptionDraft option : options) {
            Objects.requireNonNull(option, "lookup option is required");
            requireNonBlank(option.value(), "lookup option value is required");
            String key = optionKey(keyFields, option.keyValues());
            if (!uniqueKeys.add(key)) {
                throw new CalculationDataTableLookupException("LOOKUP_DUPLICATE_OPTION_KEY");
            }
        }
    }

    private static Map<String, LookupOption> toOptions(List<String> keyFields, List<LookupOptionDraft> drafts) {
        Map<String, LookupOption> options = new LinkedHashMap<>();
        for (LookupOptionDraft draft : drafts) {
            Map<String, String> orderedKeys = orderedKeys(keyFields, draft.keyValues());
            options.put(optionKey(keyFields, orderedKeys), new LookupOption(orderedKeys, draft.value(), draft.label()));
        }
        return Map.copyOf(options);
    }

    private static String optionKey(List<String> keyFields, Map<String, String> keyValues) {
        Map<String, String> orderedKeys = orderedKeys(keyFields, keyValues);
        List<String> parts = new ArrayList<>();
        for (String keyField : keyFields) {
            parts.add(keyField + "=" + orderedKeys.get(keyField));
        }
        return String.join("|", parts);
    }

    private static Map<String, String> orderedKeys(List<String> keyFields, Map<String, String> keyValues) {
        Objects.requireNonNull(keyValues, "lookup option key values are required");
        Map<String, String> ordered = new TreeMap<>();
        for (String keyField : keyFields) {
            String value = keyValues.get(keyField);
            if (value == null || value.isBlank()) {
                throw new CalculationDataTableLookupException("LOOKUP_OPTION_MISSING_KEY:" + keyField);
            }
            ordered.put(keyField, value);
        }
        return Map.copyOf(ordered);
    }

    private static Set<String> normalizedKeySet(Set<String> keyFields) {
        if (keyFields == null) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String keyField : keyFields) {
            if (keyField != null && !keyField.isBlank()) {
                normalized.add(keyField);
            }
        }
        return normalized;
    }

    private static void requireTenant(String tenantId) {
        requireNonBlank(tenantId, "tenant_id is required");
    }

    private static void requirePermission(LookupHeaders headers, String permission) {
        if (headers == null || !headers.permissions().contains(permission)) {
            throw new CalculationDataTableLookupException(permission + " permission is required");
        }
        requireNonBlank(headers.actorId(), "actor_id is required");
        requireNonBlank(headers.correlationId(), "correlation_id is required");
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CalculationDataTableLookupException(message);
        }
        return value;
    }

    public interface CalculationDataTableLookupRepository {
        int nextVersionNumber(String tenantId, String tableId);

        void saveVersion(CalculationDataTableVersion version);

        void replaceVersion(CalculationDataTableVersion version);

        Optional<CalculationDataTableVersion> findVersion(String tenantId, UUID versionId);

        Optional<CalculationDataTableVersion> findLatestVersion(String tenantId, String tableId);

        Optional<CalculationDataTableVersion> findLatestPublishedVersion(String tenantId, String tableId);

        void saveEvent(LookupAuditEvent event);

        List<LookupAuditEvent> events();
    }

    public record LookupHeaders(Set<String> permissions, String actorId, String correlationId) {
        public LookupHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record CreateLookupRequest(
            String tableId,
            String displayName,
            List<String> keyFields,
            List<LookupOptionDraft> options,
            String tenantScope,
            String description) {
        public CreateLookupRequest {
            keyFields = keyFields == null ? List.of() : List.copyOf(keyFields);
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record EditLookupOptionsRequest(List<LookupOptionDraft> options, String description) {
        public EditLookupOptionsRequest {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record LookupOptionDraft(Map<String, String> keyValues, String value, String label) {
        public LookupOptionDraft {
            keyValues = keyValues == null ? Map.of() : Map.copyOf(keyValues);
        }
    }

    public record LookupOption(Map<String, String> keyValues, String value, String label) {
        public LookupOption {
            keyValues = keyValues == null ? Map.of() : Map.copyOf(keyValues);
        }
    }

    public record CalculationLookupValidationRequest(String calculationId, List<CalculationLookupReference> references) {
        public CalculationLookupValidationRequest {
            references = references == null ? List.of() : List.copyOf(references);
        }
    }

    public record CalculationLookupReference(String tableId, Set<String> requiredKeyFields) {
        public CalculationLookupReference {
            requiredKeyFields = requiredKeyFields == null ? Set.of() : Set.copyOf(requiredKeyFields);
        }
    }

    public record LookupValueRequest(String tableId, Map<String, String> keyValues, boolean auditEnabled) {
        public LookupValueRequest(String tableId, Map<String, String> keyValues) {
            this(tableId, keyValues, false);
        }

        public LookupValueRequest {
            keyValues = keyValues == null ? Map.of() : Map.copyOf(keyValues);
        }
    }

    public record HistoricalLookupValueRequest(UUID versionId, String tableId, Map<String, String> keyValues,
            boolean auditEnabled) {
        public HistoricalLookupValueRequest {
            keyValues = keyValues == null ? Map.of() : Map.copyOf(keyValues);
        }
    }

    public record CalculationDataTableVersion(
            UUID versionId,
            String tenantId,
            String tableId,
            String displayName,
            String tenantScope,
            List<String> keyFields,
            int versionNumber,
            LookupVersionStatus status,
            Map<String, LookupOption> options,
            String description,
            String createdBy,
            String publishedBy,
            Instant createdAt,
            Instant updatedAt) {
        public CalculationDataTableVersion {
            keyFields = keyFields == null ? List.of() : List.copyOf(keyFields);
            options = options == null ? Map.of() : Map.copyOf(options);
        }

        CalculationDataTableVersion withPublication(String actorId, Instant publishedAt) {
            return new CalculationDataTableVersion(versionId, tenantId, tableId, displayName, tenantScope, keyFields,
                    versionNumber, LookupVersionStatus.PUBLISHED, options, description, createdBy, actorId, createdAt,
                    publishedAt);
        }

        CalculationDataTableVersion withStatus(LookupVersionStatus nextStatus, String actorId, Instant updatedAt) {
            return new CalculationDataTableVersion(versionId, tenantId, tableId, displayName, tenantScope, keyFields,
                    versionNumber, nextStatus, options, description, createdBy, actorId, createdAt, updatedAt);
        }
    }

    public enum LookupVersionStatus {
        DRAFT,
        PUBLISHED,
        INACTIVE
    }

    public enum LookupReferenceValidationStatus {
        VALID,
        INVALID
    }

    public enum LookupRuntimeStatus {
        FOUND,
        BLOCKED_MISSING_DATA
    }

    public record LookupCreateResponse(
            String tableId,
            UUID versionId,
            int versionNumber,
            LookupVersionStatus status,
            List<String> keyFields,
            String tenantScope,
            int optionCount,
            String auditRef,
            String correlationId) {
    }

    public record LookupDraftResponse(
            String tableId,
            UUID versionId,
            int versionNumber,
            LookupVersionStatus status,
            int optionCount,
            String auditRef,
            String correlationId) {
    }

    public record LookupPublishResponse(
            String tableId,
            UUID versionId,
            int versionNumber,
            LookupVersionStatus status,
            int optionCount,
            String auditRef,
            String correlationId) {
    }

    public record LookupReferenceValidationResult(
            String calculationId,
            LookupReferenceValidationStatus status,
            List<String> errors,
            String correlationId) {
    }

    public record LookupRuntimeResult(
            String tableId,
            UUID versionId,
            LookupRuntimeStatus status,
            String value,
            String missingReason,
            String correlationId) {
        static LookupRuntimeResult missing(String tableId, UUID versionId, String missingReason, String correlationId) {
            return new LookupRuntimeResult(tableId, versionId, LookupRuntimeStatus.BLOCKED_MISSING_DATA, null,
                    missingReason, correlationId);
        }
    }

    public record LookupAuditEvent(
            UUID eventId,
            String tenantId,
            String tableId,
            UUID versionId,
            String eventType,
            String actorId,
            String correlationId,
            Instant occurredAt,
            Map<String, String> details) {
        public LookupAuditEvent {
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }

    public static final class CalculationDataTableLookupException extends RuntimeException {
        public CalculationDataTableLookupException(String message) {
            super(message);
        }
    }
}
