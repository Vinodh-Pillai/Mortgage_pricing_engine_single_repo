package com.wcpe.mladvisory;

import java.util.Map;

public record MonitoringMetricInput(
    String metricType,
    String metricName,
    double valueNumeric,
    double thresholdNumeric,
    String band,
    String severity,
    String recommendedAction,
    boolean approvedAggregateCohort,
    boolean containsRawSensitiveData,
    String sourceRef,
    Map<String, String> metadata) {
  public MonitoringMetricInput {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
