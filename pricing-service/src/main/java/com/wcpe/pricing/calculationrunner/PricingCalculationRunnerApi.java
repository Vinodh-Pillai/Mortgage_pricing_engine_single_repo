package com.wcpe.pricing.calculationrunner;

import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupHeaders;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupRuntimeStatus;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupValueRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Governed runner for approved pricing calculation definitions.
 *
 * <p>The runner executes caller-supplied definition steps only. It can copy required input values or read a governed
 * tenant-scoped lookup table through {@link CalculationDataTableLookupApi}; it does not author formulas, derive rates,
 * or invent fallback pricing values.</p>
 */
public final class PricingCalculationRunnerApi {
    public static final String CALCULATION_RUN_PERMISSION = "pricing.calculations.run";

    private final CalculationDataTableLookupApi lookupApi;

    public PricingCalculationRunnerApi(CalculationDataTableLookupApi lookupApi) {
        this.lookupApi = Objects.requireNonNull(lookupApi, "calculation lookup api is required");
    }

    public CalculationRunResult runPricingCalculation(String tenantId, CalculationRunHeaders headers,
            CalculationRunRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, CALCULATION_RUN_PERMISSION);
        Objects.requireNonNull(request, "calculation run request is required");

        if (request.definition() == null) {
            return blocked(tenantId, null, null, CalculationRunStatus.BLOCKED_MISSING_CONFIGURATION,
                    "CALCULATION_DEFINITION_MISSING", headers.correlationId(), List.of(), List.of(), List.of());
        }

        CalculationDefinition definition = request.definition();
        String calculationId = requireNonBlank(definition.calculationId(), "calculation_id is required");
        String versionId = requireNonBlank(definition.versionId(), "calculation definition version_id is required");
        String definitionTenantId = requireNonBlank(definition.tenantId(), "calculation definition tenant_id is required");
        if (!tenantId.equals(definitionTenantId)) {
            return blocked(tenantId, calculationId, versionId, CalculationRunStatus.BLOCKED_MISSING_CONFIGURATION,
                    "CALCULATION_DEFINITION_TENANT_MISMATCH", headers.correlationId(), List.of(), List.of(), List.of());
        }
        if (definition.outputSteps().isEmpty()) {
            return blocked(tenantId, calculationId, versionId, CalculationRunStatus.BLOCKED_MISSING_CONFIGURATION,
                    "CALCULATION_OUTPUT_STEPS_MISSING", headers.correlationId(), List.of(), List.of(), List.of());
        }

        Map<String, String> inputs = request.inputs();
        List<CalculationOutput> outputs = new ArrayList<>();
        List<String> inputFieldIds = new ArrayList<>();
        List<UUID> lookupVersionIds = new ArrayList<>();

        for (CalculationOutputStep step : definition.outputSteps()) {
            String outputFieldId = requireNonBlank(step.outputFieldId(), "calculation output field_id is required");
            if (!CalculationFieldCatalogApi.isCalculationFieldId(outputFieldId)) {
                return blocked(tenantId, calculationId, versionId, CalculationRunStatus.BLOCKED_MISSING_CONFIGURATION,
                        "OUTPUT_FIELD_NOT_CALCULATION_FIELD:" + outputFieldId, headers.correlationId(), outputs,
                        inputFieldIds, lookupVersionIds);
            }

            if (step.source() == CalculationStepSource.INPUT_VALUE) {
                String inputFieldId = requireNonBlank(step.inputFieldId(), "calculation input field_id is required");
                inputFieldIds.add(inputFieldId);
                String value = inputs.get(inputFieldId);
                if (value == null || value.isBlank()) {
                    return blocked(tenantId, calculationId, versionId, CalculationRunStatus.BLOCKED_MISSING_INPUT,
                            "INPUT_VALUE_MISSING:" + inputFieldId, headers.correlationId(), outputs, inputFieldIds,
                            lookupVersionIds);
                }
                outputs.add(new CalculationOutput(outputFieldId, value, CalculationStepSource.INPUT_VALUE,
                        new CalculationOutputTrace(null, null, Map.of(inputFieldId, value))));
                continue;
            }

            if (step.source() == CalculationStepSource.LOOKUP_VALUE) {
                LookupStepResolution resolution = resolveLookupStep(tenantId, headers, step, inputs);
                inputFieldIds.addAll(resolution.inputFieldIds());
                if (resolution.blockerCode() != null) {
                    return blocked(tenantId, calculationId, versionId, resolution.status(), resolution.blockerCode(),
                            headers.correlationId(), outputs, inputFieldIds, lookupVersionIds);
                }
                lookupVersionIds.add(resolution.lookupVersionId());
                outputs.add(new CalculationOutput(outputFieldId, resolution.value(), CalculationStepSource.LOOKUP_VALUE,
                        new CalculationOutputTrace(step.lookupTableId(), resolution.lookupVersionId(), resolution.keyValues())));
                continue;
            }

            return blocked(tenantId, calculationId, versionId, CalculationRunStatus.BLOCKED_MISSING_CONFIGURATION,
                    "CALCULATION_STEP_SOURCE_UNSUPPORTED", headers.correlationId(), outputs, inputFieldIds,
                    lookupVersionIds);
        }

