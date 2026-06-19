package com.wcpe.pricing.calculationfields;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Catalog adapter for calculation-sourced field metadata.
 *
 * <p>This API intentionally catalogs metadata only. It does not evaluate formulas, look up data tables, or
 * invent pricing calculation values.</p>
 */
public final class CalculationFieldCatalogApi {
    public static final String CALCULATION_FIELD_READ_PERMISSION = "pricing.calculation-fields.read";
    public static final String SOURCE_REFERENCE_FORM_FIELDS = "ReferenceFormfields.json";

    public CalculationFieldCatalogResponse catalogFields(
            String tenantId,
            CalculationFieldHeaders headers,
            CalculationFieldImport importPayload,
            TenantCalculationFieldConfig tenantConfig) {
        requireTenant(tenantId);
        requirePermission(headers, CALCULATION_FIELD_READ_PERMISSION);
        Objects.requireNonNull(importPayload, "calculation field import is required");

        TenantCalculationFieldConfig effectiveConfig = tenantConfig == null
                ? TenantCalculationFieldConfig.allowAll()
                : tenantConfig;
        Map<String, CalculationFieldCatalogEntry> entries = new LinkedHashMap<>();

        for (ImportedCalculationField field : importPayload.fields()) {
            String canonicalId = canonicalCalculationFieldId(field.id());
            if (canonicalId == null) {
                continue;
            }
            boolean unavailable = effectiveConfig.unavailableFieldIds().contains(canonicalId);
            if (unavailable && effectiveConfig.unavailableHandling() == UnavailableHandling.HIDE_UNAVAILABLE) {
                continue;
            }
            CalculationFieldCatalogEntry entry = new CalculationFieldCatalogEntry(
                    canonicalId,
                    requireNonBlank(field.name(), "calculation field name is required"),
                    requireNonBlank(field.valueType(), "calculation field value type is required"),
                    importPayload.source(),
                    field.description(),
                    !unavailable,
                    unavailable ? "UNAVAILABLE_FOR_TENANT" : "AVAILABLE",
                    field.id());
            entries.putIfAbsent(canonicalId, entry);
        }

        List<CalculationFieldCatalogEntry> sortedEntries = entries.values().stream()
                .sorted(Comparator.comparing(CalculationFieldCatalogEntry::id))
                .toList();
        return new CalculationFieldCatalogResponse(
                tenantId,
                sortedEntries,
                sortedEntries.stream().map(CalculationFieldCatalogApi::toSettingsOption).toList(),
                headers.correlationId());
    }

    public CalculationFieldImportValidationResult validateImportedFields(CalculationFieldImport importPayload) {
        Objects.requireNonNull(importPayload, "calculation field import is required");
        List<String> calculationFieldIds = new ArrayList<>();
        List<String> unsupportedFieldIds = new ArrayList<>();
        for (ImportedCalculationField field : importPayload.fields()) {
            String canonicalId = canonicalCalculationFieldId(field.id());
            if (canonicalId == null) {
                unsupportedFieldIds.add(field.id());
            } else {
                calculationFieldIds.add(canonicalId);
            }
        }
        return new CalculationFieldImportValidationResult(
                importPayload.source(),
                calculationFieldIds.stream().distinct().sorted().toList(),
                unsupportedFieldIds.stream().distinct().sorted().toList());
    }

    public static boolean isCalculationFieldId(String fieldId) {
        return canonicalCalculationFieldId(fieldId) != null;
    }

    public static String canonicalCalculationFieldId(String fieldId) {
        if (fieldId == null || fieldId.isBlank()) {
            return null;
        }
        String normalized = fieldId.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("calc@") || normalized.startsWith("calc-field@")) {
            return normalized;
        }
        int calcIndex = normalized.indexOf("-calc@");
        if (calcIndex >= 0) {
            return normalized.substring(calcIndex + 1);
        }
        int calcFieldIndex = normalized.indexOf("-calc-field@");
        if (calcFieldIndex >= 0) {
            return normalized.substring(calcFieldIndex + 1);
        }
        return null;
    }

    private static CalculationFieldSettingsOption toSettingsOption(CalculationFieldCatalogEntry entry) {
        return new CalculationFieldSettingsOption(
                entry.id(),
                entry.name(),
                entry.valueType(),
                entry.tenantAvailable(),
                entry.tenantAvailability());
    }

    private static void requireTenant(String tenantId) {
        requireNonBlank(tenantId, "tenant_id is required");
    }

    private static void requirePermission(CalculationFieldHeaders headers, String permission) {
        if (headers == null || !headers.permissions().contains(permission)) {
            throw new CalculationFieldCatalogException(permission + " permission is required");
        }
        requireNonBlank(headers.actorId(), "actor_id is required");
        requireNonBlank(headers.correlationId(), "correlation_id is required");
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CalculationFieldCatalogException(message);
        }
        return value;
    }

    public record CalculationFieldHeaders(Set<String> permissions, String actorId, String correlationId) {
        public CalculationFieldHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record CalculationFieldImport(String source, List<ImportedCalculationField> fields) {
        public CalculationFieldImport {
            source = source == null || source.isBlank() ? SOURCE_REFERENCE_FORM_FIELDS : source;
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record ImportedCalculationField(String id, String name, String valueType, String description) {
    }

    public record TenantCalculationFieldConfig(Set<String> unavailableFieldIds, UnavailableHandling unavailableHandling) {
        public TenantCalculationFieldConfig {
            unavailableFieldIds = unavailableFieldIds == null ? Set.of() : Set.copyOf(unavailableFieldIds);
            unavailableHandling = unavailableHandling == null ? UnavailableHandling.MARK_UNAVAILABLE : unavailableHandling;
        }

        public static TenantCalculationFieldConfig allowAll() {
            return new TenantCalculationFieldConfig(Set.of(), UnavailableHandling.MARK_UNAVAILABLE);
        }
    }

    public enum UnavailableHandling {
        HIDE_UNAVAILABLE,
        MARK_UNAVAILABLE
    }

    public record CalculationFieldCatalogEntry(
            String id,
            String name,
            String valueType,
            String source,
            String description,
            boolean tenantAvailable,
            String tenantAvailability,
            String sourceFieldId) {
    }

    public record CalculationFieldSettingsOption(
            String id,
            String label,
            String valueType,
            boolean tenantAvailable,
            String tenantAvailability) {
    }

    public record CalculationFieldCatalogResponse(
            String tenantId,
            List<CalculationFieldCatalogEntry> fields,
            List<CalculationFieldSettingsOption> settingsOptions,
            String correlationId) {
    }

    public record CalculationFieldImportValidationResult(
            String source,
            List<String> calculationFieldIds,
            List<String> unsupportedFieldIds) {
    }

    public static final class CalculationFieldCatalogException extends RuntimeException {
        public CalculationFieldCatalogException(String message) {
            super(message);
        }
    }
}
