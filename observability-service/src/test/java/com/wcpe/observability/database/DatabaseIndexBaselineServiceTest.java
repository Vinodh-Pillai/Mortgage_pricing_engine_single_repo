package com.wcpe.observability.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseIndexBaselineServiceTest {
  private static final Instant NOW = Instant.parse("2026-06-07T21:00:00Z");

  @Test
  void exposesSanitizedDatabasePerformanceHealthSnapshot() {
    DatabaseIndexBaselineService service = new DatabaseIndexBaselineService(Clock.fixed(NOW, ZoneOffset.UTC));

    DatabasePerformanceHealthSnapshot snapshot = service.healthSnapshot("pricing-cache explain-plan captured");

    assertEquals("UP", snapshot.status());
    assertEquals("V3__pii17_s06_database_index_baseline.sql", snapshot.migrationVersion());
    assertEquals("pricing-cache explain-plan captured", snapshot.latestExplainPlanStatus());
    assertEquals(NOW, snapshot.generatedAt());
    assertTrue(snapshot.metricNames().contains("db.query.latency"));
    assertTrue(snapshot.metricNames().contains("db.index.scan.count"));
    assertTrue(snapshot.metricNames().contains("db.seq_scan.count"));
    assertTrue(snapshot.queryClasses().contains("cache-invalidation-status-dashboard"));
    assertTrue(snapshot.runbookSteps().stream().anyMatch(step -> step.contains("tenant predicates")));
  }

  @Test
  void rejectsUnsanitizedExplainPlanStatus() {
    DatabaseIndexBaselineService service = new DatabaseIndexBaselineService(Clock.fixed(NOW, ZoneOffset.UTC));

    assertThrows(IllegalArgumentException.class, () -> service.healthSnapshot("select * from loans where borrower='PII'"));
  }

  @Test
  void baselineIndexesAreTenantScopedAndRollbackReady() {
    DatabaseIndexBaselineService service = new DatabaseIndexBaselineService(Clock.fixed(NOW, ZoneOffset.UTC));

    List<DatabaseIndexDefinition> definitions = service.baseline();

    assertFalse(definitions.isEmpty());
    assertTrue(definitions.stream().allMatch(definition -> "PII-17-S06".equals(definition.ownerStory())));
    assertTrue(definitions.stream()
        .filter(definition -> !definition.indexName().startsWith("idx_cache_inv_processing"))
        .allMatch(definition -> definition.orderedColumns().get(0).equals("tenant_id")));
    assertTrue(definitions.stream().anyMatch(definition -> definition.unique()
        && definition.indexName().equals("uq_cache_inv_tenant_idempotency")));
    assertTrue(definitions.stream().allMatch(definition -> definition.rollbackNote().contains("if exists")));
  }

  @Test
  void migrationNamesOwnerStoryAndCreatesExpectedIndexes() throws IOException {
    String migration = Files.readString(Path.of(
        "src/main/resources/db/migration/V3__pii17_s06_database_index_baseline.sql"));

    assertTrue(migration.contains("owner_story: PII-17-S06"));
    assertTrue(migration.contains("create index if not exists idx_cache_op_audit_tenant_status_created"));
    assertTrue(migration.contains("on cache_operation_audit (tenant_id, status, created_at desc)"));
    assertTrue(migration.contains("create index if not exists idx_cache_inv_tenant_namespace_status_created"));
    assertTrue(migration.contains("on cache_invalidation_request (tenant_id, namespace, status, created_at desc)"));
    assertTrue(migration.contains("uq_cache_inv_tenant_idempotency"));
  }
}
