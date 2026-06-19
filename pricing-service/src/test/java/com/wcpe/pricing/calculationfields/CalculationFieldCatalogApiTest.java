package com.wcpe.pricing.calculationfields;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.CalculationFieldCatalogException;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.CalculationFieldHeaders;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.CalculationFieldImport;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.CalculationFieldImportValidationResult;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.CalculationFieldCatalogResponse;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.SourceFieldDependency;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.ImportedCalculationField;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.TenantCalculationFieldConfig;
import com.wcpe.pricing.calculationfields.CalculationFieldCatalogApi.UnavailableHandling;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CalculationFieldCatalogApiTest {
    private final CalculationFieldCatalogApi api = new CalculationFieldCatalogApi();

    @Test
    void catalogQueryReturnsCalculationFieldMetadataForTenant() {
        CalculationFieldCatalogResponse response = api.catalogFields(
                "tenant-a",
                headers(),
                referenceImport(),
                TenantCalculationFieldConfig.allowAll());

        assertEquals("tenant-a", response.tenantId());
        assertEquals("corr-calc-fields", response.correlationId());
        assertEquals(3, response.fields().size());
        assertEquals("calc-field@fha-1st-premium", response.fields().get(0).id());
        assertEquals("Ledger FHA 1st Premium", response.fields().get(0).name());
        assertEquals("number", response.fields().get(0).valueType());
        assertEquals("ReferenceFormfields.json", response.fields().get(0).source());
        assertEquals("Last active ledger value for FHA 1st Premium", response.fields().get(0).description());
        assertTrue(response.fields().get(0).tenantAvailable());
        assertEquals("AVAILABLE", response.fields().get(0).tenantAvailability());
    }

    @Test
    void catalogEntriesExposeSourceDependenciesAndKnownConsumers() {
        CalculationFieldCatalogResponse response = api.catalogFields(
                "tenant-a",
                headers(),
                referenceImport(),
                TenantCalculationFieldConfig.allowAll());

        var ltv = response.fields().stream()
                .filter(field -> "calc@ltv".equals(field.id()))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of(new SourceFieldDependency("field@loan-to-value", "field-library-v3")),
                ltv.sourceDependencies());
        assertEquals(List.of("adjustments", "margins", "pipeline", "pricing"), ltv.consumers());
    }

    @Test
    void settingsOptionsComeFromAvailableCatalogFields() {
        CalculationFieldCatalogResponse response = api.catalogFields(
                "tenant-a",
                headers(),
                referenceImport(),
                new TenantCalculationFieldConfig(Set.of("calc@ltv"), UnavailableHandling.HIDE_UNAVAILABLE));

        assertEquals(List.of("calc-field@fha-1st-premium", "calc@adjusted-price"), response.settingsOptions().stream()
                .map(CalculationFieldCatalogApi.CalculationFieldSettingsOption::id)
                .toList());
        assertFalse(response.settingsOptions().stream().anyMatch(option -> "calc@ltv".equals(option.id())));
    }

    @Test
    void inactiveDefinitionsAreExcludedFromCatalogAndSettings() {
        CalculationFieldImport importWithInactiveDefinition = new CalculationFieldImport("ReferenceFormfields.json", List.of(
                new ImportedCalculationField("ledger-calc@ltv", "Ledger LTV", "number",
                        "Last active ledger value for LTV"),
                new ImportedCalculationField("calc@retired-output", "Retired Output", "number",
                        "Inactive source metadata definition", null, List.of(), Set.of("pricing"), false)));

        CalculationFieldCatalogResponse response = api.catalogFields(
                "tenant-a",
                headers(),
                importWithInactiveDefinition,
                TenantCalculationFieldConfig.allowAll());

        assertEquals(List.of("calc@ltv"), response.fields().stream()
                .map(CalculationFieldCatalogApi.CalculationFieldCatalogEntry::id)
                .toList());
        assertFalse(response.settingsOptions().stream()
                .anyMatch(option -> "calc@retired-output".equals(option.id())));
    }

    @Test
    void tenantUnavailableFieldsCanBeMarkedInsteadOfHidden() {
        CalculationFieldCatalogResponse response = api.catalogFields(
                "tenant-a",
                headers(),
                referenceImport(),
                new TenantCalculationFieldConfig(Set.of("calc@ltv"), UnavailableHandling.MARK_UNAVAILABLE));

        var ltv = response.fields().stream()
                .filter(field -> "calc@ltv".equals(field.id()))
                .findFirst()
                .orElseThrow();

        assertFalse(ltv.tenantAvailable());
        assertEquals("UNAVAILABLE_FOR_TENANT", ltv.tenantAvailability());
        assertTrue(response.settingsOptions().stream()
                .filter(option -> "calc@ltv".equals(option.id()))
                .findFirst()
                .orElseThrow()
                .tenantAvailability()
                .equals("UNAVAILABLE_FOR_TENANT"));
    }

    @Test
    void importValidationRecognizesCalculationSourcedMetadataPrefixes() {
        CalculationFieldImportValidationResult result = api.validateImportedFields(new CalculationFieldImport(
                "ReferenceFormfields.json",
                List.of(
                        new ImportedCalculationField("calc@final-interest-rate", "Final Interest Rate", "number", null),
                        new ImportedCalculationField("calc-field@manual-factor", "Manual Factor", "number", null),
                        new ImportedCalculationField("ledger-calc@ltv", "Ledger LTV", "number", null),
                        new ImportedCalculationField("ordinary-field", "Ordinary Field", "text", null))));

        assertEquals(List.of("calc-field@manual-factor", "calc@final-interest-rate", "calc@ltv"), result.calculationFieldIds());
        assertEquals(List.of("ordinary-field"), result.unsupportedFieldIds());
        assertTrue(CalculationFieldCatalogApi.isCalculationFieldId("calc@final-interest-rate"));
        assertTrue(CalculationFieldCatalogApi.isCalculationFieldId("calc-field@manual-factor"));
    }

    @Test
    void missingReadPermissionFailsBeforeCatalogDataIsReturned() {
        assertThrows(CalculationFieldCatalogException.class,
                () -> api.catalogFields("tenant-a", new CalculationFieldHeaders(Set.of(), "actor-1", "corr"),
                        referenceImport(), TenantCalculationFieldConfig.allowAll()));
    }

    @Test
    void evaluationRequestWithoutSourceConfigurationReturnsBlockerInsteadOfValue() {
        var result = api.evaluationAvailability("tenant-a", headers(), "calc@missing", referenceImport());

        assertEquals("calc@missing", result.fieldId());
        assertEquals("BLOCKED_MISSING_SOURCE_CONFIGURATION", result.status());
        assertTrue(result.blockerReason().contains("values were not invented"));
    }

    private static CalculationFieldHeaders headers() {
        return new CalculationFieldHeaders(Set.of(CalculationFieldCatalogApi.CALCULATION_FIELD_READ_PERMISSION),
                "actor-1", "corr-calc-fields");
    }

    private static CalculationFieldImport referenceImport() {
        return new CalculationFieldImport("ReferenceFormfields.json", List.of(
                new ImportedCalculationField("ledger-calc@ltv", "Ledger LTV", "number",
                        "Last active ledger value for LTV", "ledger-v1",
                        List.of(new SourceFieldDependency("field@loan-to-value", "field-library-v3")),
                        Set.of("pipeline", "adjustments", "margins", "pricing")),
                new ImportedCalculationField("calc@adjusted-price", "Adjusted Price", "number",
                        "Calculation output for adjusted price"),
                new ImportedCalculationField("ledger-calc-field@fha-1st-premium", "Ledger FHA 1st Premium", "number",
                        "Last active ledger value for FHA 1st Premium"),
                new ImportedCalculationField("ordinary-field", "Ordinary Field", "text",
                        "Not a calculation-sourced field")));
    }
}
