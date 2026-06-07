package com.wcpe.lock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LockAuditReportApi {
  public static final String POST_REPORT_METHOD = "POST";
  public static final String POST_REPORT_PATH = "/api/v1/tenants/{tenantId}/lock-audit-reports";
  public static final String GET_REPORT_METHOD = "GET";
  public static final String GET_REPORT_PATH = "/api/v1/tenants/{tenantId}/lock-audit-reports/{reportId}";
  public static final String POST_REPLAY_METHOD = "POST";
  public static final String POST_REPLAY_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/replays";
  public static final String POST_CANCELLATION_METHOD = "POST";
  public static final String POST_CANCELLATION_PATH = "/api/v1/tenants/{tenantId}/locks/{lockId}/cancellations";
  public static final String POST_EXPORT_METHOD = "POST";
  public static final String POST_EXPORT_PATH = "/api/v1/tenants/{tenantId}/lock-audit-reports/{reportId}/exports";
  public static final String READ_PERMISSION = "LOCK_AUDIT_READ";
  public static final String EXPORT_PERMISSION = "LOCK_AUDIT_EXPORT";
  public static final String REPLAY_PERMISSION = "LOCK_REPLAY_RUN";
  public static final String CANCEL_PERMISSION = "LOCK_CANCEL";

  private final LockService service;

  public LockAuditReportApi(LockService service) {
    if (service == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock service is required");
    }
    this.service = service;
  }

  public AuditReportResponse postReport(UUID tenantId, String idempotencyKey, String correlationId, AuditReportRequest request) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock audit report request is required");
    }
    return AuditReportResponse.from(service.createAuditReport(new LockModels.LockAuditReportCommand(
      tenantId, request.requestId(), request.actorId(), request.criteria(), request.requestedAt(), request.permissionGranted(),
      idempotencyKey, correlationId, request.sourceRefs()
    )));
  }

  public AuditReportResponse getReport(UUID tenantId, String reportId, boolean permissionGranted) {
    if (!permissionGranted) {
      throw new LockServiceException("TENANT_ACCESS_DENIED", READ_PERMISSION + " permission is required");
    }
    LockModels.LockAuditReportRecord report = service.getAuditReport(tenantId, reportId);
    return new AuditReportResponse(
      report.reportId(), report.status().name(), 1, "Lock audit report metadata read for tenant", List.of(),
      "AUDIT-LOCK-REPORT-" + report.reportId(), "REPLAY-LOCK-REPORT-" + report.criteriaHash().substring(0, 16),
      report.correlationId(), "", report.manifestHash()
    );
  }

  public ReplayResponse postReplay(UUID tenantId, String lockId, String idempotencyKey, String correlationId, ReplayRequest request) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock replay request is required");
    }
    return ReplayResponse.from(service.replayLockReplayAware(new LockModels.LockReplayCommand(
      tenantId, lockId, request.replayId(), request.actorId(), request.capturedInputHash(), request.configGraphHash(),
      request.eventSequenceHash(), request.expectedResultHash(), request.actualResultHash(), request.permissionGranted(),
      idempotencyKey, correlationId, request.replayedAt(), request.historicalRefs()
    )));
  }

  public CancellationResponse postCancellation(
    UUID tenantId,
    String lockId,
    String idempotencyKey,
    String correlationId,
    CancellationRequest request
  ) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock cancellation request is required");
    }
    return CancellationResponse.from(service.cancelLockReplayAware(new LockModels.LockCancellationCommand(
      tenantId, lockId, request.cancellationId(), request.reasonCode(), request.note(), request.actorId(), request.expectedVersion(),
      request.permissionGranted(), request.cancellationPolicyResolved(), request.externalNotifyRequired(), request.policyVersionId(),
      request.complianceEvidenceRef(), idempotencyKey, correlationId, request.cancelledAt(), request.sourceRefs()
    )));
  }

  public EvidenceExportResponse postExport(UUID tenantId, String reportId, String idempotencyKey, String correlationId, EvidenceExportRequest request) {
    if (request == null) {
      throw new LockServiceException("VALIDATION_FAILED", "lock evidence export request is required");
    }
    return EvidenceExportResponse.from(service.createEvidenceExportReplayAware(new LockModels.LockEvidenceExportCommand(
      tenantId, reportId, request.exportId(), request.actorId(), request.eventIds(), request.schemaVersions(), request.configVersions(),
      request.snapshotHashes(), request.generatedFileHashes(), request.purposeCode(), request.permissionGranted(), request.redactedByDefault(),
      idempotencyKey, correlationId, request.generatedAt()
    )));
  }

  public record AuditReportRequest(
    String requestId,
    String actorId,
    Map<String, String> criteria,
    Instant requestedAt,
    boolean permissionGranted,
    Map<String, String> sourceRefs
  ) {}

  public record ReplayRequest(
    String replayId,
    String actorId,
    String capturedInputHash,
    String configGraphHash,
    String eventSequenceHash,
    String expectedResultHash,
    String actualResultHash,
    boolean permissionGranted,
    Instant replayedAt,
    Map<String, String> historicalRefs
  ) {}

  public record CancellationRequest(
    String cancellationId,
    String reasonCode,
    String note,
    String actorId,
    int expectedVersion,
    boolean permissionGranted,
    boolean cancellationPolicyResolved,
    boolean externalNotifyRequired,
    String policyVersionId,
    String complianceEvidenceRef,
    Instant cancelledAt,
    Map<String, String> sourceRefs
  ) {}

  public record EvidenceExportRequest(
    String exportId,
    String actorId,
    List<String> eventIds,
    Map<String, String> schemaVersions,
    Map<String, String> configVersions,
    Map<String, String> snapshotHashes,
    Map<String, String> generatedFileHashes,
    String purposeCode,
    boolean permissionGranted,
    boolean redactedByDefault,
    Instant generatedAt
  ) {}

  public record AuditReportResponse(
    String id,
    String status,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType,
    String manifestHash
  ) {
    static AuditReportResponse from(LockModels.LockAuditReportResponse response) {
      return new AuditReportResponse(
        response.reportId(), response.status().name(), response.version(), response.resultSummary(), response.validationMessages(),
        response.auditRef(), response.replayRef(), response.correlationId(), response.outboxEventType(), response.manifestHash()
      );
    }
  }

  public record ReplayResponse(
    String id,
    String lockId,
    String mismatchClass,
    String evidenceHash,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType
  ) {
    static ReplayResponse from(LockModels.LockReplayResponse response) {
      return new ReplayResponse(
        response.replayId(), response.lockId(), response.mismatchClass().name(), response.evidenceHash(), response.resultSummary(),
        response.validationMessages(), response.auditRef(), response.replayRef(), response.correlationId(), response.outboxEventType()
      );
    }
  }

  public record CancellationResponse(
    String id,
    String lockId,
    String previousStatus,
    String status,
    int version,
    String resultSummary,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType,
    String evidenceHash
  ) {
    static CancellationResponse from(LockModels.LockCancellationResponse response) {
      return new CancellationResponse(
        response.cancellationId(), response.lockId(), response.previousStatus().name(), response.status().name(), response.version(),
        response.resultSummary(), response.validationMessages(), response.auditRef(), response.replayRef(), response.correlationId(),
        response.outboxEventType(), response.evidenceHash()
      );
    }
  }

  public record EvidenceExportResponse(
    String id,
    String reportId,
    String status,
    String manifestHash,
    List<String> eventIds,
    Map<String, String> schemaVersions,
    Map<String, String> configVersions,
    Map<String, String> snapshotHashes,
    Map<String, String> actorRefs,
    Map<String, String> generatedFileHashes,
    boolean redactedByDefault,
    String actorId,
    List<String> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    String eventType
  ) {
    static EvidenceExportResponse from(LockModels.LockEvidenceExportResponse response) {
      return new EvidenceExportResponse(
        response.exportId(), response.reportId(), response.status(), response.manifestHash(), response.eventIds(),
        response.schemaVersions(), response.configVersions(), response.snapshotHashes(), response.actorRefs(), response.generatedFileHashes(),
        response.redactedByDefault(), response.actorId(),
        response.validationMessages(), response.auditRef(), response.replayRef(), response.correlationId(), response.outboxEventType()
      );
    }
  }
}
