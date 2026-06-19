package com.wcpe.pricing.calculationrunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.CalculationFieldImport;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.ImportedCalculationField;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationActivationRequest;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationActivationStatus;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationAuditContext;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationDefinition;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationExpression;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationExpressionDependencies;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationOutputStep;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationRunHeaders;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationRunRequest;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationRunStatus;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationReplayStatus;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CrossModuleTenantContext;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.EvaluationSubmittingSystem;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.ExpressionValueType;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.RuntimeEvaluationRequest;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.RuntimeEvaluationStatus;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CalculationLookupReference;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CreateLookupRequest;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.EditLookupOptionsRequest;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.InMemoryCalculationDataTableLookupRepository;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupHeaders;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupOptionDraft;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingCalculationRunnerApiTest {
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String TABLE_ID = "county-adjustment-table";
    private static final String CALCULATION_ID = "calc@pricing-runner-fixture";
    private static final String VERSION_ID = "calc-version-2026-06-18";

    private InMemoryCalculationDataTableLookupRepository lookupRepository;
    private CalculationDataTableLookupApi lookupApi;
    private PricingCalculationRunnerApi runnerApi;

    @BeforeEach
    void setUp() {
        lookupRepository = new InMemoryCalculationDataTableLookupRepository();
        lookupApi = new CalculationDataTableLookupApi(lookupRepository);
        runnerApi = new PricingCalculationRunnerApi(lookupApi);
    }

    @Test
    void configuredDefinitionsProduceCalculationFieldOutputsAndAuditMetadata() {
        publishLookup(TENANT_A, "approved-factor-a");

        var result = runnerApi.runPricingCalculation(TENANT_A, runHeaders(), new CalculationRunRequest(
                fixtureDefinition(TENANT_A), Map.of("state", "IL", "county", "COOK", "source-payment", "2150.00")));

        assertEquals(CalculationRunStatus.COMPLETED, result.status());
        assertNull(result.blockerCode());
        assertEquals(List.of("calc@county-factor", "calc@monthly-payment"), result.outputs().stream()
                .map(PricingCalculationRunnerApi.CalculationOutput::fieldId)
                .toList());
        assertEquals("approved-factor-a", result.outputs().get(0).value());
        assertEquals("2150.00", result.outputs().get(1).value());
        assertEquals(TENANT_A, result.auditTrace().tenantId());
        assertEquals(CALCULATION_ID, result.auditTrace().calculationId());
        assertEquals(VERSION_ID, result.auditTrace().definitionVersionId());
        assertEquals(List.of("county", "source-payment", "state"), result.auditTrace().inputFieldIds());
        assertEquals(List.of("calc@county-factor", "calc@monthly-payment"), result.auditTrace().outputFieldIds());
        assertFalse(result.auditTrace().lookupVersionIds().isEmpty());
    }

    @Test
    void missingFormulaOrDataBlocksWithoutInventingValues() {
        var missingDefinition = runnerApi.runPricingCalculation(TENANT_A, runHeaders(),
                new CalculationRunRequest(new CalculationDefinition(CALCULATION_ID, TENANT_A, VERSION_ID, List.of()),
                        Map.of()));

        assertEquals(CalculationRunStatus.BLOCKED_MISSING_CONFIGURATION, missingDefinition.status());
        assertEquals("CALCULATION_OUTPUT_STEPS_MISSING", missingDefinition.blockerCode());
        assertEquals(List.of(), missingDefinition.outputs());

        publishLookup(TENANT_A, "approved-factor-a");
        var missingLookupData = runnerApi.runPricingCalculation(TENANT_A, runHeaders(), new CalculationRunRequest(
                fixtureDefinition(TENANT_A), Map.of("state", "IL", "county", "DUPAGE", "source-payment", "2150.00")));

        assertEquals(CalculationRunStatus.BLOCKED_MISSING_DATA, missingLookupData.status());
        assertEquals("LOOKUP_VALUE_MISSING", missingLookupData.blockerCode());
        assertEquals(List.of(), missingLookupData.outputs());
    }

    @Test
    void tenantSpecificRunsUseOnlyTenantDefinitionsAndLookupTables() {
        publishLookup(TENANT_A, "tenant-a-factor");
        publishLookup(TENANT_B, "tenant-b-factor");

        var tenantBResult = runnerApi.runPricingCalculation(TENANT_B, runHeaders(), new CalculationRunRequest(
                fixtureDefinition(TENANT_B), Map.of("state", "IL", "county", "COOK", "source-payment", "2150.00")));

        assertEquals(CalculationRunStatus.COMPLETED, tenantBResult.status());
        assertEquals("tenant-b-factor", tenantBResult.outputs().get(0).value());
        assertEquals(TENANT_B, tenantBResult.auditTrace().tenantId());

        var tenantMismatch = runnerApi.runPricingCalculation(TENANT_B, runHeaders(), new CalculationRunRequest(
                fixtureDefinition(TENANT_A), Map.of("state", "IL", "county", "COOK", "source-payment", "2150.00")));

        assertEquals(CalculationRunStatus.BLOCKED_MISSING_CONFIGURATION, tenantMismatch.status());
        assertEquals("CALCULATION_DEFINITION_TENANT_MISMATCH", tenantMismatch.blockerCode());
    }

    @Test
    void missingRequiredInputsReturnExplicitBlocker() {
        publishLookup(TENANT_A, "approved-factor-a");

        var result = runnerApi.runPricingCalculation(TENANT_A, runHeaders(), new CalculationRunRequest(
                fixtureDefinition(TENANT_A), Map.of("state", "IL", "source-payment", "2150.00")));

        assertEquals(CalculationRunStatus.BLOCKED_MISSING_INPUT, result.status());
        assertEquals("LOOKUP_INPUT_VALUE_MISSING:county", result.blockerCode());
        assertEquals(List.of(), result.outputs());
    }

    @Test
    void expressionFunctionsProduceDeterministicTypedOutputs() {
        var definition = new CalculationDefinition(CALCULATION_ID, TENANT_A, VERSION_ID, List.of(
                CalculationOutputStep.fromExpression("calc@occupancy-note", new CalculationExpression(
                        "IF(AND(ISBLANK(FIELD(\"occupancy\")), OR(true, false)), \"missing\", FIELD(\"occupancy\"))",
                        ExpressionValueType.STRING,
                        new CalculationExpressionDependencies(Set.of("occupancy"), Set.of(), List.of()))),
                CalculationOutputStep.fromExpression("calc@payment-copy", new CalculationExpression(
                        "FIELD(\"source-payment\")", ExpressionValueType.NUMBER,
                        new CalculationExpressionDependencies(Set.of("source-payment"), Set.of(), List.of())))));

        var result = runnerApi.runPricingCalculation(TENANT_A, runHeaders(),
                new CalculationRunRequest(definition, Map.of("occupancy", "", "source-payment", "2150.00")));

        assertEquals(CalculationRunStatus.COMPLETED, result.status());
        assertEquals(List.of("missing", "2150"), result.outputs().stream()
                .map(PricingCalculationRunnerApi.CalculationOutput::value)
                .toList());
        assertEquals(List.of("occupancy", "source-payment"), result.auditTrace().inputFieldIds());
    }

    @Test
    void activationDetectsMissingExpressionFieldsEnumsAndTables() {
        var definition = new CalculationDefinition(CALCULATION_ID, TENANT_A, VERSION_ID, List.of(
                CalculationOutputStep.fromExpression("calc@activation-output", new CalculationExpression(
                        "IF(ISBLANK(FIELD(\"state\")), \"missing\", FIELD(\"state\"))",
                        ExpressionValueType.STRING,
                        new CalculationExpressionDependencies(Set.of("state", "calc@activation-output"),
                                Set.of("occupancy-type"),
                                List.of(new CalculationLookupReference(TABLE_ID, Set.of("state", "county"))))))));
        publishLookup(TENANT_A, "approved-factor-a");

        var valid = runnerApi.activateCalculationDefinition(TENANT_A, runHeaders(), new CalculationActivationRequest(
                definition, calculationFieldImport(), Set.of("state"), Set.of("occupancy-type")));

        assertEquals(CalculationActivationStatus.VALID, valid.status());

        var invalidDefinition = new CalculationDefinition(CALCULATION_ID, TENANT_A, VERSION_ID, List.of(
                CalculationOutputStep.fromExpression("calc@activation-output", new CalculationExpression(
                        "IF(ISBLANK(FIELD(\"state\")), \"missing\", FIELD(\"state\"))",
                        ExpressionValueType.STRING,
                        new CalculationExpressionDependencies(Set.of("state", "calc@activation-output"),
                                Set.of("occupancy-type"),
                                List.of(new CalculationLookupReference("missing-table", Set.of("state"))))))));
        var invalid = runnerApi.activateCalculationDefinition(TENANT_A, runHeaders(), new CalculationActivationRequest(
                invalidDefinition, new CalculationFieldImport("ReferenceFormfields.json", List.of()), Set.of(), Set.of()));

        assertEquals(CalculationActivationStatus.INVALID, invalid.status());
        assertTrue(invalid.errors().stream().anyMatch(error -> error.code().equals("FIELD_NOT_FOUND")));
        assertTrue(invalid.errors().stream().anyMatch(error -> error.code().equals("ENUM_NOT_FOUND")));
        assertTrue(invalid.errors().stream().anyMatch(error -> error.code().equals("LOOKUP_DEPENDENCY_ERROR")));
    }

    @Test
    void expressionErrorsAreStructuredAndTenantSafe() {
        var definition = new CalculationDefinition(CALCULATION_ID, TENANT_A, VERSION_ID, List.of(
                CalculationOutputStep.fromExpression("calc@missing-value", new CalculationExpression(
                        "FIELD(\"missing-input\")", ExpressionValueType.STRING,
                        new CalculationExpressionDependencies(Set.of("missing-input"), Set.of(), List.of())))));

        var result = runnerApi.runPricingCalculation(TENANT_A, runHeaders(),
                new CalculationRunRequest(definition, Map.of()));

        assertEquals(CalculationRunStatus.BLOCKED_EXPRESSION_ERROR, result.status());
        assertEquals("FIELD_VALUE_MISSING", result.blockerCode());
        assertEquals("FIELD_VALUE_MISSING", result.errors().get(0).code());
        assertEquals("Expression evaluation failed without tenant data exposure.", result.errors().get(0).message());
    }

    @Test
    void configuredCalculationRolesAreEnforcedByRunner() {
        var definition = new CalculationDefinition(CALCULATION_ID, TENANT_A, VERSION_ID,
                List.of(CalculationOutputStep.fromInput("calc@monthly-payment", "source-payment")),
                Set.of("pricing-manager"));

        var result = runnerApi.runPricingCalculation(TENANT_A,
                new CalculationRunHeaders(Set.of(PricingCalculationRunnerApi.CALCULATION_RUN_PERMISSION),
                        "calculation-runner", "corr-runner", Set.of("loan-officer")),
                new CalculationRunRequest(definition, Map.of("source-payment", "2150.00")));

        assertEquals(CalculationRunStatus.ACCESS_DENIED, result.status());
        assertEquals("CALCULATION_ROLE_ACCESS_DENIED", result.errors().get(0).code());
    }

    @Test
    void runtimeEvaluationApiReturnsConfiguredPricingOutputsRequiredDataAndAuditTrace() {
        var pipelineDefinition = new CalculationDefinition("calc@pipeline-runtime", TENANT_A,
                "runtime-version-1", List.of(
                CalculationOutputStep.fromInput("calc@adjusted-rate", "configured-adjusted-rate"),
                CalculationOutputStep.fromInput("calc@price", "configured-price"),
                CalculationOutputStep.fromInput("calc@monthly-payment", "configured-payment"),
                CalculationOutputStep.fromInput("calc@dscr", "configured-dscr")));
        var marginDefinition = new CalculationDefinition("calc@margin-runtime", TENANT_A,
                "runtime-version-2", List.of(
                CalculationOutputStep.fromInput("calc@fee", "configured-fee"),
                CalculationOutputStep.fromInput("calc@llpa", "configured-llpa"),
                CalculationOutputStep.fromInput("calc@margin", "configured-margin")));

        var result = runnerApi.evaluateRuntime(TENANT_A, runHeaders(), new RuntimeEvaluationRequest(
                EvaluationSubmittingSystem.PIPELINE, List.of(pipelineDefinition, marginDefinition), Map.of(
                "configured-adjusted-rate", "7.125",
                "configured-price", "99.875",
                "configured-payment", "2150.00",
                "configured-dscr", "1.20",
                "configured-fee", "950.00",
                "configured-llpa", "0.375",
                "configured-margin", "1.500"), Set.of("calc@required-data-indicator"), true,
                tenantContext(TENANT_A, EvaluationSubmittingSystem.PIPELINE)));

        assertEquals(RuntimeEvaluationStatus.PARTIAL, result.status());
        assertEquals("7.125", result.outputsByFieldId().get("calc@adjusted-rate"));
        assertEquals("99.875", result.outputsByFieldId().get("calc@price"));
        assertEquals("2150.00", result.outputsByFieldId().get("calc@monthly-payment"));
        assertEquals("1.20", result.outputsByFieldId().get("calc@dscr"));
        assertEquals("950.00", result.outputsByFieldId().get("calc@fee"));
        assertEquals("0.375", result.outputsByFieldId().get("calc@llpa"));
        assertEquals("1.500", result.outputsByFieldId().get("calc@margin"));
        assertTrue(result.requiredDataIndicators().stream()
                .anyMatch(indicator -> indicator.fieldId().equals("calc@required-data-indicator")
                        && indicator.reasonCode().equals("REQUIRED_OUTPUT_NOT_EVALUATED")));
        assertEquals(TENANT_A, result.auditTrace().tenantId());
        assertEquals(EvaluationSubmittingSystem.PIPELINE, result.auditTrace().submittingSystem());
        assertEquals(List.of("calc@margin-runtime", "calc@pipeline-runtime"), result.auditTrace().calculationIds());
        assertEquals(List.of("runtime-version-1", "runtime-version-2"), result.auditTrace().definitionVersionIds());
        assertTrue(result.auditTrace().outputFieldIds().contains("calc@adjusted-rate"));
        assertEquals("corr-runner", result.auditTrace().correlationId());
    }

    @Test
    void auditRecordCapturesReplayVersionsAndHistoricalReplayUsesOriginalLookupVersion() {
        publishLookup(TENANT_A, "approved-factor-a");

        var auditRecord = runnerApi.captureCalculationAuditRecord(TENANT_A, runHeaders(), new CalculationRunRequest(
                fixtureDefinition(TENANT_A), Map.of("state", "IL", "county", "COOK", "source-payment", "2150.00")),
                new CalculationAuditContext(EvaluationSubmittingSystem.PRICING.name(),
                        Map.of("calculation-metadata", "metadata-version-1"),
                        Map.of("source-payment", "field-version-1"), Map.of()));

        var edited = lookupApi.editLookupOptions(TENANT_A, writeHeaders(), TABLE_ID,
                new EditLookupOptionsRequest(List.of(new LookupOptionDraft(Map.of("state", "IL", "county", "COOK"),
                        "approved-factor-b", "Approved fixture option v2")), "fixture update"));
        lookupApi.publishLookupOptions(TENANT_A, publishHeaders(), edited.versionId());

        var replay = runnerApi.replayAuditRecord(TENANT_A, runHeaders(), auditRecord);

        assertEquals("calculation-runner", auditRecord.actorId());
        assertEquals(EvaluationSubmittingSystem.PRICING.name(), auditRecord.source());
        assertEquals("corr-runner", auditRecord.correlationId());
        assertEquals("IL", auditRecord.inputValues().get("state"));
        assertEquals("approved-factor-a", auditRecord.outputValues().get("calc@county-factor"));
        assertEquals("metadata-version-1", auditRecord.metadataVersionIds().get("calculation-metadata"));
        assertEquals("field-version-1", auditRecord.fieldVersionIds().get("source-payment"));
        assertFalse(auditRecord.tableVersionIds().isEmpty());
        assertEquals(CalculationReplayStatus.MATCHED, replay.status());
        assertEquals("approved-factor-a", replay.replayedOutputs().get("calc@county-factor"));
        assertEquals(List.of(), replay.divergences());
    }

    @Test
    void replayDivergenceIsStructuredAndTenantSafe() {
        publishLookup(TENANT_A, "approved-factor-a");
        var auditRecord = runnerApi.captureCalculationAuditRecord(TENANT_A, runHeaders(), new CalculationRunRequest(
                fixtureDefinition(TENANT_A), Map.of("state", "IL", "county", "COOK", "source-payment", "2150.00")),
                new CalculationAuditContext(EvaluationSubmittingSystem.PRICING.name(), Map.of(), Map.of(), Map.of()));
        var changedDefinition = new CalculationDefinition(CALCULATION_ID, TENANT_A, "changed-definition-version", List.of(
                CalculationOutputStep.fromInput("calc@monthly-payment", "source-payment")));
        var changedRecord = new PricingCalculationRunnerApi.CalculationAuditRecord(auditRecord.auditRecordId(),
                auditRecord.tenantId(), auditRecord.actorId(), auditRecord.source(), auditRecord.correlationId(),
                changedDefinition, auditRecord.inputValues(), auditRecord.outputValues(), auditRecord.metadataVersionIds(),
                auditRecord.fieldVersionIds(), auditRecord.tableVersionIds(), auditRecord.outputDetails(),
                auditRecord.recordedStatus(), auditRecord.errors(), auditRecord.occurredAt());

        var replay = runnerApi.replayAuditRecord(TENANT_A, runHeaders(), changedRecord);
        var denied = runnerApi.replayAuditRecord(TENANT_B, runHeaders(), auditRecord);

        assertEquals(CalculationReplayStatus.DIVERGED, replay.status());
        assertTrue(replay.divergences().stream().anyMatch(divergence -> divergence.fieldId().equals("calc@county-factor")
                && divergence.reasonCode().equals("OUTPUT_MISSING_DURING_REPLAY")));
        assertEquals(CalculationReplayStatus.ACCESS_DENIED, denied.status());
        assertEquals("CALCULATION_AUDIT_TENANT_MISMATCH", denied.errors().get(0).code());
        assertEquals(Map.of(), denied.replayedOutputs());
    }

    @Test
    void runtimeEvaluationRequiresTenantContext() {
        var request = new RuntimeEvaluationRequest(EvaluationSubmittingSystem.ADJUSTMENTS, List.of(fixtureDefinition(TENANT_A)),
                Map.of("state", "IL", "county", "COOK", "source-payment", "2150.00"), Set.of(), true,
                tenantContext(TENANT_A, EvaluationSubmittingSystem.ADJUSTMENTS));

        var exception = assertThrows(PricingCalculationRunnerApi.CalculationRunnerException.class,
                () -> runnerApi.evaluateRuntime(" ", runHeaders(), request));

        assertEquals("tenant_id is required", exception.getMessage());
    }

    @Test
    void runtimeEvaluationBlocksCrossModuleCallWhenTenantContextDoesNotMatchPathTenant() {
        var request = new RuntimeEvaluationRequest(EvaluationSubmittingSystem.ADJUSTMENTS, List.of(fixtureDefinition(TENANT_A)),
                Map.of("state", "IL", "county", "COOK", "source-payment", "2150.00"), Set.of(), true,
                tenantContext(TENANT_B, EvaluationSubmittingSystem.ADJUSTMENTS));

        var result = runnerApi.evaluateRuntime(TENANT_A, runHeaders(), request);

        assertEquals(RuntimeEvaluationStatus.BLOCKED, result.status());
        assertTrue(result.requiredDataIndicators().stream()
                .anyMatch(indicator -> indicator.reasonCode().equals("TENANT_CONTEXT_MISMATCH")));
        assertEquals(Map.of(), result.outputsByFieldId());
    }

    private CalculationDefinition fixtureDefinition(String tenantId) {
        return new CalculationDefinition(CALCULATION_ID, tenantId, VERSION_ID, List.of(
                CalculationOutputStep.fromLookup("calc@county-factor", TABLE_ID,
                        Map.of("state", "state", "county", "county")),
                CalculationOutputStep.fromInput("calc@monthly-payment", "source-payment")));
    }

    private CrossModuleTenantContext tenantContext(String tenantId, EvaluationSubmittingSystem sourceModule) {
        return new CrossModuleTenantContext(tenantId, sourceModule, "corr-runner");
    }

    private CalculationFieldImport calculationFieldImport() {
        return new CalculationFieldImport("ReferenceFormfields.json",
                List.of(new ImportedCalculationField("calc@activation-output", "Activation output", "STRING",
                        "Expression output fixture")));
    }

    private void publishLookup(String tenantId, String value) {
        var created = lookupApi.createLookup(tenantId, writeHeaders(), new CreateLookupRequest(TABLE_ID,
                "County adjustment lookup", List.of("state", "county"),
                List.of(new LookupOptionDraft(Map.of("state", "IL", "county", "COOK"), value,
                        "Approved fixture option")),
                tenantId, "caller-managed approved fixture lookup"));
        lookupApi.publishLookupOptions(tenantId, publishHeaders(), created.versionId());
    }

    private static CalculationRunHeaders runHeaders() {
        return new CalculationRunHeaders(Set.of(PricingCalculationRunnerApi.CALCULATION_RUN_PERMISSION),
                "calculation-runner", "corr-runner");
    }

    private static LookupHeaders writeHeaders() {
        return new LookupHeaders(Set.of(CalculationDataTableLookupApi.LOOKUP_ADMIN_WRITE_PERMISSION),
                "pricing-admin-1", "corr-lookup-write");
    }

    private static LookupHeaders publishHeaders() {
        return new LookupHeaders(Set.of(CalculationDataTableLookupApi.LOOKUP_ADMIN_PUBLISH_PERMISSION),
                "pricing-publisher-1", "corr-lookup-publish");
    }
}
