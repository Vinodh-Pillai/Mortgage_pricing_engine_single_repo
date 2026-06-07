package com.wcpe.mladvisory;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ml-advisory")
public final class DriftMonitoringController {
  private final MlAdvisoryControlService service;

  public DriftMonitoringController() {
    this(new MlAdvisoryControlService());
  }

  DriftMonitoringController(MlAdvisoryControlService service) {
    this.service = service;
  }

  @PostMapping("/monitoring-runs")
  public ResponseEntity<?> startRun(@PathVariable String tenantId, @RequestBody MonitoringRunRequest request) {
    MonitoringRunRequest safeRequest = request == null ? MonitoringRunRequest.empty() : request;
    MlAdvisoryResult<ModelMonitoringRun> result = service.startMonitoringRun(safeRequest.toCommand(tenantId));
    if (!result.valid()) {
      String code = result.errorCode().orElse("VALIDATION_FAILED");
      return ResponseEntity.status(statusFor(code)).body(new MonitoringError(code));
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(result.value().orElseThrow());
  }

  @GetMapping("/monitoring-runs")
  public List<ModelMonitoringRun> listRuns(
      @PathVariable String tenantId,
      @RequestParam(required = false) String modelVersionId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return service.monitoringRuns(new MonitoringRunQuery(tenantId, modelVersionId, from, to));
  }

  @GetMapping("/monitoring-alerts")
  public List<MonitoringAlert> listAlerts(
      @PathVariable String tenantId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String severity) {
    return service.monitoringAlerts(new MonitoringAlertQuery(tenantId, status, severity));
  }

  @PostMapping("/monitoring-alerts/{alertId}:acknowledge")
  public ResponseEntity<?> acknowledge(
      @PathVariable String tenantId, @PathVariable String alertId, @RequestBody MonitoringDispositionRequest request) {
    MonitoringDispositionRequest safeRequest = request == null ? MonitoringDispositionRequest.empty() : request;
    MlAdvisoryResult<MonitoringAlert> result = service.acknowledgeMonitoringAlert(safeRequest.toCommand(tenantId, alertId));
    if (!result.valid()) {
      String code = result.errorCode().orElse("VALIDATION_FAILED");
      return ResponseEntity.status(statusFor(code)).body(new MonitoringError(code));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  @PostMapping("/monitoring-alerts/{alertId}:request-suspension")
  public ResponseEntity<?> requestSuspension(
      @PathVariable String tenantId, @PathVariable String alertId, @RequestBody MonitoringDispositionRequest request) {
    MonitoringDispositionRequest safeRequest = request == null ? MonitoringDispositionRequest.empty() : request;
    MlAdvisoryResult<MonitoringAlert> result = service.requestAdvisorySuspensionReview(safeRequest.toCommand(tenantId, alertId));
    if (!result.valid()) {
      String code = result.errorCode().orElse("VALIDATION_FAILED");
      return ResponseEntity.status(statusFor(code)).body(new MonitoringError(code));
    }
    return ResponseEntity.ok(result.value().orElseThrow());
  }

  private HttpStatus statusFor(String code) {
    return switch (code) {
      case "ML_MONITORING_ACTION_UNAUTHORIZED" -> HttpStatus.FORBIDDEN;
      case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "IDEMPOTENCY_CONFLICT" -> HttpStatus.CONFLICT;
      case "ML_MONITORING_POLICY_MISSING", "ML_MONITORING_INSUFFICIENT_DATA" -> HttpStatus.UNPROCESSABLE_ENTITY;
      default -> HttpStatus.BAD_REQUEST;
    };
  }

  public record MonitoringRunRequest(
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
    public MonitoringRunRequest {
      metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    static MonitoringRunRequest empty() {
      return new MonitoringRunRequest("", "", "", null, null, null, "", "", "", 0, 0, List.of(), "");
    }

    StartMonitoringRunCommand toCommand(String tenantId) {
      return new StartMonitoringRunCommand(
          tenantId,
          idempotencyKey,
          actorId,
          modelVersionId,
          advisoryType,
          windowStart,
          windowEnd,
          policyVersion,
          featureSchemaVersion,
          dataLineageRef,
          aggregateCohortCount,
          minimumCohortSize,
          metrics,
          correlationId);
    }
  }

  public record MonitoringDispositionRequest(
      String actorId, Set<String> actorRoles, String dispositionReason, String governanceTicket, String correlationId) {
    public MonitoringDispositionRequest {
      actorRoles = actorRoles == null ? Set.of() : Set.copyOf(actorRoles);
    }

    static MonitoringDispositionRequest empty() {
      return new MonitoringDispositionRequest("", Set.of(), "", "", "");
    }

    MonitoringDispositionCommand toCommand(String tenantId, String alertId) {
      return new MonitoringDispositionCommand(
          tenantId, alertId, actorId, actorRoles, dispositionReason, governanceTicket, correlationId);
    }
  }

  public record MonitoringError(String code) {}
}
