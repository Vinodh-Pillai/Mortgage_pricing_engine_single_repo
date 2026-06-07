package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

final class DriftMonitoringTestFixtures {
  static final String MODEL_VERSION_ID = "model-version-approved-1";
  static final Instant WINDOW_START = Instant.parse("2026-06-06T00:00:00Z");
  static final Instant WINDOW_END = Instant.parse("2026-06-07T00:00:00Z");

  private DriftMonitoringTestFixtures() {}

  static StartMonitoringRunCommand driftRunCommand(String idempotencyKey) {
    return command(idempotencyKey, List.of(criticalDriftMetric()));
  }

  static StartMonitoringRunCommand command(String idempotencyKey, List<MonitoringMetricInput> metrics) {
    return new StartMonitoringRunCommand(
        AdvisoryTestFixtures.TENANT,
        idempotencyKey,
        "model-risk-admin-1",
        MODEL_VERSION_ID,
        AdvisoryType.PRICING,
        WINDOW_START,
        WINDOW_END,
        "tenant-policy:ml-monitoring:v1",
        "ml-advisory-feature-schema-v1",
        "lineage:feature-snapshots:daily-aggregate",
        120,
        100,
        metrics,
        "corr-monitoring-PII-14-S10");
  }

  static MonitoringMetricInput criticalDriftMetric() {
    return new MonitoringMetricInput(
        "DRIFT",
        "population-stability-index",
        0.42,
        0.20,
        "CRITICAL_DRIFT",
        "CRITICAL",
        "REQUEST_ADVISORY_SUSPENSION_REVIEW",
        true,
        false,
        "aggregate:feature-snapshot:income-stability",
        Map.of("window", "daily", "policyVersion", "tenant-policy:ml-monitoring:v1"));
  }

  static MonitoringMetricInput biasMetric(boolean approvedAggregateCohort, boolean containsRawSensitiveData) {
    return new MonitoringMetricInput(
        "BIAS",
        "approved-cohort-disparity-indicator",
        0.31,
        0.20,
        "BIAS_REVIEW",
        "HIGH",
        "CREATE_GOVERNANCE_TICKET",
        approvedAggregateCohort,
        containsRawSensitiveData,
        "aggregate:approved-cohort:fair-lending-review",
        Map.of("cohortRef", "approved-aggregate-only"));
  }
}