        return new CalculationRunResult(tenantId, calculationId, versionId, CalculationRunStatus.COMPLETED,
                List.copyOf(outputs), null, audit(tenantId, calculationId, versionId, outputs, inputFieldIds,
                        lookupVersionIds, headers.correlationId()));
    }

    private LookupStepResolution resolveLookupStep(String tenantId, CalculationRunHeaders headers,
            CalculationOutputStep step, Map<String, String> inputs) {
        String tableId = requireNonBlank(step.lookupTableId(), "lookup table_id is required");
        if (step.lookupKeyInputBindings().isEmpty()) {
            return LookupStepResolution.blocked(CalculationRunStatus.BLOCKED_MISSING_CONFIGURATION,
                    "LOOKUP_KEY_BINDINGS_MISSING:" + tableId, List.of());
        }
        Map<String, String> keyValues = new LinkedHashMap<>();
        List<String> inputFieldIds = new ArrayList<>();
        for (Map.Entry<String, String> binding : step.lookupKeyInputBindings().entrySet()) {
            String lookupKey = requireNonBlank(binding.getKey(), "lookup key field is required");
            String inputFieldId = requireNonBlank(binding.getValue(), "lookup key input field_id is required");
            inputFieldIds.add(inputFieldId);
            String inputValue = inputs.get(inputFieldId);
            if (inputValue == null || inputValue.isBlank()) {
                return LookupStepResolution.blocked(CalculationRunStatus.BLOCKED_MISSING_INPUT,
                        "LOOKUP_INPUT_VALUE_MISSING:" + inputFieldId, inputFieldIds);
            }
            keyValues.put(lookupKey, inputValue);
        }

        var lookup = lookupApi.lookupValue(tenantId,
                new LookupHeaders(Set.of(CalculationDataTableLookupApi.LOOKUP_READ_PERMISSION), headers.actorId(),
                        headers.correlationId()),
                new LookupValueRequest(tableId, keyValues));
        if (lookup.status() != LookupRuntimeStatus.FOUND) {
            return LookupStepResolution.blocked(CalculationRunStatus.BLOCKED_MISSING_DATA,
                    lookup.missingReason() == null ? "LOOKUP_VALUE_MISSING:" + tableId : lookup.missingReason(),
                    inputFieldIds);
        }
        return LookupStepResolution.found(lookup.value(), lookup.versionId(), keyValues, inputFieldIds);
    }

    private static CalculationRunResult blocked(String tenantId, String calculationId, String versionId,
            CalculationRunStatus status, String blockerCode, String correlationId, List<CalculationOutput> outputs,
            List<String> inputFieldIds, List<UUID> lookupVersionIds) {
        return new CalculationRunResult(tenantId, calculationId, versionId, status, List.copyOf(outputs), blockerCode,
                audit(tenantId, calculationId, versionId, outputs, inputFieldIds, lookupVersionIds, correlationId));
    }

    private static CalculationAuditTrace audit(String tenantId, String calculationId, String versionId,
            List<CalculationOutput> outputs, List<String> inputFieldIds, List<UUID> lookupVersionIds,
            String correlationId) {
        return new CalculationAuditTrace(tenantId, calculationId, versionId,
                inputFieldIds.stream().distinct().sorted().toList(),
                outputs.stream().map(CalculationOutput::fieldId).distinct().sorted().toList(),
                lookupVersionIds.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList(),
                correlationId, Instant.now());
    }

    private static void requireTenant(String tenantId) {
        requireNonBlank(tenantId, "tenant_id is required");
    }

    private static void requirePermission(CalculationRunHeaders headers, String permission) {
        if (headers == null || !headers.permissions().contains(permission)) {
            throw new CalculationRunnerException(permission + " permission is required");
        }
        requireNonBlank(headers.actorId(), "actor_id is required");
        requireNonBlank(headers.correlationId(), "correlation_id is required");
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CalculationRunnerException(message);
        }
        return value;
    }

    public record CalculationRunHeaders(Set<String> permissions, String actorId, String correlationId) {
        public CalculationRunHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record CalculationRunRequest(CalculationDefinition definition, Map<String, String> inputs) {
        public CalculationRunRequest {
            inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
        }
    }

    public record CalculationDefinition(String calculationId, String tenantId, String versionId,
            List<CalculationOutputStep> outputSteps) {
        public CalculationDefinition {
            outputSteps = outputSteps == null ? List.of() : List.copyOf(outputSteps);
        }
    }

    public record CalculationOutputStep(String outputFieldId, CalculationStepSource source, String inputFieldId,
            String lookupTableId, Map<String, String> lookupKeyInputBindings) {
        public CalculationOutputStep {
            lookupKeyInputBindings = lookupKeyInputBindings == null ? Map.of() : Map.copyOf(lookupKeyInputBindings);
        }

        public static CalculationOutputStep fromInput(String outputFieldId, String inputFieldId) {
            return new CalculationOutputStep(outputFieldId, CalculationStepSource.INPUT_VALUE, inputFieldId, null, Map.of());
        }

        public static CalculationOutputStep fromLookup(String outputFieldId, String lookupTableId,
                Map<String, String> lookupKeyInputBindings) {
            return new CalculationOutputStep(outputFieldId, CalculationStepSource.LOOKUP_VALUE, null, lookupTableId,
                    lookupKeyInputBindings);
        }
    }

    public enum CalculationStepSource {
        INPUT_VALUE,
        LOOKUP_VALUE
    }

    public enum CalculationRunStatus {
        COMPLETED,
        BLOCKED_MISSING_CONFIGURATION,
        BLOCKED_MISSING_INPUT,
        BLOCKED_MISSING_DATA
    }

    public record CalculationRunResult(String tenantId, String calculationId, String definitionVersionId,
            CalculationRunStatus status, List<CalculationOutput> outputs, String blockerCode,
            CalculationAuditTrace auditTrace) {
        public CalculationRunResult {
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
        }
    }

    public record CalculationOutput(String fieldId, String value, CalculationStepSource source,
            CalculationOutputTrace trace) {
    }

    public record CalculationOutputTrace(String lookupTableId, UUID lookupVersionId, Map<String, String> sourceValues) {
        public CalculationOutputTrace {
            sourceValues = sourceValues == null ? Map.of() : Map.copyOf(sourceValues);
        }
    }

    public record CalculationAuditTrace(String tenantId, String calculationId, String definitionVersionId,
            List<String> inputFieldIds, List<String> outputFieldIds, List<UUID> lookupVersionIds, String correlationId,
            Instant occurredAt) {
        public CalculationAuditTrace {
            inputFieldIds = inputFieldIds == null ? List.of() : List.copyOf(inputFieldIds);
            outputFieldIds = outputFieldIds == null ? List.of() : List.copyOf(outputFieldIds);
            lookupVersionIds = lookupVersionIds == null ? List.of() : List.copyOf(lookupVersionIds);
        }
    }

    private record LookupStepResolution(String value, UUID lookupVersionId, Map<String, String> keyValues,
            List<String> inputFieldIds, CalculationRunStatus status, String blockerCode) {
        private LookupStepResolution {
            keyValues = keyValues == null ? Map.of() : Map.copyOf(keyValues);
            inputFieldIds = inputFieldIds == null ? List.of() : List.copyOf(inputFieldIds);
        }

        static LookupStepResolution found(String value, UUID lookupVersionId, Map<String, String> keyValues,
                List<String> inputFieldIds) {
            return new LookupStepResolution(value, lookupVersionId, keyValues, inputFieldIds, null, null);
        }

        static LookupStepResolution blocked(CalculationRunStatus status, String blockerCode, List<String> inputFieldIds) {
            return new LookupStepResolution(null, null, Map.of(), inputFieldIds, status, blockerCode);
        }
    }

    public static final class CalculationRunnerException extends RuntimeException {
        public CalculationRunnerException(String message) {
            super(message);
        }
    }
}
