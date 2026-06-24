package com.wcpe.lock.nonqm;

import com.wcpe.lock.LockServiceException;
import com.wcpe.lock.nonqm.LoanPassLockTermModels.LockTermOptionRef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanPassLockTermPersistenceTest {
    @Test
    void migrationCreatesDurableLockTermOptionsWithProvenance() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V8__loanpass_lock_term_options.sql"));

        assertTrue(sql.contains("lock_service.loanpass_lock_term_option"));
        assertTrue(sql.contains("lock_term_days"));
        assertTrue(sql.contains("source_provenance"));
        assertTrue(sql.contains("synthetic_dev_only"));
        assertTrue(!sql.toUpperCase().contains("INSERT INTO"));
    }

    @Test
    void modelRejectsNonPositiveTermDays() {
        assertThrows(LockServiceException.class, () -> new LockTermOptionRef("term-1", "tenant-a", "product-a", null, null,
                0, null, null, Map.of(), "LOANHOUSE_PUBLIC", "public capture shape", null, false, "DRAFT", null, null));
    }
}
