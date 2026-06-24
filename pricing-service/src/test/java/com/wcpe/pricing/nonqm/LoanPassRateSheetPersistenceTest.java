package com.wcpe.pricing.nonqm;

import com.wcpe.pricing.nonqm.LoanPassRateSheetModels.RateSheetRowRef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanPassRateSheetPersistenceTest {
    @Test
    void migrationCreatesDurableRateRowsAndOutputRefsWithProvenance() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V9__loanpass_ratesheet_refs.sql"));

        assertTrue(sql.contains("loanpass_ratesheet_import_batch"));
        assertTrue(sql.contains("loanpass_ratesheet_row"));
        assertTrue(sql.contains("loanpass_rate_output_ref"));
        assertTrue(sql.contains("source_provenance"));
        assertTrue(sql.contains("synthetic_dev_only"));
        assertTrue(!sql.toUpperCase().contains("INSERT INTO"));
    }

    @Test
    void modelRejectsSyntheticRateRowsWithoutDevMarker() {
        assertThrows(IllegalArgumentException.class, () -> new RateSheetRowRef("row-1", "batch-1", "tenant-a", "product-a",
                null, null, null, Map.of(), null, null, null, "SYNTHETIC_DEV", "dev fixture", false, null, null));
    }
}
