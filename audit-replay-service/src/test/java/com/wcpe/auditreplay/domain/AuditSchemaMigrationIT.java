package com.wcpe.auditreplay.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuditSchemaMigrationIT {

    @Test
    void hasTenantAndSearchIndexes() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V5__audit_log_schema.sql"));

        assertTrue(migration.contains("CREATE TABLE audit_records"));
        assertTrue(migration.contains("CREATE TABLE audit_snapshots"));
        assertTrue(migration.contains("idx_audit_records_tenant_occurred_at"));
        assertTrue(migration.contains("idx_audit_records_tenant_subject"));
        assertTrue(migration.contains("idx_audit_records_tenant_correlation"));
        assertTrue(migration.contains("reject_audit_log_mutation"));
    }
}
