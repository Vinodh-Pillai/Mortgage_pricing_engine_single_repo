package com.wcpe.mladvisory;

import java.util.Map;

public record MonitoringMetric(
    String metricId,
    String runId,
    String metricType,
    String metricName,
    double valueNumeric,
    double thresholdNumeric,
    String band,
    String severity,
    String sourceRef,
    Map<String, String> metadata) {
  public MonitoringMetric {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
