package com.wcpe.mladvisory;

import java.time.Instant;

public record MonitoringAlert(
    String alertId,
    String tenantId,
    String modelVersionId,
    String runId,
    String metricType,
    String metricName,
    String severity,
    String status,
    String recommendedAction,
    Instant createdAt,
    String acknowledgedBy,
    String dispositionReason,
    String governanceTicket,
    boolean advisoryOnly,
    String eventRef,
    String auditRef,
    String correlationId) {
  MonitoringAlert withDisposition(
      String nextStatus,
      String actorId,
      String reason,
      String ticket,
      String dispositionEventRef,
      String dispositionAuditRef,
      String nextCorrelationId) {
    return new MonitoringAlert(
        alertId,
        tenantId,
        modelVersionId,
        runId,
        metricType,
        metricName,
        severity,
        nextStatus,
        recommendedAction,
        createdAt,
        actorId,
        reason,
        ticket,
        advisoryOnly,
        dispositionEventRef,
        dispositionAuditRef,
        nextCorrelationId);
  }
}
