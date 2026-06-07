package com.wcpe.observability.database;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DatabasePerformanceHealthSnapshot(
    String status,
    String migrationVersion,
    String latestExplainPlanStatus,
    List<String> queryClasses,
    List<String> metricNames,
    List<String> runbookSteps,
    Instant generatedAt) {
  public DatabasePerformanceHealthSnapshot {
    status = requireText(status, "status");
    migrationVersion = requireText(migrationVersion, "migrationVersion");
    latestExplainPlanStatus = requireText(latestExplainPlanStatus, "latestExplainPlanStatus");
    queryClasses = List.copyOf(Objects.requireNonNull(queryClasses, "queryClasses is required"));
    metricNames = List.copyOf(Objects.requireNonNull(metricNames, "metricNames is required"));
    runbookSteps = List.copyOf(Objects.requireNonNull(runbookSteps, "runbookSteps is required"));
    generatedAt = Objects.requireNonNull(generatedAt, "generatedAt is required");
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }
}
