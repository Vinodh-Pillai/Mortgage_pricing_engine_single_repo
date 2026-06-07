package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;

public record ModelMonitoringRun(
    String runId,
    String tenantId,
    String modelVersionId,
    AdvisoryType advisoryType,
    Instant windowStart,
    Instant windowEnd,
    String policyVersion,
    String featureSchemaVersion,
    String dataLineageRef,
    String status,
    String highestSeverity,
    int aggregateCohortCount,
    int minimumCohortSize,
    Instant createdAt,
    Instant completedAt,
    String eventRef,
    String auditRef,
    String correlationId,
    List<MonitoringMetric> metrics,
    List<MonitoringAlert> alerts) {
  public ModelMonitoringRun {
    metrics = metrics == null ? List.of() : List.copyOf(metrics);
    alerts = alerts == null ? List.of() : List.copyOf(alerts);
  }
}
