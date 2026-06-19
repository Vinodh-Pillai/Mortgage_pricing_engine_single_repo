package com.wcpe.pricing.calculationrunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationDefinition;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationOutputStep;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationRunHeaders;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationRunRequest;
import com.wcpe.pricing.calculationrunner.PricingCalculationRunnerApi.CalculationRunStatus;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CreateLookupRequest;
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

    private CalculationDefinition fixtureDefinition(String tenantId) {
        return new CalculationDefinition(CALCULATION_ID, tenantId, VERSION_ID, List.of(
                CalculationOutputStep.fromLookup("calc@county-factor", TABLE_ID,
                        Map.of("state", "state", "county", "county")),
                CalculationOutputStep.fromInput("calc@monthly-payment", "source-payment")));
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
