package com.wcpe.eligibility;

import com.wcpe.eligibility.nonqm.LoanPassStipulationModels.StipulationRuleRef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanPassStipulationPersistenceTest {
    @Test
    void migrationCreatesDurableTemplatesAndRulesWithProvenance() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V14__loanpass_stipulation_templates.sql"));

        assertTrue(sql.contains("eligibility.loanpass_stipulation_template"));
        assertTrue(sql.contains("eligibility.loanpass_stipulation_rule"));
        assertTrue(sql.contains("source_provenance"));
        assertTrue(sql.contains("synthetic_dev_only"));
        assertTrue(!sql.toUpperCase().contains("INSERT INTO"));
    }

    @Test
    void modelRejectsUnmarkedSyntheticDevRules() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new StipulationRuleRef(
                "rule-1", "tenant-a", "template-1", "product-a", null, null, Map.of(), null,
                "SYNTHETIC_DEV", "dev fixture", null, false, "DRAFT", null, null));

        assertEquals("synthetic dev source must be marked syntheticDevOnly", error.getMessage());
    }
}
