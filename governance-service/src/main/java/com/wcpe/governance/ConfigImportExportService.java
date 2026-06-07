package com.wcpe.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConfigImportExportService {
  public static final String PROFILES_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-import-export/profiles";
  public static final String IMPORT_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-import-jobs";
  public static final String IMPORT_REPORT_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-import-jobs/{jobId}";
  public static final String CREATE_DRAFTS_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-import-jobs/{jobId}/create-drafts";
  public static final String EXPORT_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-export-jobs";
  public static final String DOWNLOAD_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-export-jobs/{jobId}/download";
  public static final String IMPORT_PERMISSION = "admin.config.import";
  public static final String DRAFT_PERMISSION = "admin.config.write";
  public static final String EXPORT_PERMISSION = "admin.config.export";
  public static final String READ_PERMISSION = "admin.config.read";
  public static final String IMPORT_STARTED_EVENT = "ConfigImportJobStarted.v1";
  public static final String IMPORT_COMPLETED_EVENT = "ConfigImportJobCompleted.v1";
  public static final String IMPORT_DRAFTS_CREATED_EVENT = "ConfigImportDraftsCreated.v1";
  public static final String EXPORT_CREATED_EVENT = "ConfigExportPackageCreated.v1";
  public static final String EXPORT_DOWNLOADED_EVENT = "ConfigExportDownloaded.v1";
  public static final String IMPORT_AUDIT_ACTION = "CONFIG_IMPORT_EXPORT_IMPORT_COMPLETED";
  public static final String EXPORT_AUDIT_ACTION = "CONFIG_IMPORT_EXPORT_EXPORT_COMPLETED";
  public static final String DOWNLOAD_AUDIT_ACTION = "CONFIG_IMPORT_EXPORT_DOWNLOAD_COMPLETED";

  private final Clock clock;
  private final Map<String, ConfigImportJob> importJobs = new HashMap<>();
  private final Map<String, ConfigExportJob> exportJobs = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public ConfigImportExportService() {
    this(Clock.systemUTC());
  }

  public ConfigImportExportService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<ConfigImportJob> importBundle(ConfigImportCommand command) {
    GovernanceValidationResult<ConfigImportCommand> validation = validateImportCommand(command);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    String requestHash = hash(canonicalImportCommand(command));
    String idempotencyLookupKey = command.tenantId() + ":import:" + command.idempotencyKey();
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyLookupKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        return GovernanceValidationResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return GovernanceValidationResult.success(existing.importJob());
    }

    Instant now = clock.instant();
    String fileHash = hash(command.sourceContent());
    String jobId = deterministicId(command.tenantId(), command.profile().profileId(), command.idempotencyKey(), fileHash);
    List<ConfigImportFinding> findings = importFindings(command, jobId);
    boolean blocking = findings.stream().anyMatch(ConfigImportFinding::blocking);
    List<ImportedDraftRef> drafts =
        command.mode() == ConfigImportMode.CREATE_DRAFTS && !blocking ? importedDrafts(command, jobId, fileHash) : List.of();
    String status = importStatus(command.mode(), blocking);
    String resultHash = hash(jobId + "|" + status + "|" + fileHash + "|" + canonicalFindings(findings) + "|" + canonicalDrafts(drafts));
    ConfigImportJob job =
        new ConfigImportJob(
            command.tenantId(),
            jobId,
            command.profile().profileId(),
            command.profile().profileVersion(),
            status,
            command.mode(),
            command.artifactType(),
            command.sourceSystem(),
            command.fileName(),
            command.fileFormat(),
            fileHash,
            command.idempotencyKey(),
            resultHash,
            command.actorId(),
            now,
            now,
            command.correlationId(),
            findings,
            drafts,
            jobId);

    importJobs.put(key(command.tenantId(), jobId), job);
    idempotencyEntries.put(idempotencyLookupKey, new IdempotencyEntry(requestHash, job, null));
    recordImportEvents(command, job, now);
    return GovernanceValidationResult.success(job);
  }

  public GovernanceValidationResult<ConfigExportJob> exportPackage(ConfigExportCommand command) {
    GovernanceValidationResult<ConfigExportCommand> validation = validateExportCommand(command);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    String requestHash = hash(canonicalExportCommand(command));
    String idempotencyLookupKey = command.tenantId() + ":export:" + command.idempotencyKey();
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyLookupKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        return GovernanceValidationResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return GovernanceValidationResult.success(existing.exportJob());
    }

    Instant now = clock.instant();
    String jobId = deterministicId(command.tenantId(), command.profile().profileId(), command.idempotencyKey(), command.reason());
    List<PackageArtifactRef> artifactRefs = packageArtifactRefs(command);
    String manifestHash = hash(canonicalPackageArtifactRefs(artifactRefs));
    String packageHash = hash(jobId + "|" + manifestHash + "|" + command.profile().profileVersion() + "|" + command.redactionLevel());
    Instant expiresAt = now.plus(command.profile().packageTtl());
    ExportManifest manifest =
        new ExportManifest(
            deterministicId(jobId, "manifest"),
            command.tenantId(),
            jobId,
            command.profile().profileId(),
            command.profile().profileVersion(),
            command.redactionLevel(),
            artifactRefs,
            manifestHash,
            packageHash,
            now,
            expiresAt);
    ConfigExportJob job =
        new ConfigExportJob(
            command.tenantId(),
            jobId,
            command.profile().profileId(),
            command.profile().profileVersion(),
            "PACKAGE_CREATED",
            command.redactionLevel(),
            packageHash,
            manifest,
            command.actorId(),
            now,
            expiresAt,
            null,
            command.correlationId(),
            jobId);

    exportJobs.put(key(command.tenantId(), jobId), job);
    idempotencyEntries.put(idempotencyLookupKey, new IdempotencyEntry(requestHash, null, job));
    recordExportEvent(command, job, now, EXPORT_CREATED_EVENT, EXPORT_AUDIT_ACTION, manifestHash, packageHash);
    return GovernanceValidationResult.success(job);
  }

  public GovernanceValidationResult<ConfigExportJob> downloadPackage(String tenantId, String jobId, String actorId, String correlationId) {
    ConfigExportJob current = exportJobs.get(key(tenantId, jobId));
    if (current == null) {
      return GovernanceValidationResult.failure("NOT_FOUND");
    }
    if (isBlank(actorId) || isBlank(correlationId)) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: actor and correlationId are required");
    }
    Instant now = clock.instant();
    if (current.expiresAt().isBefore(now)) {
      return GovernanceValidationResult.failure("PACKAGE_EXPIRED");
    }
    ConfigExportJob downloaded =
        new ConfigExportJob(
            current.tenantId(),
            current.exportJobId(),
            current.profileId(),
            current.profileVersion(),
            "DOWNLOADED",
            current.redactionLevel(),
            current.packageHash(),
            current.manifest(),
            current.actorId(),
            current.createdAt(),
            current.expiresAt(),
            now,
            correlationId,
            current.replayRef());
    exportJobs.put(key(tenantId, jobId), downloaded);
    ConfigExportCommand eventCommand =
        new ConfigExportCommand(
            tenantId,
            deterministicId(jobId, actorId, "download"),
            actorId,
            new ConfigExportProfile(current.profileId(), current.profileVersion(), List.of(), List.of(), Duration.ofDays(1)),
            List.of(),
            current.redactionLevel(),
            "download",
            correlationId);
    recordExportEvent(eventCommand, downloaded, now, EXPORT_DOWNLOADED_EVENT, DOWNLOAD_AUDIT_ACTION, current.packageHash(), current.packageHash());
    return GovernanceValidationResult.success(downloaded);
  }

  public GovernanceValidationResult<ConfigImportJob> importJob(String tenantId, String jobId) {
    ConfigImportJob job = importJobs.get(key(tenantId, jobId));
    return job == null ? GovernanceValidationResult.failure("NOT_FOUND") : GovernanceValidationResult.success(job);
  }

  public GovernanceValidationResult<ConfigExportJob> exportJob(String tenantId, String jobId) {
    ConfigExportJob job = exportJobs.get(key(tenantId, jobId));
    return job == null ? GovernanceValidationResult.failure("NOT_FOUND") : GovernanceValidationResult.success(job);
  }

  public List<ConfigImportJob> importJobsForTenant(String tenantId) {
    return importJobs.values().stream()
        .filter(job -> job.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(ConfigImportJob::completedAt).thenComparing(ConfigImportJob::importJobId))
        .toList();
  }

  public List<ConfigExportJob> exportJobsForTenant(String tenantId) {
    return exportJobs.values().stream()
        .filter(job -> job.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(ConfigExportJob::createdAt).thenComparing(ConfigExportJob::exportJobId))
        .toList();
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private GovernanceValidationResult<ConfigImportCommand> validateImportCommand(ConfigImportCommand command) {
    if (command == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: command is required");
    }
    if (!isUuid(command.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (isBlank(command.idempotencyKey()) || isBlank(command.actorId()) || isBlank(command.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: idempotency key, actor, and correlationId are required");
    }
    if (command.mode() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: import mode is required");
    }
    if (command.profile() == null || isBlank(command.profile().profileId()) || isBlank(command.profile().profileVersion())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: import profile is required");
    }
    if (!command.profile().supportedFormats().contains(command.fileFormat())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: unsupported file format");
    }
    if (!command.profile().allowedArtifactTypes().contains(command.artifactType())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: unsupported artifact type");
    }
    if (isBlank(command.fileName()) || command.fileName().contains("..") || command.fileName().contains("/") || command.fileName().contains("\\")) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: safe file name is required");
    }
    if (isBlank(command.sourceContent()) || command.payload() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: source content and payload are required");
    }
    return GovernanceValidationResult.success(command);
  }

  private GovernanceValidationResult<ConfigExportCommand> validateExportCommand(ConfigExportCommand command) {
    if (command == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: command is required");
    }
    if (!isUuid(command.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (isBlank(command.idempotencyKey()) || isBlank(command.actorId()) || isBlank(command.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: idempotency key, actor, and correlationId are required");
    }
    if (command.profile() == null || isBlank(command.profile().profileId()) || isBlank(command.profile().profileVersion())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: export profile is required");
    }
    if (command.profile().packageTtl() == null || command.profile().packageTtl().isNegative() || command.profile().packageTtl().isZero()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: package expiry policy is required");
    }
    if (command.artifactSnapshots() == null || command.artifactSnapshots().isEmpty()) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: at least one artifact snapshot is required");
    }
    for (ConfigExportArtifactSnapshot snapshot : command.artifactSnapshots()) {
      if (snapshot == null
          || isBlank(snapshot.artifactRef())
          || isBlank(snapshot.artifactType())
          || isBlank(snapshot.versionId())
          || snapshot.payload() == null
          || snapshot.payload().isEmpty()) {
        return GovernanceValidationResult.failure("VALIDATION_FAILED: artifact snapshots must include ref, type, version, and payload");
      }
      if (!command.profile().allowedArtifactTypes().contains(snapshot.artifactType())) {
        return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: unsupported export artifact type");
      }
    }
    if (isBlank(command.redactionLevel()) || isBlank(command.reason())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: redaction level and export reason are required");
    }
    return GovernanceValidationResult.success(command);
  }

  private List<ConfigImportFinding> importFindings(ConfigImportCommand command, String jobId) {
    List<ConfigImportFinding> findings = new ArrayList<>();
    for (String requiredField : command.profile().requiredPayloadFields()) {
      if (!command.payload().containsKey(requiredField) || isBlank(command.payload().get(requiredField))) {
        findings.add(
            new ConfigImportFinding(
                deterministicId(jobId, requiredField, "missing"),
                jobId,
                "ERROR",
                command.fileName() + ":" + command.artifactType(),
                1,
                "$." + requiredField,
                "REQUIRED_FIELD_MISSING",
                "config.import.required-field.missing",
                "Supply the tenant-profile required field before draft creation.",
                true,
                findings.size() + 1));
      }
    }
    return findings.stream()
        .sorted(Comparator.comparing(ConfigImportFinding::blocking).reversed().thenComparing(ConfigImportFinding::code).thenComparing(ConfigImportFinding::fieldPath))
        .toList();
  }

  private List<ImportedDraftRef> importedDrafts(ConfigImportCommand command, String jobId, String fileHash) {
    String payloadHash = hash(canonicalMap(command.payload()));
    String artifactId = deterministicId(command.tenantId(), command.artifactType(), payloadHash);
    String versionId = deterministicId(artifactId, fileHash, command.profile().profileVersion(), "draft");
    return List.of(
        new ImportedDraftRef(
            artifactId,
            versionId,
            command.artifactType(),
            payloadHash,
            command.fileName() + "#row:1",
            Map.of("importJobId", jobId, "fileHash", fileHash, "profileVersion", command.profile().profileVersion())));
  }

  private String importStatus(ConfigImportMode mode, boolean blocking) {
    if (blocking) {
      return "VALIDATION_FAILED";
    }
    return mode == ConfigImportMode.DRY_RUN ? "DRY_RUN_PASSED" : "DRAFTS_CREATED";
  }

  private void recordImportEvents(ConfigImportCommand command, ConfigImportJob job, Instant now) {
    outboxEvents.add(
        event(command, deterministicId(job.importJobId(), "started", command.correlationId()), IMPORT_STARTED_EVENT, job.importJobId(), job.resultHash(), now));
    outboxEvents.add(
        event(command, deterministicId(job.importJobId(), "completed", command.correlationId()), IMPORT_COMPLETED_EVENT, job.importJobId(), job.resultHash(), now));
    if (!job.draftRefs().isEmpty()) {
      outboxEvents.add(
          event(command, deterministicId(job.importJobId(), "drafts", command.correlationId()), IMPORT_DRAFTS_CREATED_EVENT, job.importJobId(), job.resultHash(), now));
    }
    auditRecords.add(
        new ConfigApiAuditRecord(
            deterministicId(job.importJobId(), command.actorId(), "audit"),
            command.tenantId(),
            job.importJobId(),
            job.profileVersion(),
            command.actorId(),
            IMPORT_AUDIT_ACTION,
            job.fileHash(),
            job.resultHash(),
            command.correlationId(),
            now));
  }

  private ConfigApiOutboxEvent event(ConfigImportCommand command, String eventId, String eventType, String aggregateId, String resultHash, Instant now) {
    return new ConfigApiOutboxEvent(
        eventId,
        eventType,
        1,
        command.tenantId(),
        aggregateId,
        command.profile().profileVersion(),
        command.actorId(),
        command.correlationId(),
        command.idempotencyKey(),
        command.idempotencyKey(),
        now,
        Map.of(
            "jobId", aggregateId,
            "profileId", command.profile().profileId(),
            "statusHash", resultHash,
            "mode", command.mode().name(),
            "artifactType", command.artifactType()));
  }

  private List<PackageArtifactRef> packageArtifactRefs(ConfigExportCommand command) {
    return command.artifactSnapshots().stream()
        .sorted(Comparator.comparing(ConfigExportArtifactSnapshot::artifactRef).thenComparing(ConfigExportArtifactSnapshot::versionId))
        .map(
            snapshot -> {
              Map<String, String> redactedPayload = redact(snapshot.payload(), command.profile().redactedFields());
              return new PackageArtifactRef(
                  snapshot.artifactRef(),
                  snapshot.artifactType(),
                  snapshot.versionId(),
                  hash(canonicalMap(snapshot.payload())),
                  hash(canonicalMap(redactedPayload)),
                  snapshot.schemaRef(),
                  redactedPayload);
            })
        .toList();
  }

  private Map<String, String> redact(Map<String, String> payload, List<String> redactedFields) {
    Map<String, String> redacted = new HashMap<>();
    for (Map.Entry<String, String> entry : payload.entrySet()) {
      redacted.put(entry.getKey(), redactedFields.contains(entry.getKey()) ? "REDACTED_BY_EXPORT_PROFILE" : entry.getValue());
    }
    return Map.copyOf(redacted);
  }

  private void recordExportEvent(
      ConfigExportCommand command, ConfigExportJob job, Instant now, String eventType, String auditAction, String beforeHash, String afterHash) {
    outboxEvents.add(
        new ConfigApiOutboxEvent(
            deterministicId(job.exportJobId(), eventType, command.correlationId()),
            eventType,
            1,
            command.tenantId(),
            job.exportJobId(),
            job.profileVersion(),
            command.actorId(),
            command.correlationId(),
            command.idempotencyKey(),
            command.idempotencyKey(),
            now,
            Map.of(
                "jobId", job.exportJobId(),
                "profileId", job.profileId(),
                "status", job.status(),
                "manifestHash", job.manifest().manifestHash(),
                "packageHash", job.packageHash())));
    auditRecords.add(
        new ConfigApiAuditRecord(
            deterministicId(job.exportJobId(), command.actorId(), eventType, "audit"),
            command.tenantId(),
            job.exportJobId(),
            job.profileVersion(),
            command.actorId(),
            auditAction,
            beforeHash,
            afterHash,
            command.correlationId(),
            now));
  }

  private String canonicalImportCommand(ConfigImportCommand command) {
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.profile().profileId(),
        command.profile().profileVersion(),
        command.mode().name(),
        command.sourceSystem(),
        command.fileName(),
        command.fileFormat(),
        command.artifactType(),
        hash(command.sourceContent()),
        canonicalMap(command.payload()),
        command.correlationId());
  }

  private String canonicalExportCommand(ConfigExportCommand command) {
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.profile().profileId(),
        command.profile().profileVersion(),
        command.redactionLevel(),
        command.reason(),
        canonicalPackageArtifactRefs(packageArtifactRefs(command)),
        command.correlationId());
  }

  private String canonicalFindings(List<ConfigImportFinding> findings) {
    return findings.stream()
        .map(finding -> finding.sortOrder() + "|" + finding.severity() + "|" + finding.code() + "|" + finding.fieldPath() + "|" + finding.blocking())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("no-findings");
  }

  private String canonicalDrafts(List<ImportedDraftRef> drafts) {
    return drafts.stream()
        .map(draft -> draft.artifactId() + "|" + draft.versionId() + "|" + draft.artifactType() + "|" + draft.payloadHash())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("no-drafts");
  }

  private String canonicalPackageArtifactRefs(List<PackageArtifactRef> artifactRefs) {
    return artifactRefs.stream()
        .map(
            artifact ->
                artifact.artifactRef()
                    + "|"
                    + artifact.artifactType()
                    + "|"
                    + artifact.versionId()
                    + "|"
                    + artifact.payloadHash()
                    + "|"
                    + artifact.redactedPayloadHash()
                    + "|"
                    + artifact.schemaRef()
                    + "|"
                    + canonicalMap(artifact.redactedPayload()))
        .reduce((left, right) -> left + "\n" + right)
        .orElse("no-artifacts");
  }

  private String canonicalMap(Map<String, String> map) {
    return map.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String deterministicId(String... parts) {
    return UUID.nameUUIDFromBytes(String.join(":", parts).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private String key(String tenantId, String id) {
    return tenantId + ":" + id;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record IdempotencyEntry(String requestHash, ConfigImportJob importJob, ConfigExportJob exportJob) {}
}

enum ConfigImportMode {
  DRY_RUN,
  CREATE_DRAFTS
}

record ConfigImportProfile(
    String profileId,
    String profileVersion,
    List<String> allowedArtifactTypes,
    List<String> supportedFormats,
    List<String> requiredPayloadFields,
    Map<String, String> schemaRefs) {}

record ConfigImportCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    ConfigImportProfile profile,
    ConfigImportMode mode,
    String sourceSystem,
    String fileName,
    String fileFormat,
    String sourceContent,
    String artifactType,
    Map<String, String> payload,
    String correlationId) {}

record ConfigImportFinding(
    String findingId,
    String importJobId,
    String severity,
    String artifactRef,
    int rowNumber,
    String fieldPath,
    String code,
    String messageKey,
    String remediation,
    boolean blocking,
    int sortOrder) {}

record ImportedDraftRef(
    String artifactId,
    String versionId,
    String artifactType,
    String payloadHash,
    String sourcePath,
    Map<String, String> provenance) {}

record ConfigImportJob(
    String tenantId,
    String importJobId,
    String profileId,
    String profileVersion,
    String status,
    ConfigImportMode mode,
    String artifactType,
    String sourceSystem,
    String fileName,
    String fileFormat,
    String fileHash,
    String idempotencyKey,
    String resultHash,
    String actorId,
    Instant startedAt,
    Instant completedAt,
    String correlationId,
    List<ConfigImportFinding> findings,
    List<ImportedDraftRef> draftRefs,
    String replayRef) {}

record ConfigExportProfile(
    String profileId,
    String profileVersion,
    List<String> allowedArtifactTypes,
    List<String> redactedFields,
    Duration packageTtl) {}

record ConfigExportArtifactSnapshot(
    String artifactRef,
    String artifactType,
    String versionId,
    String schemaRef,
    Map<String, String> payload) {}

record ConfigExportCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    ConfigExportProfile profile,
    List<ConfigExportArtifactSnapshot> artifactSnapshots,
    String redactionLevel,
    String reason,
    String correlationId) {}

record PackageArtifactRef(
    String artifactRef,
    String artifactType,
    String versionId,
    String payloadHash,
    String redactedPayloadHash,
    String schemaRef,
    Map<String, String> redactedPayload) {}

record ExportManifest(
    String manifestId,
    String tenantId,
    String exportJobId,
    String profileId,
    String profileVersion,
    String redactionLevel,
    List<PackageArtifactRef> artifactRefs,
    String manifestHash,
    String packageHash,
    Instant createdAt,
    Instant expiresAt) {}

record ConfigExportJob(
    String tenantId,
    String exportJobId,
    String profileId,
    String profileVersion,
    String status,
    String redactionLevel,
    String packageHash,
    ExportManifest manifest,
    String actorId,
    Instant createdAt,
    Instant expiresAt,
    Instant downloadedAt,
    String correlationId,
    String replayRef) {}
