package com.wcpe.pricing.calculationrunner;

import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.CalculationFieldImport;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CalculationLookupReference;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CalculationLookupValidationRequest;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.HistoricalLookupValueRequest;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupHeaders;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupReferenceValidationStatus;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupRuntimeStatus;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupValueRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
        return runPricingCalculation(tenantId, headers, request, Map.of());
    }

    private CalculationRunResult runPricingCalculation(String tenantId, CalculationRunHeaders headers,
            CalculationRunRequest request, Map<String, UUID> historicalLookupVersions) {
        requireTenant(tenantId);
        requirePermission(headers, CALCULATION_RUN_PERMISSION);
        Objects.requireNonNull(request, "calculation run request is required");
        historicalLookupVersions = historicalLookupVersions == null ? Map.of() : Map.copyOf(historicalLookupVersions);

        if (request.definition() == null) {
            return blocked(tenantId, null, null, CalculationRunStatus.BLOCKED_MISSING_CONFIGURATION,
                    "CALCULATION_DEFINITION_MISSING", headers.correlationId(), List.of(), List.of(), List.of());
        }

        CalculationDefinition definition = request.definition();
        String calculationId = requireNonBlank(definition.calculationId(), "calculation_id is required");
        String versionId = requireNonBlank(definition.versionId(), "calculation definition version_id is required");
        String definitionTenantId = requireNonBlank(definition.tenantId(), "calculation definition tenant_id is required");
        if (!definition.allowedRoles().isEmpty() && headers.roles().stream().noneMatch(definition.allowedRoles()::contains)) {
            return blocked(tenantId, calculationId, versionId, CalculationRunStatus.ACCESS_DENIED,
                    "CALCULATION_ROLE_ACCESS_DENIED", headers.correlationId(), List.of(), List.of(), List.of(),
                    List.of(new CalculationEvaluationError("CALCULATION_ROLE_ACCESS_DENIED", calculationId,
                            "Caller role is not authorized for this calculation.")));
        }
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
                LookupStepResolution resolution = resolveLookupStep(tenantId, headers, step, inputs,
                        historicalLookupVersions);
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

            if (step.source() == CalculationStepSource.FORMULA_EXPRESSION) {
                ExpressionEvaluation evaluation = evaluateExpression(step.expression(), inputs);
                inputFieldIds.addAll(evaluation.fieldIds());
                if (!evaluation.errors().isEmpty()) {
                    return blocked(tenantId, calculationId, versionId, CalculationRunStatus.BLOCKED_EXPRESSION_ERROR,
                            evaluation.errors().get(0).code(), headers.correlationId(), outputs, inputFieldIds,
                            lookupVersionIds, evaluation.errors());
                }
                outputs.add(new CalculationOutput(outputFieldId, evaluation.value().asOutputString(),
                        CalculationStepSource.FORMULA_EXPRESSION, new CalculationOutputTrace(null, null,
                                evaluation.sourceValues())));
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

    public CalculationAuditRecord captureCalculationAuditRecord(String tenantId, CalculationRunHeaders headers,
            CalculationRunRequest request, CalculationAuditContext auditContext) {
        CalculationRunResult result = runPricingCalculation(tenantId, headers, request);
        auditContext = auditContext == null ? CalculationAuditContext.empty() : auditContext;
        Map<String, UUID> tableVersionIds = new LinkedHashMap<>(auditContext.tableVersionIds());
        tableVersionIds.putAll(lookupVersionsByTable(result));
        return new CalculationAuditRecord("calculation-audit:" + UUID.randomUUID(), result.tenantId(), headers.actorId(),
                auditContext.source(), headers.correlationId(), request.definition(), request.inputs(), outputsByField(result),
                auditContext.metadataVersionIds(), auditContext.fieldVersionIds(), tableVersionIds,
                result.outputs(), result.status(), result.errors(), result.auditTrace().occurredAt());
    }

    public CalculationReplayResult replayAuditRecord(String tenantId, CalculationRunHeaders headers,
            CalculationAuditRecord auditRecord) {
        requireTenant(tenantId);
        requirePermission(headers, CALCULATION_RUN_PERMISSION);
        Objects.requireNonNull(auditRecord, "calculation audit record is required");
        if (!tenantId.equals(auditRecord.tenantId())) {
            return new CalculationReplayResult(auditRecord.auditRecordId(), CalculationReplayStatus.ACCESS_DENIED,
                    Map.of(), List.of(), List.of(new CalculationEvaluationError("CALCULATION_AUDIT_TENANT_MISMATCH",
                    auditRecord.auditRecordId(), "Audit record tenant does not match replay tenant.")),
                    headers.correlationId());
        }

        CalculationRunResult replayed = runPricingCalculation(tenantId, headers,
                new CalculationRunRequest(auditRecord.definition(), auditRecord.inputValues()),
                auditRecord.tableVersionIds());
        List<CalculationReplayDivergence> divergences = replayDivergences(auditRecord, replayed);
        CalculationReplayStatus status = divergences.isEmpty()
                ? CalculationReplayStatus.MATCHED
                : replayed.status() == CalculationRunStatus.COMPLETED
                        ? CalculationReplayStatus.DIVERGED
                        : CalculationReplayStatus.BLOCKED;
        return new CalculationReplayResult(auditRecord.auditRecordId(), status, outputsByField(replayed), divergences,
                replayed.errors(), headers.correlationId());
    }

    public CalculationActivationResult activateCalculationDefinition(String tenantId, CalculationRunHeaders headers,
            CalculationActivationRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, CALCULATION_RUN_PERMISSION);
        Objects.requireNonNull(request, "calculation activation request is required");
        Objects.requireNonNull(request.definition(), "calculation definition is required");

        CalculationDefinition definition = request.definition();
        String calculationId = requireNonBlank(definition.calculationId(), "calculation_id is required");
        if (!tenantId.equals(requireNonBlank(definition.tenantId(), "calculation definition tenant_id is required"))) {
            return CalculationActivationResult.invalid(calculationId, headers.correlationId(),
                    new CalculationEvaluationError("CALCULATION_DEFINITION_TENANT_MISMATCH", calculationId,
                            "Calculation definition tenant does not match activation tenant."));
        }
        if (!definition.allowedRoles().isEmpty() && headers.roles().stream().noneMatch(definition.allowedRoles()::contains)) {
            return CalculationActivationResult.accessDenied(calculationId, headers.correlationId(),
                    new CalculationEvaluationError("CALCULATION_ROLE_ACCESS_DENIED", calculationId,
                            "Caller role is not authorized for this calculation."));
        }

        Set<String> configuredFields = configuredFieldIds(request.fieldImport());
        Set<String> availableFields = new HashSet<>(request.availableFieldIds());
        Set<String> availableEnums = new HashSet<>(request.availableEnumIds());
        List<CalculationLookupReference> lookupReferences = new ArrayList<>();
        List<CalculationEvaluationError> errors = new ArrayList<>();

        for (CalculationOutputStep step : definition.outputSteps()) {
            if (step.expression() == null) {
                continue;
            }
            for (String fieldId : step.expression().dependencies().fieldIds()) {
                String canonical = CalculationFieldCatalogApi.canonicalCalculationFieldId(fieldId);
                boolean present = canonical == null ? availableFields.contains(fieldId) : configuredFields.contains(canonical);
                if (!present) {
                    errors.add(new CalculationEvaluationError("FIELD_NOT_FOUND", fieldId,
                            "Expression dependency field is not configured."));
                }
            }
            for (String enumId : step.expression().dependencies().enumIds()) {
                if (!availableEnums.contains(enumId)) {
                    errors.add(new CalculationEvaluationError("ENUM_NOT_FOUND", enumId,
                            "Expression dependency enum is not configured."));
                }
            }
            lookupReferences.addAll(step.expression().dependencies().lookupReferences());
        }

        if (!lookupReferences.isEmpty()) {
            var validation = lookupApi.validateCalculationReferences(tenantId,
                    new LookupHeaders(Set.of(CalculationDataTableLookupApi.LOOKUP_READ_PERMISSION), headers.actorId(),
                            headers.correlationId()),
                    new CalculationLookupValidationRequest(calculationId, lookupReferences));
            if (validation.status() == LookupReferenceValidationStatus.INVALID) {
                validation.errors().forEach(error -> errors.add(new CalculationEvaluationError("LOOKUP_DEPENDENCY_ERROR",
                        calculationId, error)));
            }
        }

        if (!errors.isEmpty()) {
            return new CalculationActivationResult(calculationId, CalculationActivationStatus.INVALID, List.copyOf(errors),
                    headers.correlationId());
        }
        return new CalculationActivationResult(calculationId, CalculationActivationStatus.VALID, List.of(),
                headers.correlationId());
    }

    /**
     * Runtime-facing evaluation wrapper for Pipeline, Adjustments, Margins, and Pricing callers.
     *
     * <p>Callers provide tenant-scoped definitions and field values; this method runs only those configured definitions
     * and reports missing required data without defaulting rates, prices, fees, margins, LLPA, DSCR, or payment values.</p>
     */
    public RuntimeEvaluationResult evaluateRuntime(String tenantId, CalculationRunHeaders headers,
            RuntimeEvaluationRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, CALCULATION_RUN_PERMISSION);
        Objects.requireNonNull(request, "runtime evaluation request is required");

        List<RequiredDataIndicator> tenantContextErrors = tenantContextErrors(tenantId, request, headers);
        if (!tenantContextErrors.isEmpty()) {
            return new RuntimeEvaluationResult(tenantId, request.submittingSystem(), RuntimeEvaluationStatus.BLOCKED,
                    Map.of(), tenantContextErrors, List.of(), request.auditEnabled() ? RuntimeEvaluationAuditTrace.empty(
                    tenantId, request.submittingSystem(), headers.correlationId()) : null);
        }

        if (request.definitions().isEmpty()) {
            return new RuntimeEvaluationResult(tenantId, request.submittingSystem(), RuntimeEvaluationStatus.BLOCKED,
                    Map.of(), List.of(new RequiredDataIndicator("calculationDefinition",
                    "CALCULATION_DEFINITION_MISSING", "At least one configured calculation definition is required.")),
                    List.of(), request.auditEnabled() ? RuntimeEvaluationAuditTrace.empty(tenantId,
                    request.submittingSystem(), headers.correlationId()) : null);
        }

        Map<String, String> outputs = new LinkedHashMap<>();
        List<RequiredDataIndicator> requiredData = new ArrayList<>();
        List<CalculationRunResult> runResults = new ArrayList<>();

        for (CalculationDefinition definition : request.definitions()) {
            CalculationRunResult result = runPricingCalculation(tenantId, headers,
                    new CalculationRunRequest(definition, request.inputs()));
            runResults.add(result);
            result.outputs().forEach(output -> outputs.put(output.fieldId(), output.value()));
            requiredData.addAll(requiredDataIndicators(result));
        }

        request.requiredOutputFieldIds().stream()
                .filter(fieldId -> !outputs.containsKey(fieldId))
                .sorted()
                .map(fieldId -> new RequiredDataIndicator(fieldId, "REQUIRED_OUTPUT_NOT_EVALUATED",
                        "Configured required output was not evaluated."))
                .forEach(requiredData::add);

        RuntimeEvaluationStatus status = requiredData.isEmpty()
                ? RuntimeEvaluationStatus.COMPLETED
                : outputs.isEmpty() ? RuntimeEvaluationStatus.BLOCKED : RuntimeEvaluationStatus.PARTIAL;

        RuntimeEvaluationAuditTrace auditTrace = request.auditEnabled()
                ? runtimeAudit(tenantId, request.submittingSystem(), runResults, headers.correlationId())
                : null;

        return new RuntimeEvaluationResult(tenantId, request.submittingSystem(), status, outputs,
                requiredData.stream().distinct().toList(), List.copyOf(runResults), auditTrace);
    }

    private static List<RequiredDataIndicator> tenantContextErrors(String tenantId, RuntimeEvaluationRequest request,
            CalculationRunHeaders headers) {
        List<RequiredDataIndicator> errors = new ArrayList<>();
        CrossModuleTenantContext context = request.tenantContext();
        if (context == null) {
            errors.add(new RequiredDataIndicator("tenantContext", "TENANT_CONTEXT_REQUIRED",
                    "Runtime evaluation requires tenant context before outputs can be consumed across modules."));
            return errors;
        }
        if (!tenantId.equals(context.tenantId())) {
            errors.add(new RequiredDataIndicator("tenantContext.tenantId", "TENANT_CONTEXT_MISMATCH",
                    "Tenant context must match the runtime evaluation tenant."));
        }
        if (context.sourceModule() != request.submittingSystem()) {
            errors.add(new RequiredDataIndicator("tenantContext.sourceModule", "TENANT_CONTEXT_SOURCE_MISMATCH",
                    "Tenant context source module must match the submitting runtime module."));
        }
        if (!headers.correlationId().equals(context.correlationId())) {
            errors.add(new RequiredDataIndicator("tenantContext.correlationId", "TENANT_CONTEXT_CORRELATION_MISMATCH",
                    "Tenant context correlation id must match the runtime call correlation id."));
        }
        return errors;
    }

    private LookupStepResolution resolveLookupStep(String tenantId, CalculationRunHeaders headers,
            CalculationOutputStep step, Map<String, String> inputs, Map<String, UUID> historicalLookupVersions) {
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

        LookupHeaders lookupHeaders = new LookupHeaders(Set.of(CalculationDataTableLookupApi.LOOKUP_READ_PERMISSION),
                headers.actorId(), headers.correlationId());
        UUID historicalVersionId = historicalLookupVersions == null ? null : historicalLookupVersions.get(tableId);
        var lookup = historicalVersionId == null
                ? lookupApi.lookupValue(tenantId, lookupHeaders, new LookupValueRequest(tableId, keyValues))
                : lookupApi.lookupHistoricalValue(tenantId, lookupHeaders,
                        new HistoricalLookupValueRequest(historicalVersionId, tableId, keyValues, true));
        if (lookup.status() != LookupRuntimeStatus.FOUND) {
            return LookupStepResolution.blocked(CalculationRunStatus.BLOCKED_MISSING_DATA,
                    lookup.missingReason() == null ? "LOOKUP_VALUE_MISSING:" + tableId : lookup.missingReason(),
                    inputFieldIds);
        }
        return LookupStepResolution.found(lookup.value(), lookup.versionId(), keyValues, inputFieldIds);
    }

    private static List<RequiredDataIndicator> requiredDataIndicators(CalculationRunResult result) {
        if (result.status() == CalculationRunStatus.COMPLETED || result.blockerCode() == null) {
            return List.of();
        }
        String fieldId = null;
        int delimiter = result.blockerCode().indexOf(':');
        if (delimiter >= 0 && delimiter < result.blockerCode().length() - 1) {
            fieldId = result.blockerCode().substring(delimiter + 1);
        }
        if (fieldId == null || fieldId.isBlank()) {
            fieldId = result.calculationId() == null || result.calculationId().isBlank()
                    ? "calculationDefinition"
                    : result.calculationId();
        }
        return List.of(new RequiredDataIndicator(fieldId, result.blockerCode(),
                "Configured calculation data is required before evaluation can complete."));
    }

    private static Map<String, String> outputsByField(CalculationRunResult result) {
        Map<String, String> outputs = new LinkedHashMap<>();
        result.outputs().forEach(output -> outputs.put(output.fieldId(), output.value()));
        return Map.copyOf(outputs);
    }

    private static Map<String, UUID> lookupVersionsByTable(CalculationRunResult result) {
        Map<String, UUID> versions = new LinkedHashMap<>();
        result.outputs().stream()
                .map(CalculationOutput::trace)
                .filter(Objects::nonNull)
                .filter(trace -> trace.lookupTableId() != null && trace.lookupVersionId() != null)
                .forEach(trace -> versions.put(trace.lookupTableId(), trace.lookupVersionId()));
        return Map.copyOf(versions);
    }

    private static List<CalculationReplayDivergence> replayDivergences(CalculationAuditRecord auditRecord,
            CalculationRunResult replayed) {
        List<CalculationReplayDivergence> divergences = new ArrayList<>();
        if (auditRecord.recordedStatus() != replayed.status()) {
            divergences.add(new CalculationReplayDivergence("status", auditRecord.recordedStatus().name(),
                    replayed.status().name(), "CALCULATION_STATUS_CHANGED"));
        }
        Map<String, String> replayedOutputs = outputsByField(replayed);
        Set<String> fieldIds = new HashSet<>();
        fieldIds.addAll(auditRecord.outputValues().keySet());
        fieldIds.addAll(replayedOutputs.keySet());
        fieldIds.stream().sorted().forEach(fieldId -> {
            String recordedValue = auditRecord.outputValues().get(fieldId);
            String replayedValue = replayedOutputs.get(fieldId);
            if (!Objects.equals(recordedValue, replayedValue)) {
                String reasonCode = recordedValue == null
                        ? "OUTPUT_ADDED_DURING_REPLAY"
                        : replayedValue == null ? "OUTPUT_MISSING_DURING_REPLAY" : "OUTPUT_VALUE_CHANGED";
                divergences.add(new CalculationReplayDivergence(fieldId, recordedValue, replayedValue, reasonCode));
            }
        });
        return List.copyOf(divergences);
    }

    private static RuntimeEvaluationAuditTrace runtimeAudit(String tenantId, EvaluationSubmittingSystem submittingSystem,
            List<CalculationRunResult> runResults, String correlationId) {
        return new RuntimeEvaluationAuditTrace(tenantId, submittingSystem,
                runResults.stream().map(CalculationRunResult::calculationId).filter(Objects::nonNull).distinct().sorted()
                        .toList(),
                runResults.stream().map(CalculationRunResult::definitionVersionId).filter(Objects::nonNull).distinct()
                        .sorted().toList(),
                runResults.stream().flatMap(result -> result.auditTrace().inputFieldIds().stream()).distinct().sorted()
                        .toList(),
                runResults.stream().flatMap(result -> result.auditTrace().outputFieldIds().stream()).distinct().sorted()
                        .toList(),
                runResults.stream().flatMap(result -> result.auditTrace().lookupVersionIds().stream()).distinct()
                        .sorted(Comparator.comparing(UUID::toString)).toList(),
                correlationId, Instant.now());
    }

    private static CalculationRunResult blocked(String tenantId, String calculationId, String versionId,
            CalculationRunStatus status, String blockerCode, String correlationId, List<CalculationOutput> outputs,
            List<String> inputFieldIds, List<UUID> lookupVersionIds) {
        return blocked(tenantId, calculationId, versionId, status, blockerCode, correlationId, outputs, inputFieldIds,
                lookupVersionIds, List.of(new CalculationEvaluationError(blockerCode, calculationId,
                        "Calculation could not be completed; see blocker code.")));
    }

    private static CalculationRunResult blocked(String tenantId, String calculationId, String versionId,
            CalculationRunStatus status, String blockerCode, String correlationId, List<CalculationOutput> outputs,
            List<String> inputFieldIds, List<UUID> lookupVersionIds, List<CalculationEvaluationError> errors) {
        return new CalculationRunResult(tenantId, calculationId, versionId, status, List.copyOf(outputs), blockerCode,
                errors, audit(tenantId, calculationId, versionId, outputs, inputFieldIds, lookupVersionIds, correlationId));
    }

    private static Set<String> configuredFieldIds(CalculationFieldImport fieldImport) {
        if (fieldImport == null) {
            return Set.of();
        }
        Set<String> configured = new HashSet<>();
        for (CalculationFieldCatalogApi.ImportedCalculationField field : fieldImport.fields()) {
            String canonical = CalculationFieldCatalogApi.canonicalCalculationFieldId(field.id());
            if (canonical != null) {
                configured.add(canonical);
            }
        }
        return Set.copyOf(configured);
    }

    private static ExpressionEvaluation evaluateExpression(CalculationExpression expression, Map<String, String> inputs) {
        if (expression == null || expression.formula() == null || expression.formula().isBlank()) {
            return ExpressionEvaluation.failed("EXPRESSION_MISSING", "", "Calculation expression formula is required.");
        }
        return new ExpressionParser(expression.formula(), inputs, expression.resultType()).evaluate();
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

    public record CalculationRunHeaders(Set<String> permissions, String actorId, String correlationId, Set<String> roles) {
        public CalculationRunHeaders(Set<String> permissions, String actorId, String correlationId) {
            this(permissions, actorId, correlationId, Set.of());
        }

        public CalculationRunHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }

    public record CalculationRunRequest(CalculationDefinition definition, Map<String, String> inputs) {
        public CalculationRunRequest {
            inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
        }
    }

    public record RuntimeEvaluationRequest(EvaluationSubmittingSystem submittingSystem,
            List<CalculationDefinition> definitions, Map<String, String> inputs, Set<String> requiredOutputFieldIds,
            boolean auditEnabled, CrossModuleTenantContext tenantContext) {
        public RuntimeEvaluationRequest(EvaluationSubmittingSystem submittingSystem,
                List<CalculationDefinition> definitions, Map<String, String> inputs, Set<String> requiredOutputFieldIds,
                boolean auditEnabled) {
            this(submittingSystem, definitions, inputs, requiredOutputFieldIds, auditEnabled, null);
        }

        public RuntimeEvaluationRequest {
            submittingSystem = submittingSystem == null ? EvaluationSubmittingSystem.PRICING : submittingSystem;
            definitions = definitions == null ? List.of() : List.copyOf(definitions);
            inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
            requiredOutputFieldIds = requiredOutputFieldIds == null ? Set.of() : Set.copyOf(requiredOutputFieldIds);
        }
    }

    public record CrossModuleTenantContext(String tenantId, EvaluationSubmittingSystem sourceModule,
            String correlationId) {
        public CrossModuleTenantContext {
            tenantId = requireNonBlank(tenantId, "tenant context tenant_id is required");
            sourceModule = sourceModule == null ? EvaluationSubmittingSystem.PRICING : sourceModule;
            correlationId = requireNonBlank(correlationId, "tenant context correlation_id is required");
        }
    }

    public enum EvaluationSubmittingSystem {
        PIPELINE,
        ADJUSTMENTS,
        MARGINS,
        PRICING
    }

    public enum RuntimeEvaluationStatus {
        COMPLETED,
        PARTIAL,
        BLOCKED
    }

    public record RuntimeEvaluationResult(String tenantId, EvaluationSubmittingSystem submittingSystem,
            RuntimeEvaluationStatus status, Map<String, String> outputsByFieldId,
            List<RequiredDataIndicator> requiredDataIndicators, List<CalculationRunResult> calculationResults,
            RuntimeEvaluationAuditTrace auditTrace) {
        public RuntimeEvaluationResult {
            outputsByFieldId = outputsByFieldId == null ? Map.of() : Map.copyOf(outputsByFieldId);
            requiredDataIndicators = requiredDataIndicators == null ? List.of() : List.copyOf(requiredDataIndicators);
            calculationResults = calculationResults == null ? List.of() : List.copyOf(calculationResults);
        }
    }

    public record RequiredDataIndicator(String fieldId, String reasonCode, String message) {
        public RequiredDataIndicator {
            fieldId = requireNonBlank(fieldId, "required data field_id is required");
            reasonCode = requireNonBlank(reasonCode, "required data reason_code is required");
            message = message == null ? "" : message;
        }
    }

    public record RuntimeEvaluationAuditTrace(String tenantId, EvaluationSubmittingSystem submittingSystem,
            List<String> calculationIds, List<String> definitionVersionIds, List<String> inputFieldIds,
            List<String> outputFieldIds, List<UUID> lookupVersionIds, String correlationId, Instant occurredAt) {
        public RuntimeEvaluationAuditTrace {
            calculationIds = calculationIds == null ? List.of() : List.copyOf(calculationIds);
            definitionVersionIds = definitionVersionIds == null ? List.of() : List.copyOf(definitionVersionIds);
            inputFieldIds = inputFieldIds == null ? List.of() : List.copyOf(inputFieldIds);
            outputFieldIds = outputFieldIds == null ? List.of() : List.copyOf(outputFieldIds);
            lookupVersionIds = lookupVersionIds == null ? List.of() : List.copyOf(lookupVersionIds);
        }

        static RuntimeEvaluationAuditTrace empty(String tenantId, EvaluationSubmittingSystem submittingSystem,
                String correlationId) {
            return new RuntimeEvaluationAuditTrace(tenantId, submittingSystem, List.of(), List.of(), List.of(), List.of(),
                    List.of(), correlationId, Instant.now());
        }
    }

    public record CalculationAuditContext(String source, Map<String, String> metadataVersionIds,
            Map<String, String> fieldVersionIds, Map<String, UUID> tableVersionIds) {
        public CalculationAuditContext {
            source = source == null || source.isBlank() ? EvaluationSubmittingSystem.PRICING.name() : source;
            metadataVersionIds = metadataVersionIds == null ? Map.of() : Map.copyOf(metadataVersionIds);
            fieldVersionIds = fieldVersionIds == null ? Map.of() : Map.copyOf(fieldVersionIds);
            tableVersionIds = tableVersionIds == null ? Map.of() : Map.copyOf(tableVersionIds);
        }

        static CalculationAuditContext empty() {
            return new CalculationAuditContext(EvaluationSubmittingSystem.PRICING.name(), Map.of(), Map.of(), Map.of());
        }
    }

    public record CalculationAuditRecord(String auditRecordId, String tenantId, String actorId, String source,
            String correlationId, CalculationDefinition definition, Map<String, String> inputValues,
            Map<String, String> outputValues, Map<String, String> metadataVersionIds,
            Map<String, String> fieldVersionIds, Map<String, UUID> tableVersionIds,
            List<CalculationOutput> outputDetails, CalculationRunStatus recordedStatus,
            List<CalculationEvaluationError> errors, Instant occurredAt) {
        public CalculationAuditRecord {
            auditRecordId = requireNonBlank(auditRecordId, "audit record_id is required");
            tenantId = requireNonBlank(tenantId, "audit tenant_id is required");
            actorId = requireNonBlank(actorId, "audit actor_id is required");
            source = source == null || source.isBlank() ? EvaluationSubmittingSystem.PRICING.name() : source;
            correlationId = requireNonBlank(correlationId, "audit correlation_id is required");
            Objects.requireNonNull(definition, "audit calculation definition is required");
            inputValues = inputValues == null ? Map.of() : Map.copyOf(inputValues);
            outputValues = outputValues == null ? Map.of() : Map.copyOf(outputValues);
            metadataVersionIds = metadataVersionIds == null ? Map.of() : Map.copyOf(metadataVersionIds);
            fieldVersionIds = fieldVersionIds == null ? Map.of() : Map.copyOf(fieldVersionIds);
            tableVersionIds = tableVersionIds == null ? Map.of() : Map.copyOf(tableVersionIds);
            outputDetails = outputDetails == null ? List.of() : List.copyOf(outputDetails);
            errors = errors == null ? List.of() : List.copyOf(errors);
            occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        }
    }

    public enum CalculationReplayStatus {
        MATCHED,
        DIVERGED,
        BLOCKED,
        ACCESS_DENIED
    }

    public record CalculationReplayDivergence(String fieldId, String recordedValue, String replayedValue,
            String reasonCode) {
        public CalculationReplayDivergence {
            fieldId = requireNonBlank(fieldId, "replay divergence field_id is required");
            reasonCode = requireNonBlank(reasonCode, "replay divergence reason_code is required");
        }
    }

    public record CalculationReplayResult(String auditRecordId, CalculationReplayStatus status,
            Map<String, String> replayedOutputs, List<CalculationReplayDivergence> divergences,
            List<CalculationEvaluationError> errors, String correlationId) {
        public CalculationReplayResult {
            auditRecordId = requireNonBlank(auditRecordId, "replay audit record_id is required");
            replayedOutputs = replayedOutputs == null ? Map.of() : Map.copyOf(replayedOutputs);
            divergences = divergences == null ? List.of() : List.copyOf(divergences);
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    public record CalculationDefinition(String calculationId, String tenantId, String versionId,
            List<CalculationOutputStep> outputSteps, Set<String> allowedRoles) {
        public CalculationDefinition(String calculationId, String tenantId, String versionId,
                List<CalculationOutputStep> outputSteps) {
            this(calculationId, tenantId, versionId, outputSteps, Set.of());
        }

        public CalculationDefinition {
            outputSteps = outputSteps == null ? List.of() : List.copyOf(outputSteps);
            allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
        }
    }

    public record CalculationOutputStep(String outputFieldId, CalculationStepSource source, String inputFieldId,
            String lookupTableId, Map<String, String> lookupKeyInputBindings, CalculationExpression expression) {
        public CalculationOutputStep(String outputFieldId, CalculationStepSource source, String inputFieldId,
                String lookupTableId, Map<String, String> lookupKeyInputBindings) {
            this(outputFieldId, source, inputFieldId, lookupTableId, lookupKeyInputBindings, null);
        }

        public CalculationOutputStep {
            lookupKeyInputBindings = lookupKeyInputBindings == null ? Map.of() : Map.copyOf(lookupKeyInputBindings);
        }

        public static CalculationOutputStep fromInput(String outputFieldId, String inputFieldId) {
            return new CalculationOutputStep(outputFieldId, CalculationStepSource.INPUT_VALUE, inputFieldId, null, Map.of());
        }

        public static CalculationOutputStep fromLookup(String outputFieldId, String lookupTableId,
                Map<String, String> lookupKeyInputBindings) {
            return new CalculationOutputStep(outputFieldId, CalculationStepSource.LOOKUP_VALUE, null, lookupTableId,
                    lookupKeyInputBindings, null);
        }

        public static CalculationOutputStep fromExpression(String outputFieldId, CalculationExpression expression) {
            return new CalculationOutputStep(outputFieldId, CalculationStepSource.FORMULA_EXPRESSION, null, null, Map.of(),
                    expression);
        }
    }

    public enum CalculationStepSource {
        INPUT_VALUE,
        LOOKUP_VALUE,
        FORMULA_EXPRESSION
    }

    public enum CalculationRunStatus {
        COMPLETED,
        BLOCKED_MISSING_CONFIGURATION,
        BLOCKED_MISSING_INPUT,
        BLOCKED_MISSING_DATA,
        BLOCKED_EXPRESSION_ERROR,
        ACCESS_DENIED
    }

    public record CalculationRunResult(String tenantId, String calculationId, String definitionVersionId,
            CalculationRunStatus status, List<CalculationOutput> outputs, String blockerCode,
            List<CalculationEvaluationError> errors,
            CalculationAuditTrace auditTrace) {
        public CalculationRunResult(String tenantId, String calculationId, String definitionVersionId,
                CalculationRunStatus status, List<CalculationOutput> outputs, String blockerCode,
                CalculationAuditTrace auditTrace) {
            this(tenantId, calculationId, definitionVersionId, status, outputs, blockerCode, List.of(), auditTrace);
        }

        public CalculationRunResult {
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    public record CalculationEvaluationError(String code, String subject, String message) {
        public CalculationEvaluationError {
            code = requireNonBlank(code, "calculation error code is required");
            subject = subject == null ? "" : subject;
            message = message == null ? "" : message;
        }
    }

    public record CalculationActivationRequest(CalculationDefinition definition, CalculationFieldImport fieldImport,
            Set<String> availableFieldIds, Set<String> availableEnumIds) {
        public CalculationActivationRequest {
            availableFieldIds = availableFieldIds == null ? Set.of() : Set.copyOf(availableFieldIds);
            availableEnumIds = availableEnumIds == null ? Set.of() : Set.copyOf(availableEnumIds);
        }
    }

    public enum CalculationActivationStatus {
        VALID,
        INVALID,
        ACCESS_DENIED
    }

    public record CalculationActivationResult(String calculationId, CalculationActivationStatus status,
            List<CalculationEvaluationError> errors, String correlationId) {
        public CalculationActivationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        static CalculationActivationResult invalid(String calculationId, String correlationId,
                CalculationEvaluationError error) {
            return new CalculationActivationResult(calculationId, CalculationActivationStatus.INVALID, List.of(error),
                    correlationId);
        }

        static CalculationActivationResult accessDenied(String calculationId, String correlationId,
                CalculationEvaluationError error) {
            return new CalculationActivationResult(calculationId, CalculationActivationStatus.ACCESS_DENIED, List.of(error),
                    correlationId);
        }
    }

    public record CalculationExpression(String formula, ExpressionValueType resultType,
            CalculationExpressionDependencies dependencies) {
        public CalculationExpression {
            resultType = resultType == null ? ExpressionValueType.STRING : resultType;
            dependencies = dependencies == null ? CalculationExpressionDependencies.none() : dependencies;
        }
    }

    public enum ExpressionValueType {
        STRING,
        NUMBER,
        BOOLEAN
    }

    public record CalculationExpressionDependencies(Set<String> fieldIds, Set<String> enumIds,
            List<CalculationLookupReference> lookupReferences) {
        public CalculationExpressionDependencies {
            fieldIds = fieldIds == null ? Set.of() : Set.copyOf(fieldIds);
            enumIds = enumIds == null ? Set.of() : Set.copyOf(enumIds);
            lookupReferences = lookupReferences == null ? List.of() : List.copyOf(lookupReferences);
        }

        public static CalculationExpressionDependencies none() {
            return new CalculationExpressionDependencies(Set.of(), Set.of(), List.of());
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

    private record ExpressionEvaluation(TypedExpressionValue value, Map<String, String> sourceValues, List<String> fieldIds,
            List<CalculationEvaluationError> errors) {
        private ExpressionEvaluation {
            sourceValues = sourceValues == null ? Map.of() : Map.copyOf(sourceValues);
            fieldIds = fieldIds == null ? List.of() : List.copyOf(fieldIds);
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        static ExpressionEvaluation failed(String code, String subject, String message) {
            return new ExpressionEvaluation(null, Map.of(), List.of(),
                    List.of(new CalculationEvaluationError(code, subject, message)));
        }
    }

    private record TypedExpressionValue(ExpressionValueType type, Object value) {
        String asOutputString() {
            if (value == null) {
                return "";
            }
            return switch (type) {
                case BOOLEAN -> Boolean.toString((Boolean) value);
                case NUMBER -> ((BigDecimal) value).stripTrailingZeros().toPlainString();
                case STRING -> value.toString();
            };
        }

        boolean asBoolean() {
            if (type != ExpressionValueType.BOOLEAN) {
                throw new CalculationRunnerException("EXPRESSION_BOOLEAN_REQUIRED");
            }
            return (Boolean) value;
        }
    }

    private static final class ExpressionParser {
        private final String expression;
        private final Map<String, String> inputs;
        private final ExpressionValueType expectedType;
        private final Map<String, String> sourceValues = new LinkedHashMap<>();
        private final List<String> fieldIds = new ArrayList<>();

        private ExpressionParser(String expression, Map<String, String> inputs, ExpressionValueType expectedType) {
            this.expression = expression.trim();
            this.inputs = inputs == null ? Map.of() : inputs;
            this.expectedType = expectedType == null ? ExpressionValueType.STRING : expectedType;
        }

        ExpressionEvaluation evaluate() {
            try {
                TypedExpressionValue value = evaluateToken(expression);
                TypedExpressionValue typed = coerce(value, expectedType);
                return new ExpressionEvaluation(typed, sourceValues, fieldIds.stream().distinct().sorted().toList(), List.of());
            } catch (CalculationRunnerException ex) {
                return new ExpressionEvaluation(null, sourceValues, fieldIds.stream().distinct().sorted().toList(),
                        List.of(new CalculationEvaluationError(safeErrorCode(ex.getMessage()), "expression",
                                "Expression evaluation failed without tenant data exposure.")));
            }
        }

        private TypedExpressionValue evaluateToken(String token) {
            token = token.trim();
            if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
                return new TypedExpressionValue(ExpressionValueType.STRING, token.substring(1, token.length() - 1));
            }
            if ("true".equalsIgnoreCase(token) || "false".equalsIgnoreCase(token)) {
                return new TypedExpressionValue(ExpressionValueType.BOOLEAN, Boolean.parseBoolean(token));
            }
            if (token.matches("-?\\d+(\\.\\d+)?")) {
                return new TypedExpressionValue(ExpressionValueType.NUMBER, new BigDecimal(token));
            }
            String functionName = functionName(token);
            List<String> args = functionArguments(token, functionName);
            return switch (functionName) {
                case "FIELD" -> fieldValue(args);
                case "ISBLANK" -> isBlank(args);
                case "AND" -> and(args);
                case "OR" -> or(args);
                case "IF" -> ifValue(args);
                default -> throw new CalculationRunnerException("EXPRESSION_UNSUPPORTED_FUNCTION");
            };
        }

        private TypedExpressionValue fieldValue(List<String> args) {
            requireArgumentCount("FIELD", args, 1);
            String fieldId = stringLiteral(args.get(0));
            fieldIds.add(fieldId);
            String value = inputs.get(fieldId);
            if (value == null) {
                throw new CalculationRunnerException("FIELD_VALUE_MISSING");
            }
            sourceValues.put(fieldId, value);
            return new TypedExpressionValue(ExpressionValueType.STRING, value);
        }

        private TypedExpressionValue isBlank(List<String> args) {
            requireArgumentCount("ISBLANK", args, 1);
            TypedExpressionValue value = evaluateToken(args.get(0));
            return new TypedExpressionValue(ExpressionValueType.BOOLEAN,
                    value.value() == null || value.asOutputString().isBlank());
        }

        private TypedExpressionValue and(List<String> args) {
            if (args.isEmpty()) {
                throw new CalculationRunnerException("EXPRESSION_ARGUMENT_MISSING");
            }
            boolean result = true;
            for (String arg : args) {
                result = result && evaluateToken(arg).asBoolean();
            }
            return new TypedExpressionValue(ExpressionValueType.BOOLEAN, result);
        }

        private TypedExpressionValue or(List<String> args) {
            if (args.isEmpty()) {
                throw new CalculationRunnerException("EXPRESSION_ARGUMENT_MISSING");
            }
            boolean result = false;
            for (String arg : args) {
                result = result || evaluateToken(arg).asBoolean();
            }
            return new TypedExpressionValue(ExpressionValueType.BOOLEAN, result);
        }

        private TypedExpressionValue ifValue(List<String> args) {
            requireArgumentCount("IF", args, 3);
            return evaluateToken(args.get(0)).asBoolean() ? evaluateToken(args.get(1)) : evaluateToken(args.get(2));
        }

        private static TypedExpressionValue coerce(TypedExpressionValue value, ExpressionValueType expectedType) {
            if (value.type() == expectedType) {
                return value;
            }
            if (expectedType == ExpressionValueType.STRING) {
                return new TypedExpressionValue(ExpressionValueType.STRING, value.asOutputString());
            }
            if (expectedType == ExpressionValueType.NUMBER && value.type() == ExpressionValueType.STRING) {
                try {
                    return new TypedExpressionValue(ExpressionValueType.NUMBER, new BigDecimal(value.asOutputString()));
                } catch (NumberFormatException ex) {
                    throw new CalculationRunnerException("EXPRESSION_TYPE_MISMATCH");
                }
            }
            throw new CalculationRunnerException("EXPRESSION_TYPE_MISMATCH");
        }

        private static String functionName(String token) {
            int paren = token.indexOf('(');
            if (paren <= 0 || !token.endsWith(")")) {
                throw new CalculationRunnerException("EXPRESSION_PARSE_ERROR");
            }
            return token.substring(0, paren).trim().toUpperCase();
        }

        private static List<String> functionArguments(String token, String functionName) {
            int start = functionName.length() + 1;
            String body = token.substring(start, token.length() - 1);
            List<String> args = new ArrayList<>();
            int depth = 0;
            boolean quoted = false;
            int segmentStart = 0;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '"') {
                    quoted = !quoted;
                } else if (!quoted && c == '(') {
                    depth++;
                } else if (!quoted && c == ')') {
                    depth--;
                } else if (!quoted && depth == 0 && c == ',') {
                    args.add(body.substring(segmentStart, i).trim());
                    segmentStart = i + 1;
                }
            }
            if (!body.isBlank()) {
                args.add(body.substring(segmentStart).trim());
            }
            return args;
        }

        private static void requireArgumentCount(String functionName, List<String> args, int expected) {
            if (args.size() != expected) {
                throw new CalculationRunnerException(functionName + "_ARGUMENT_COUNT_INVALID");
            }
        }

        private static String stringLiteral(String token) {
            String trimmed = token.trim();
            if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"") || trimmed.length() < 2) {
                throw new CalculationRunnerException("EXPRESSION_STRING_LITERAL_REQUIRED");
            }
            return trimmed.substring(1, trimmed.length() - 1);
        }

        private static String safeErrorCode(String message) {
            if (message == null || message.isBlank()) {
                return "EXPRESSION_ERROR";
            }
            int space = message.indexOf(' ');
            return space < 0 ? message : message.substring(0, space);
        }
    }
}
