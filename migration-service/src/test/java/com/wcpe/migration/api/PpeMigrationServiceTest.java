package com.wcpe.migration.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PpeMigrationServiceTest {
    private final PpeMigrationService service = new PpeMigrationService(new ObjectMapper());
    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void optimalBlueImportMapsProductsAndRateSheetsReportsUnsupportedFieldsAndMigratesV1() {
        PpeMigrationService.ImportResponse response = service.importPackage(tenantId, new PpeMigrationService.ImportRequest(
                "Optimal Blue", "v1", false,
                new PpeMigrationService.PpeMigrationPackage(
                        List.of(),
                        List.of(),
                        List.of(Map.of(
                                "rate_sheet_id", "OB-RS-2026-06-13",
                                "effective_date", "2026-06-13",
                                "base_rate", "6.500",
                                "Portal Credential", "redacted-not-imported")),
                        List.of(),
                        List.of(),
                        List.of(Map.of(
                                "ob_product_code", "OB-CONF-30",
                                "product_name", "Conforming 30 Year",
                                "investor", "INV-A"))),
                "consultant-1", "corr-ob"));

        assertTrue(response.validationReport().blockers().isEmpty());
        assertTrue(response.loaded());
        assertTrue(response.validationReport().versionCheck().migrated());
        assertEquals("v2", response.validationReport().schemaVersion());
        assertTrue(response.validationReport().warnings().stream().anyMatch(issue -> "UNSUPPORTED_FIELD".equals(issue.code())));
        assertTrue(response.validationReport().mappings().stream()
                .anyMatch(artifact -> "rateSheet".equals(artifact.artifactType())
                        && artifact.canonicalFields().containsKey("rateValue")
                        && artifact.unsupportedFields().contains("Portal Credential")));
        assertTrue(response.integrationCommands().stream().anyMatch(command -> "catalog-service".equals(command.targetService())));
        assertTrue(response.integrationCommands().stream().anyMatch(command -> "rate-feed-service".equals(command.targetService())));
    }

    @Test
    void pollyEligibilityAndAdjustmentsCompileOnlyAfterValidationSucceeds() {
        PpeMigrationService.ImportResponse response = service.importPackage(tenantId, new PpeMigrationService.ImportRequest(
                "POLLY", "v2", false,
                new PpeMigrationService.PpeMigrationPackage(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(Map.of(
                                "matrix_id", "POLLY-ELIG-1",
                                "condition", "fico >= configured_minimum",
                                "scenario_field", "fico")),
                        List.of(Map.of(
                                "adjustment_code", "POLLY-LLPA-1",
                                "adjustment_type", "LLPA",
                                "comparison_operator", ">=")),
                        List.of()),
                "analyst-1", "corr-polly"));

        assertTrue(response.validationReport().blockers().isEmpty());
        assertEquals(1, response.validationReport().compiledRuleCount());
        assertTrue(response.integrationCommands().stream().anyMatch(command -> "adjustment-service".equals(command.targetService())));

        PpeMigrationService.ImportResponse blocked = service.importPackage(tenantId, new PpeMigrationService.ImportRequest(
                "POLLY", "v2", false,
                new PpeMigrationService.PpeMigrationPackage(
                        List.of(), List.of(), List.of(),
                        List.of(Map.of("matrix_id", "POLLY-BLOCKED")),
                        List.of(), List.of()),
                "analyst-1", "corr-polly-blocked"));

        assertFalse(blocked.validationReport().blockers().isEmpty());
        assertEquals(0, blocked.validationReport().compiledRuleCount());
    }

    @Test
    void loanPassNonQmProgramMapsToCanonicalPii39AndPii40Dimensions() {
        PpeMigrationService.ImportResponse response = service.importPackage(tenantId, new PpeMigrationService.ImportRequest(
                "LoanPASS", "v2", false,
                new PpeMigrationService.PpeMigrationPackage(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(Map.of(
                                "loanpass_program_code", "LP-DSCR-INV",
                                "program_name", "Investor DSCR",
                                "non_qm_type", "DSCR",
                                "dscr", "configured-threshold-reference",
                                "bank_statement_months", "12"))),
                "architect-1", "corr-loanpass"));

        assertTrue(response.validationReport().blockers().isEmpty());
        PpeMigrationService.MappedArtifact product = response.validationReport().mappings().stream()
                .filter(artifact -> "product".equals(artifact.artifactType()))
                .findFirst()
                .orElseThrow();
        assertEquals("LP-DSCR-INV", product.canonicalFields().get("productCode"));
        assertEquals("DSCR", product.canonicalFields().get("nonQmProductType"));
        assertEquals("configured-threshold-reference", product.canonicalFields().get("nonQmDscr"));
        assertEquals("12", product.canonicalFields().get("nonQmBankStatementMonths"));
    }

    @Test
    void dryRunAndMissingFieldsBlockPublishWithoutLoadingPricingUsableData() {
        PpeMigrationService.ImportResponse response = service.importPackage(tenantId, new PpeMigrationService.ImportRequest(
                "POLLY", "v2", true,
                new PpeMigrationService.PpeMigrationPackage(
                        List.of(), List.of(), List.of(),
                        List.of(Map.of("matrix_id", "POLLY-MISSING-RULE")),
                        List.of(), List.of()),
                "analyst-2", "corr-dry-run"));

        assertFalse(response.loaded());
        assertTrue(response.dryRun());
        assertTrue(response.validationReport().publishBlocked());
        assertTrue(response.validationReport().blockers().stream().anyMatch(issue -> "MISSING_CANONICAL_FIELD".equals(issue.code())));

        PpeMigrationService.PublishRequestResult publish = service.requestPublish(tenantId, response.importId());
        assertEquals("REJECTED_VALIDATION_BLOCKED", publish.status());
        assertFalse(publish.pricingUsable());
    }

    @Test
    void publishRequestRequiresGovernanceApprovalBeforePricingUse() {
        PpeMigrationService.ImportResponse response = service.importPackage(tenantId, new PpeMigrationService.ImportRequest(
                "OPTIMAL_BLUE", "v2", false,
                new PpeMigrationService.PpeMigrationPackage(
                        List.of(Map.of("rule_id", "RULE-1", "rule_expression", "configured-rule-ref")),
                        List.of(), List.of(), List.of(), List.of(), List.of()),
                "governance-user", "corr-publish"));

        PpeMigrationService.PublishRequestResult publish = service.requestPublish(tenantId, response.importId());

        assertEquals("PENDING_GOVERNANCE_APPROVAL", publish.status());
        assertFalse(publish.pricingUsable());
        assertTrue(publish.integrationCommands().stream().anyMatch(command -> "governance-service".equals(command.targetService())));
    }

    @Test
    void exportCreatesPortableJsonOrYamlPackageAndAuditTrail() {
        PpeMigrationService.ExportResponse response = service.exportPackage(tenantId, new PpeMigrationService.ExportRequest(
                "LoanWeft", "YAML", "migration-admin",
                List.of(Map.of("id", "RULE-EXPORT-1", "ruleExpression", "configured-rule-ref")),
                List.of(Map.of("id", "BOOK-1", "description", "portable rule book")),
                List.of(Map.of("id", "RATE-SHEET-1", "effectiveDate", "2026-06-13")),
                List.of(Map.of("id", "ELIG-1", "ruleExpression", "configured-eligibility-ref"))));

        assertEquals("YAML", response.format());
        assertEquals("v2", response.schemaVersion());
        assertEquals(4, response.artifacts().size());
        assertTrue(response.portableDocument().contains("schemaVersion: v2"));
        assertNotNull(response.packageHash());
        assertTrue(response.auditTrail().stream().anyMatch(event -> "EXPORT_CREATE".equals(event.operation())));
    }
}
