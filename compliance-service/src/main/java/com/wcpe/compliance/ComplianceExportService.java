package com.wcpe.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ComplianceExportService {
  public static final String REQUESTED_EVENT_TYPE = "ComplianceExportRequested.v1";
  public static final String APPROVED_EVENT_TYPE = "ComplianceExportApproved.v1";
  public static final String COMPLETED_EVENT_TYPE = "ComplianceExportCompleted.v1";
  public static final String FAILED_EVENT_TYPE = "ComplianceExportFailed.v1";
  public static final String EXPIRED_EVENT_TYPE = "ComplianceExportExpired.v1";

  public static final String PENDING_APPROVAL = "pending_approval";
  public static final String READY_TO_RUN = "ready_to_run";
  public static final String APPROVED = "approved";
  public static final String COMPLETED = "completed";
  public static final String FAILED_BLOCKED = "failed_blocked";
  public static final String EXPIRED = "expired";
  public static final String POLICY_NOT_SATISFIED = "POLICY_NOT_SATISFIED";

  private ComplianceExportService() {}

  public static ComplianceExportJob createExportRequest(CreateComplianceExportRequest request) {
    validateCreateRequest(request);
    List<String> reasonCodes = new ArrayList<>();
    if (!request.templateVersionRef().approved()) {
      reasonCodes.add("EXPORT_TEMPLATE_NOT_APPROVED");
    }
    if (request.sensitiveExport()) {
      if (request.redactionProfileRef() == null) {
        reasonCodes.add("REDACTION_PROFILE_REQUIRED");
      } else if (!request.redactionProfileRef().approved()) {
        reasonCodes.add("REDACTION_PROFILE_NOT_APPROVED");
      }
      if (request.deliveryPolicyRef() == null || !request.deliveryPolicyRef().approved()) {
        reasonCodes.add("DELIVERY_POLICY_NOT_APPROVED");
      }
    }
    if (!reasonCodes.isEmpty()) {
      return failed(request, reasonCodes);
    }
    String status = request.approvalRequired() || request.sensitiveExport() ? PENDING_APPROVAL : READY_TO_RUN;
    return new ComplianceExportJob(
        request.exportId().trim(),
        request.tenantId().trim(),
        status,
        request.requestedBy().trim(),
        null,
        request.templateVersionRef().normalized(),
        request.redactionProfileRef() == null ? null : request.redactionProfileRef().normalized(),
        request.subjectFilter().normalized(),
        request.deliveryPolicyRef() == null ? null : request.deliveryPolicyRef().normalized(),
        null,
        0,
        null,
        request.requestedAt(),
        request.requestedAt(),
        request.expiresAt(),
        request.artifactTypes(),
        request.sourceSnapshotRefs(),
        request.sensitiveExport(),
        request.idempotencyKey().trim(),
        request.correlationId().trim(),
        auditRef(request.tenantId(), request.exportId(), request.correlationId()),
        List.of(REQUESTED_EVENT_TYPE),
        List.of());
  }

  public static ComplianceExportJob approveExport(ComplianceExportJob job, ApprovalCommand command) {
    validateJob(job);
    validateApprovalCommand(command);
    if (!PENDING_APPROVAL.equals(job.status())) {
      throw validation("Compliance export approval validation failed.", List.of("EXPORT_STATUS_CONFLICT"));
    }
    if (sameActor(job.requestedBy(), command.actorId())) {
      throw validation("Compliance export approval validation failed.", List.of("SOD_VIOLATION"));
    }
    return withStatus(job, APPROVED, command.actorId().trim(), null, List.of(APPROVED_EVENT_TYPE), List.of());
  }

  public static ComplianceExportJob runExport(ComplianceExportJob job, RunComplianceExport command) {
    validateJob(job);
    validateRunCommand(command);
    if (!(READY_TO_RUN.equals(job.status()) || APPROVED.equals(job.status()))) {
      throw validation("Compliance export run validation failed.", List.of("EXPORT_APPROVAL_REQUIRED"));
    }
    List<ExportArtifactRef> artifacts = sortedArtifacts(command.artifacts());
    List<String> defects = artifactDefects(job, artifacts);
    if (!defects.isEmpty()) {
      return withStatus(job, FAILED_BLOCKED, job.approvedBy(), null, List.of(FAILED_EVENT_TYPE), defects);
    }
    ExportManifest manifest = manifest(job, artifacts, command.completedAt());
    return new ComplianceExportJob(
        job.exportId(),
        job.tenantId(),
        COMPLETED,
        job.requestedBy(),
        job.approvedBy(),
        job.templateVersionRef(),
        job.redactionProfileRef(),
        job.subjectFilter(),
        job.deliveryPolicyRef(),
        manifest.manifestHash(),
        artifacts.size(),
        manifest,
        job.requestedAt(),
        command.completedAt(),
        job.expiresAt(),
        job.artifactTypes(),
        job.sourceSnapshotRefs(),
        job.sensitiveExport(),
        job.idempotencyKey(),
        command.correlationId().trim(),
        auditRef(job.tenantId(), job.exportId(), manifest.manifestHash()),
        List.of(COMPLETED_EVENT_TYPE),
        List.of());
  }

  public static ExportManifest manifestForDownload(
      ComplianceExportJob job, DownloadCommand command, Instant now) {
    validateJob(job);
    validateDownloadCommand(command);
    if (!COMPLETED.equals(job.status()) || job.manifest() == null) {
      throw validation("Compliance export download validation failed.", List.of("EXPORT_NOT_COMPLETED"));
    }
    if (job.expiresAt() != null && (now == null || !now.isBefore(job.expiresAt()))) {
      throw validation("Compliance export download validation failed.", List.of("EXPORT_EXPIRED"));
    }
    return job.manifest();
  }

  public static ComplianceExportJob expireExport(ComplianceExportJob job, ExpireCommand command) {
    validateJob(job);
    if (command == null || command.actorId() == null || command.actorId().isBlank()) {
      throw validation("Compliance export expiry validation failed.", List.of("actorId must be provided"));
    }
    return withStatus(job, EXPIRED, job.approvedBy(), job.manifest(), List.of(EXPIRED_EVENT_TYPE), List.of());
  }

  public static String hashPayload(String payload) {
    if (payload == null || payload.isBlank()) {
      throw validation("Compliance export validation failed.", List.of("payload must be a non-empty string"));
    }
    return "sha256:" + sha256("compliance-export-payload|" + payload.trim());
  }

  private static ComplianceExportJob failed(CreateComplianceExportRequest request, List<String> reasonCodes) {
    return new ComplianceExportJob(
        request.exportId().trim(),
        request.tenantId().trim(),
        FAILED_BLOCKED,
        request.requestedBy().trim(),
        null,
        request.templateVersionRef().normalized(),
        request.redactionProfileRef() == null ? null : request.redactionProfileRef().normalized(),
        request.subjectFilter().normalized(),
        request.deliveryPolicyRef() == null ? null : request.deliveryPolicyRef().normalized(),
        null,
        0,
        null,
        request.requestedAt(),
        request.requestedAt(),
        request.expiresAt(),
        request.artifactTypes(),
        request.sourceSnapshotRefs(),
        request.sensitiveExport(),
        request.idempotencyKey().trim(),
        request.correlationId().trim(),
        auditRef(request.tenantId(), request.exportId(), String.join(",", reasonCodes)),
        List.of(FAILED_EVENT_TYPE),
        reasonCodes);
  }

  private static ComplianceExportJob withStatus(
      ComplianceExportJob job,
      String status,
      String approvedBy,
      ExportManifest manifest,
      List<String> eventTypes,
      List<String> reasonCodes) {
    return new ComplianceExportJob(
        job.exportId(),
        job.tenantId(),
        status,
        job.requestedBy(),
        approvedBy,
        job.templateVersionRef(),
        job.redactionProfileRef(),
        job.subjectFilter(),
        job.deliveryPolicyRef(),
        manifest == null ? job.manifestHash() : manifest.manifestHash(),
        manifest == null ? job.artifactCount() : manifest.artifacts().size(),
        manifest == null ? job.manifest() : manifest,
        job.requestedAt(),
        job.updatedAt(),
        job.expiresAt(),
        job.artifactTypes(),
        job.sourceSnapshotRefs(),
        job.sensitiveExport(),
        job.idempotencyKey(),
        job.correlationId(),
        job.auditRef(),
        eventTypes,
        reasonCodes);
  }

  private static ExportManifest manifest(
      ComplianceExportJob job, List<ExportArtifactRef> artifacts, Instant completedAt) {
    String artifactMaterial = artifacts.stream().map(ExportArtifactRef::hashMaterial).reduce("", (a, b) -> a + b);
    String manifestHash =
        "sha256:"
            + sha256(
                String.join(
                    "|",
                    "schema:compliance-export-manifest:v1",
                    job.tenantId(),
                    job.exportId(),
                    job.templateVersionRef().hashMaterial(),
                    job.redactionProfileRef() == null ? "" : job.redactionProfileRef().hashMaterial(),
                    job.deliveryPolicyRef() == null ? "" : job.deliveryPolicyRef().hashMaterial(),
                    job.subjectFilter().hashMaterial(),
                    completedAt.toString(),
                    artifactMaterial));
    return new ExportManifest(
        job.exportId(),
        job.tenantId(),
        job.templateVersionRef(),
        job.redactionProfileRef(),
        artifacts,
        manifestHash,
        "chain-of-custody:" + manifestHash,
        completedAt,
        job.correlationId());
  }

  private static List<String> artifactDefects(ComplianceExportJob job, List<ExportArtifactRef> artifacts) {
    List<String> defects = new ArrayList<>();
    if (artifacts.isEmpty()) {
      defects.add("SOURCE_SNAPSHOT_NOT_FOUND");
    }
    for (ExportArtifactRef artifact : artifacts) {
      requireNonBlank(artifact.artifactType(), "artifactType", defects);
      requireNonBlank(artifact.sourceRef(), "sourceRef", defects);
      requireNonBlank(artifact.fileRef(), "fileRef", defects);
      requireNonBlank(artifact.contentType(), "contentType", defects);
      requireNonBlank(artifact.payloadHash(), "payloadHash", defects);
      if (job.sensitiveExport() && !artifact.redactionApplied()) {
        defects.add("REDACTION_PROFILE_REQUIRED:" + artifact.sequence());
      }
    }
    return defects;
  }

  private static void validateCreateRequest(CreateComplianceExportRequest request) {
    List<String> details = new ArrayList<>();
    if (request == null) {
      details.add("request");
    } else {
      requireNonBlank(request.tenantId(), "tenantId", details);
      requireNonBlank(request.exportId(), "exportId", details);
      requireNonBlank(request.requestedBy(), "requestedBy", details);
      requireNonBlank(request.idempotencyKey(), "idempotencyKey", details);
      requireNonBlank(request.correlationId(), "correlationId", details);
      if (request.requestedAt() == null) {
        details.add("requestedAt must be provided");
      }
      if (request.subjectFilter() == null || request.subjectFilter().missingRequiredFields()) {
        details.add("subjectFilter must include subjectType, subjectId, periodStart, and periodEnd");
      }
      if (request.templateVersionRef() == null || request.templateVersionRef().missingRequiredFields()) {
        details.add("templateVersionRef must include templateId and version");
      }
      if (request.artifactTypes() == null || request.artifactTypes().isEmpty()) {
        details.add("artifactTypes must include at least one type");
      }
      if (request.sourceSnapshotRefs() == null || request.sourceSnapshotRefs().isEmpty()) {
        details.add("sourceSnapshotRefs must include immutable source refs");
      }
    }
    if (!details.isEmpty()) {
      throw validation("Compliance export request validation failed.", details);
    }
  }

  private static void validateJob(ComplianceExportJob job) {
    if (job == null) {
      throw validation("Compliance export validation failed.", List.of("job"));
    }
  }

  private static void validateApprovalCommand(ApprovalCommand command) {
    List<String> details = new ArrayList<>();
    if (command == null) {
      details.add("command");
    } else {
      requireNonBlank(command.actorId(), "actorId", details);
      requireNonBlank(command.correlationId(), "correlationId", details);
      if (command.approvedAt() == null) {
        details.add("approvedAt must be provided");
      }
    }
    if (!details.isEmpty()) {
      throw validation("Compliance export approval validation failed.", details);
    }
  }

  private static void validateRunCommand(RunComplianceExport command) {
    List<String> details = new ArrayList<>();
    if (command == null) {
      details.add("command");
    } else {
      requireNonBlank(command.actorId(), "actorId", details);
      requireNonBlank(command.correlationId(), "correlationId", details);
      if (command.completedAt() == null) {
        details.add("completedAt must be provided");
      }
      if (command.artifacts() == null) {
        details.add("artifacts must be provided");
      }
    }
    if (!details.isEmpty()) {
      throw validation("Compliance export run validation failed.", details);
    }
  }

  private static void validateDownloadCommand(DownloadCommand command) {
    List<String> details = new ArrayList<>();
    if (command == null) {
      details.add("command");
    } else {
      requireNonBlank(command.actorId(), "actorId", details);
      requireNonBlank(command.purpose(), "purpose", details);
      requireNonBlank(command.correlationId(), "correlationId", details);
    }
    if (!details.isEmpty()) {
      throw validation("Compliance export download validation failed.", details);
    }
  }

  private static List<ExportArtifactRef> sortedArtifacts(List<ExportArtifactRef> artifacts) {
    return artifacts == null
        ? List.of()
        : artifacts.stream().filter(Objects::nonNull).sorted(Comparator.comparingInt(ExportArtifactRef::sequence)).toList();
  }

  private static boolean sameActor(String left, String right) {
    return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
  }

  private static void requireNonBlank(String value, String field, List<String> details) {
    if (value == null || value.trim().isEmpty()) {
      details.add(field + " must be a non-empty string");
    }
  }

  private static String auditRef(String tenantId, String exportId, String material) {
    return "compliance-export:" + tenantId.trim() + ":" + exportId.trim() + ":sha256:" + sha256(material);
  }

  private static ComplianceShellValidationError validation(String message, List<String> details) {
    return new ComplianceShellValidationError(message, details);
  }

  private static String sha256(String material) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest((material == null ? "" : material).getBytes(StandardCharsets.UTF_8));
      StringBuilder encoded = new StringBuilder();
      for (byte value : hash) {
        encoded.append(String.format(Locale.ROOT, "%02x", value));
      }
      return encoded.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is required for compliance exports", exception);
    }
  }

  public record CreateComplianceExportRequest(
      String tenantId,
      String exportId,
      String requestedBy,
      ExportSubjectFilter subjectFilter,
      ExportTemplateVersionRef templateVersionRef,
      RedactionProfileRef redactionProfileRef,
      DeliveryPolicyRef deliveryPolicyRef,
      List<String> artifactTypes,
      List<String> sourceSnapshotRefs,
      boolean sensitiveExport,
      boolean approvalRequired,
      String idempotencyKey,
      String correlationId,
      Instant requestedAt,
      Instant expiresAt) {
    public CreateComplianceExportRequest {
      artifactTypes = artifactTypes == null ? List.of() : List.copyOf(artifactTypes);
      sourceSnapshotRefs = sourceSnapshotRefs == null ? List.of() : List.copyOf(sourceSnapshotRefs);
    }
  }

  public record ExportSubjectFilter(
      String subjectType, String subjectId, LocalDate periodStart, LocalDate periodEnd) {
    boolean missingRequiredFields() {
      return subjectType == null
          || subjectType.isBlank()
          || subjectId == null
          || subjectId.isBlank()
          || periodStart == null
          || periodEnd == null;
    }

    ExportSubjectFilter normalized() {
      return new ExportSubjectFilter(subjectType.trim(), subjectId.trim(), periodStart, periodEnd);
    }

    String hashMaterial() {
      return String.join("|", subjectType.trim(), subjectId.trim(), periodStart.toString(), periodEnd.toString());
    }
  }

  public record ExportTemplateVersionRef(String templateId, String version, boolean approved, String approvalRef) {
    boolean missingRequiredFields() {
      return templateId == null || templateId.isBlank() || version == null || version.isBlank();
    }

    ExportTemplateVersionRef normalized() {
      return new ExportTemplateVersionRef(
          templateId.trim(), version.trim(), approved, approvalRef == null ? "" : approvalRef.trim());
    }

    String hashMaterial() {
      return templateId.trim() + ":" + version.trim() + ":" + approved + ":" + (approvalRef == null ? "" : approvalRef.trim());
    }
  }

  public record RedactionProfileRef(String profileId, String version, boolean approved) {
    RedactionProfileRef normalized() {
      return new RedactionProfileRef(profileId == null ? "" : profileId.trim(), version == null ? "" : version.trim(), approved);
    }

    String hashMaterial() {
      return (profileId == null ? "" : profileId.trim()) + ":" + (version == null ? "" : version.trim()) + ":" + approved;
    }
  }

  public record DeliveryPolicyRef(String policyId, String version, boolean approved, boolean localReferenceOnly) {
    DeliveryPolicyRef normalized() {
      return new DeliveryPolicyRef(
          policyId == null ? "" : policyId.trim(), version == null ? "" : version.trim(), approved, localReferenceOnly);
    }

    String hashMaterial() {
      return (policyId == null ? "" : policyId.trim())
          + ":"
          + (version == null ? "" : version.trim())
          + ":"
          + approved
          + ":"
          + localReferenceOnly;
    }
  }

  public record ApprovalCommand(String actorId, Instant approvedAt, String correlationId) {}

  public record RunComplianceExport(
      String actorId, List<ExportArtifactRef> artifacts, Instant completedAt, String correlationId) {
    public RunComplianceExport {
      artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
  }

  public record DownloadCommand(String actorId, String purpose, String correlationId) {}

  public record ExpireCommand(String actorId, Instant expiredAt, String correlationId) {}

  public record ExportArtifactRef(
      int sequence,
      String artifactType,
      String sourceRef,
      String fileRef,
      String contentType,
      String payloadHash,
      boolean redactionApplied) {
    String hashMaterial() {
      return String.join(
          "|",
          String.valueOf(sequence),
          artifactType == null ? "" : artifactType.trim(),
          sourceRef == null ? "" : sourceRef.trim(),
          fileRef == null ? "" : fileRef.trim(),
          contentType == null ? "" : contentType.trim(),
          payloadHash == null ? "" : payloadHash.trim(),
          String.valueOf(redactionApplied));
    }
  }

  public record ExportManifest(
      String exportId,
      String tenantId,
      ExportTemplateVersionRef templateVersionRef,
      RedactionProfileRef redactionProfileRef,
      List<ExportArtifactRef> artifacts,
      String manifestHash,
      String chainOfCustodyHash,
      Instant completedAt,
      String correlationId) {
    public ExportManifest {
      artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
  }

  public record ComplianceExportJob(
      String exportId,
      String tenantId,
      String status,
      String requestedBy,
      String approvedBy,
      ExportTemplateVersionRef templateVersionRef,
      RedactionProfileRef redactionProfileRef,
      ExportSubjectFilter subjectFilter,
      DeliveryPolicyRef deliveryPolicyRef,
      String manifestHash,
      int artifactCount,
      ExportManifest manifest,
      Instant requestedAt,
      Instant updatedAt,
      Instant expiresAt,
      List<String> artifactTypes,
      List<String> sourceSnapshotRefs,
      boolean sensitiveExport,
      String idempotencyKey,
      String correlationId,
      String auditRef,
      List<String> outboxEventTypes,
      List<String> reasonCodes) {
    public ComplianceExportJob {
      artifactTypes = artifactTypes == null ? List.of() : List.copyOf(artifactTypes);
      sourceSnapshotRefs = sourceSnapshotRefs == null ? List.of() : List.copyOf(sourceSnapshotRefs);
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
      reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
  }
}
