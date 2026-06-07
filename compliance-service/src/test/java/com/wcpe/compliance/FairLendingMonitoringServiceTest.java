package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.FairLendingMonitoringService.FairLendingAlert;
import com.wcpe.compliance.FairLendingMonitoringService.FairLendingMonitorConfigVersion;
import com.wcpe.compliance.FairLendingMonitoringService.FairLendingSnapshotRequest;
import com.wcpe.compliance.FairLendingMonitoringService.FairLendingSnapshotResult;
import com.wcpe.compliance.FairLendingMonitoringService.MetricDefinition;
import com.wcpe.compliance.FairLendingMonitoringService.OutcomeMeasure;
import com.wcpe.compliance.FairLendingMonitoringService.SourceCompleteness;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FairLendingMonitoringServiceTest {
  @Test
  void usesConfiguredMetricDefinition() {
    FairLendingSnapshotResult result =
        FairLendingMonitoringService.runSnapshot(
            request(
                true,
                true,
                new SourceCompleteness(250, new BigDecimal("0.99000000"), "complete-rule-v1", List.of("lock-events:2026-05")),
                List.of(outcome("RATE_DISPARITY", "0.03149", 250, List.of("pricing-event-1"))),
                List.of(config(metric("RATE_DISPARITY", "0.03000", 100, "HIGH", "RATE_DISPARITY_ALERT")))));

    assertEquals("completed_alert", result.status());
    assertEquals("fair-lending-config-2026-05", result.configVersionRef().configVersionId());
    assertEquals(new BigDecimal("0.03149000"), result.metricResults().get(0).value());
    assertEquals("fair-lending-rate-disparity-formula-v1", result.metricResults().get(0).formulaRef());
    assertEquals("rate-disparity-threshold-config-v1", result.metricResults().get(0).thresholdRef());
    assertEquals("HIGH", result.alerts().get(0).severity());
    assertEquals(List.of("FairLendingSnapshotCompleted.v1", "FairLendingAlertRaised.v1"), result.outboxEventTypes());
    assertTrue(result.auditRef().startsWith("fair-lending-monitoring-audit:sha256:"));
  }

  @Test
  void failsClosedForInsufficientPopulation() {
    FairLendingSnapshotResult result =
        FairLendingMonitoringService.runSnapshot(
            request(
                true,
                true,
                new SourceCompleteness(75, new BigDecimal("0.99000000"), "complete-rule-v1", List.of("pricing-events")),
                List.of(outcome("RATE_DISPARITY", "0.04000", 75, List.of("pricing-event-1"))),
                List.of(config(metric("RATE_DISPARITY", "0.03000", 100, "HIGH", "RATE_DISPARITY_ALERT")))));

    assertEquals("blocked_missing_config", result.status());
    assertEquals(List.of("INSUFFICIENT_POPULATION:RATE_DISPARITY"), result.reasonCodes());
    assertEquals(List.of("FairLendingSnapshotFailedClosed.v1"), result.outboxEventTypes());
  }

  @Test
  void raisesConfiguredSeverity() {
    FairLendingSnapshotResult result =
        FairLendingMonitoringService.runSnapshot(
            request(
                true,
                true,
                new SourceCompleteness(120, new BigDecimal("0.97000000"), "complete-rule-v1", List.of("pricing-events")),
                List.of(outcome("CONCESSION_DISPARITY", "0.01250", 120, List.of("concession-event-1"))),
                List.of(config(metric("CONCESSION_DISPARITY", "0.01000", 100, "MEDIUM", "CONCESSION_ALERT")))));

    assertEquals("completed_alert", result.status());
    assertEquals("CONCESSION_DISPARITY", result.alerts().get(0).metricCode());
    assertEquals("MEDIUM", result.alerts().get(0).severity());
    assertEquals("raised", result.alerts().get(0).status());
    assertEquals("CONCESSION_ALERT", result.reasonCodes().get(0));
  }

  @Test
  void missingApprovedConfigFailsClosed() {
    FairLendingSnapshotResult result =
        FairLendingMonitoringService.runSnapshot(
            request(
                true,
                true,
                new SourceCompleteness(120, new BigDecimal("0.97000000"), "complete-rule-v1", List.of("pricing-events")),
                List.of(outcome("RATE_DISPARITY", "0.01250", 120, List.of("pricing-event-1"))),
                List.of()));

    assertEquals("blocked_missing_config", result.status());
    assertEquals(List.of("MISSING_MONITOR_CONFIG"), result.reasonCodes());
  }

  @Test
  void redactsProtectedClassForUnauthorizedUser() {
    FairLendingSnapshotResult result =
        FairLendingMonitoringService.runSnapshot(
            request(
                false,
                true,
                new SourceCompleteness(250, new BigDecimal("0.99000000"), "complete-rule-v1", List.of("lock-events:2026-05")),
                List.of(outcome("RATE_DISPARITY", "0.02000", 250, List.of("pricing-event-1"))),
                List.of(config(metric("RATE_DISPARITY", "0.03000", 100, "HIGH", "RATE_DISPARITY_CLEAR")))));

    assertEquals("completed_clear", result.status());
    assertEquals("none", result.metricResults().get(0).severity());
    assertTrue(result.metricResults().get(0).protectedClassRedacted());
    assertFalse(result.resultHash().contains("protected-class-key"));
    assertFalse(result.auditRef().contains("protected-class-key"));
  }

  @Test
  void replayHashIsDeterministic() {
    FairLendingSnapshotRequest request =
        request(
            true,
            true,
            new SourceCompleteness(250, new BigDecimal("0.99000000"), "complete-rule-v1", List.of("lock-events:2026-05")),
            List.of(outcome("RATE_DISPARITY", "0.03149", 250, List.of("pricing-event-1"))),
            List.of(config(metric("RATE_DISPARITY", "0.03000", 100, "HIGH", "RATE_DISPARITY_ALERT"))));

    FairLendingSnapshotResult first = FairLendingMonitoringService.runSnapshot(request);
    FairLendingSnapshotResult second = FairLendingMonitoringService.runSnapshot(request);

    assertEquals(first.resultHash(), second.resultHash());
    assertEquals(first, FairLendingMonitoringService.replay(request, first.resultHash()));
    assertFalse(first.resultHash().contains("protected-class-key"));
  }

  @Test
  void replayHashMismatchFailsClosed() {
    FairLendingSnapshotResult result =
        FairLendingMonitoringService.replay(
            request(
                true,
                true,
                new SourceCompleteness(250, new BigDecimal("0.99000000"), "complete-rule-v1", List.of("lock-events:2026-05")),
                List.of(outcome("RATE_DISPARITY", "0.03149", 250, List.of("pricing-event-1"))),
                List.of(config(metric("RATE_DISPARITY", "0.03000", 100, "HIGH", "RATE_DISPARITY_ALERT")))),
            "sha256:wrong");

    assertEquals("POLICY_NOT_SATISFIED", result.status());
    assertTrue(result.reasonCodes().contains("FAIR_LENDING_REPLAY_HASH_MISMATCH"));
    assertEquals(List.of("FairLendingSnapshotFailedClosed.v1"), result.outboxEventTypes());
  }

  @Test
  void alertWorkflowEnforcesStatusTransitions() {
    FairLendingAlert raised =
        FairLendingMonitoringService.runSnapshot(
                request(
                    true,
                    true,
                    new SourceCompleteness(250, new BigDecimal("0.99000000"), "complete-rule-v1", List.of("lock-events")),
                    List.of(outcome("RATE_DISPARITY", "0.03149", 250, List.of("pricing-event-1"))),
                    List.of(config(metric("RATE_DISPARITY", "0.03000", 100, "HIGH", "RATE_DISPARITY_ALERT")))))
            .alerts()
            .get(0);

    FairLendingAlert reviewed =
        FairLendingMonitoringService.reviewAlert(
            raised, "compliance-manager", "analyst-a", "Investigation opened.");
    FairLendingAlert resolved =
        FairLendingMonitoringService.resolveAlert(
            reviewed, "compliance-manager", "DOCUMENTED_NO_ACTION", "Documented review complete.");

    assertEquals("under_review", reviewed.status());
    assertEquals("analyst-a", reviewed.assignedTo());
    assertEquals(List.of("FairLendingAlertReviewed.v1"), reviewed.outboxEventTypes());
    assertEquals("resolved", resolved.status());
    assertEquals("DOCUMENTED_NO_ACTION", resolved.disposition());
    assertEquals(List.of("FairLendingAlertResolved.v1"), resolved.outboxEventTypes());

    ComplianceShellValidationError error =
        assertInstanceOf(
            ComplianceShellValidationError.class,
            assertThrows(
                RuntimeException.class,
                () -> FairLendingMonitoringService.resolveAlert(raised, "manager", "CLOSED", "bad transition")));
    assertEquals(List.of("ALERT_STATUS_CONFLICT"), error.getDetails());
  }

  @Test
  void malformedRequestReturnsProjectStandardValidationErrorShape() {
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> FairLendingMonitoringService.runSnapshot(null));

    ComplianceShellValidationError error = assertInstanceOf(ComplianceShellValidationError.class, thrown);
    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertEquals(List.of("request"), error.getDetails());
  }

  private static FairLendingSnapshotRequest request(
      boolean protectedClassDetailAuthorized,
      boolean protectedClassDetailsRequested,
      SourceCompleteness sourceCompleteness,
      List<OutcomeMeasure> outcomes,
      List<FairLendingMonitorConfigVersion> configs) {
    return new FairLendingSnapshotRequest(
        "tenant-a",
        "snapshot-2026-05",
        "compliance-analyst",
        LocalDate.parse("2026-05-01"),
        LocalDate.parse("2026-05-31"),
        "CONVENTIONAL",
        "RETAIL",
        "CA",
        protectedClassDetailAuthorized,
        protectedClassDetailsRequested,
        sourceCompleteness,
        outcomes,
        configs,
        "idem-123",
        "correlation-456");
  }

  private static OutcomeMeasure outcome(
      String metricCode, String value, int populationCount, List<String> supportingRefs) {
    return new OutcomeMeasure(
        metricCode,
        "peer-group-configured",
        "comparison-group-configured",
        new BigDecimal(value),
        populationCount,
        supportingRefs,
        List.of("protected-class-key-minimized"));
  }

  private static FairLendingMonitorConfigVersion config(MetricDefinition... metrics) {
    return new FairLendingMonitorConfigVersion(
        "tenant-a",
        "fair-lending-config-2026-05",
        3,
        "APPROVED",
        LocalDate.parse("2026-01-01"),
        LocalDate.parse("2026-12-31"),
        "CONVENTIONAL",
        "RETAIL",
        "CA",
        "protected-class-policy-approved-v1",
        List.of(metrics),
        "config-hash-approved");
  }

  private static MetricDefinition metric(
      String metricCode, String threshold, int minimumPopulation, String alertSeverity, String reasonCode) {
    return new MetricDefinition(
        metricCode,
        "fair-lending-" + metricCode.toLowerCase().replace('_', '-') + "-formula-v1",
        metricCode.toLowerCase().replace('_', '-') + "-threshold-config-v1",
        ">=",
        new BigDecimal(threshold),
        minimumPopulation,
        8,
        RoundingMode.HALF_UP,
        alertSeverity,
        reasonCode);
  }
}
