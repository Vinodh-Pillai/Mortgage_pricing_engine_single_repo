package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;

public record StartMonitoringRunCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String modelVersionId,
    AdvisoryType advisoryType,
    Instant windowStart,
    Instant windowEnd,
    String policyVersion,
    String featureSchemaVersion,
    String dataLineageRef,
    int aggregateCohortCount,
    int minimumCohortSize,
    List<MonitoringMetricInput> metrics,
    String correlationId) {
  public StartMonitoringRunCommand {
    metrics = metrics == null ? List.of() : List.copyOf(metrics);
  }
}
