package com.wcpe.observability.database;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class DatabaseIndexBaselineService {
  public static final String OWNER_STORY = "PII-17-S06";
  public static final String MIGRATION_VERSION = "V3__pii17_s06_database_index_baseline.sql";

  private static final List<DatabaseIndexDefinition> BASELINE = List.of(
      new DatabaseIndexDefinition(
          "cache_operation_audit",
          "idx_cache_op_audit_tenant_status_created",
          List.of("tenant_id", "status", "created_at desc"),
          false,
          "cache-audit-status-dashboard",
          OWNER_STORY,
          "drop index concurrently if exists idx_cache_op_audit_tenant_status_created"),
      new DatabaseIndexDefinition(
          "cache_operation_audit",
          "idx_cache_op_audit_tenant_namespace_operation_created",
          List.of("tenant_id", "namespace", "operation", "created_at desc"),
          false,
          "cache-audit-namespace-operation-dashboard",
          OWNER_STORY,
          "drop index concurrently if exists idx_cache_op_audit_tenant_namespace_operation_created"),
      new DatabaseIndexDefinition(
          "cache_invalidation_request",
          "uq_cache_inv_tenant_idempotency",
          List.of("tenant_id", "idempotency_key"),
          true,
          "cache-invalidation-idempotency-replay",
          OWNER_STORY,
          "alter table cache_invalidation_request drop constraint if exists uq_cache_inv_tenant_idempotency"),
      new DatabaseIndexDefinition(
          "cache_invalidation_request",
          "uq_cache_inv_tenant_source_namespace",
          List.of("tenant_id", "source_event_id", "namespace"),
          true,
          "cache-invalidation-source-replay",
          OWNER_STORY,
          "alter table cache_invalidation_request drop constraint if exists uq_cache_inv_tenant_source_namespace"),
      new DatabaseIndexDefinition(
          "cache_invalidation_request",
          "idx_cache_inv_tenant_namespace_status_created",
          List.of("tenant_id", "namespace", "status", "created_at desc"),
          false,
          "cache-invalidation-status-dashboard",
          OWNER_STORY,
          "drop index concurrently if exists idx_cache_inv_tenant_namespace_status_created"),
      new DatabaseIndexDefinition(
          "cache_invalidation_request",
          "idx_cache_inv_processing_created",
          List.of("status", "attempt_count", "created_at"),
          false,
          "cache-invalidation-worker-processing",
          OWNER_STORY,
          "drop index concurrently if exists idx_cache_inv_processing_created"));

  private static final List<String> METRIC_NAMES = List.of(
      "db.query.latency",
      "db.index.scan.count",
      "db.seq_scan.count",
      "db.connection.pool.active",
      "db.lock.wait");

  private static final List<String> RUNBOOK_STEPS = List.of(
      "Inspect the DB performance dashboard query class and latest EXPLAIN status.",
      "Confirm tenant predicates are present before comparing index usage.",
      "Refresh statistics with an approved analyze operation in dev or stage.",
      "If rollout blocks writes, stop rollout and apply the rollback note for only the newly created index.");

  private final Clock clock;

  public DatabaseIndexBaselineService(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public List<DatabaseIndexDefinition> baseline() {
    return BASELINE;
  }

  public DatabasePerformanceHealthSnapshot healthSnapshot(String latestExplainPlanStatus) {
    return new DatabasePerformanceHealthSnapshot(
        "UP",
        MIGRATION_VERSION,
        sanitizeStatus(latestExplainPlanStatus),
        BASELINE.stream().map(DatabaseIndexDefinition::queryClass).distinct().toList(),
        METRIC_NAMES,
        RUNBOOK_STEPS,
        clock.instant());
  }

  private static String sanitizeStatus(String value) {
    if (value == null || value.isBlank()) {
      return "explain-plan-not-captured";
    }
    String trimmed = value.trim();
    if (!trimmed.matches("[A-Za-z0-9 ._:-]+")) {
      throw new IllegalArgumentException("latestExplainPlanStatus must be sanitized");
    }
    return trimmed;
  }
}
