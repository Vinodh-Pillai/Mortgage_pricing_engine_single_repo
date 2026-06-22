package com.wcpe.mladvisory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ModelRegistryService {
  public static final String REGISTER_ENDPOINT = "POST /api/v1/tenants/{tenantId}/ml-advisory/model-versions";
  public static final String SUBMIT_REVIEW_ENDPOINT =
      "POST /api/v1/tenants/{tenantId}/ml-advisory/model-versions/{id}:submit-review";
  public static final String APPROVE_ENDPOINT = "POST /api/v1/tenants/{tenantId}/ml-advisory/model-versions/{id}:approve";
  public static final String SUSPEND_ENDPOINT = "POST /api/v1/tenants/{tenantId}/ml-advisory/model-versions/{id}:suspend";
  public static final String LIST_ENDPOINT =
      "GET /api/v1/tenants/{tenantId}/ml-advisory/model-versions?status=&advisoryType=";
  public static final String REGISTERED_EVENT = "MlModelVersionRegistered.v1";
  public static final String APPROVAL_CHANGED_EVENT = "MlModelVersionApprovalChanged.v1";
  public static final String SUSPENDED_EVENT = "MlModelVersionSuspended.v1";
  public static final String MODEL_RISK_APPROVER_ROLE = "ML_MODEL_RISK_APPROVER";
  public static final String COMPLIANCE_APPROVER_ROLE = "ML_COMPLIANCE_APPROVER";

  private final Clock clock;
  private final ModelVersionRepository repository;
  private final ModelLifecyclePolicy lifecyclePolicy = new ModelLifecyclePolicy();
  private final ModelApprovalPolicy approvalPolicy = new ModelApprovalPolicy();

  public ModelRegistryService() {
    throw failClosedMissingPersistence();
  }

  public ModelRegistryService(Clock clock) {
    throw failClosedMissingPersistence();
  }

  ModelRegistryService(Clock clock, ModelVersionRepository repository) {
    this.clock = clock;
    this.repository = repository;
  }

  public MlAdvisoryResult<ModelVersionResponse> register(RegisterModelVersionCommand command) {
    MlAdvisoryResult<RegisterModelVersionCommand> validation = validateRegister(command);
    if (!validation.valid()) {
      return MlAdvisoryResult.failure(validation.errorCode().orElseThrow());
    }
    String idempotencyKey = command.tenantId() + ":model-version:" + command.idempotencyKey();
    String requestHash = hash(canonicalRegister(command));
    Optional<ModelVersionIdempotencyRecord> existingIdempotency = repository.findIdempotency(idempotencyKey);
    if (existingIdempotency.isPresent()) {
      ModelVersionIdempotencyRecord record = existingIdempotency.orElseThrow();
      if (!record.requestHash().equals(requestHash)) {
        return MlAdvisoryResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return MlAdvisoryResult.success(record.response());
    }
    if (repository.existsByTenantModelAndSemanticVersion(command.tenantId(), command.modelName(), command.semanticVersion())) {
      return MlAdvisoryResult.failure("VERSION_CONFLICT");
    }
    Instant now = clock.instant();
    String modelVersionId = deterministicId(command.tenantId(), command.modelName(), command.semanticVersion());
    ModelVersion version =
        new ModelVersion(
            modelVersionId,
            command.tenantId(),
            command.modelName(),
            command.semanticVersion(),
            command.advisoryTypes(),
            command.allowedUse(),
            ModelStatus.DRAFT,
            command.artifactUri(),
            command.artifactChecksum(),
            command.featureSchemaVersion(),
            command.actorId(),
            now,
            "",
            null,
            "",
            command.evidence(),
            command.lineageRefs(),
            1);
    repository.save(version);
    ModelVersionResponse response = recordTransition(version, "none", ModelStatus.DRAFT, command.actorId(), REGISTERED_EVENT, command.idempotencyKey(), "registered", command.correlationId());
    repository.saveIdempotency(new ModelVersionIdempotencyRecord(idempotencyKey, requestHash, response));
    return MlAdvisoryResult.success(response);
  }

  public MlAdvisoryResult<ModelVersionResponse> submitReview(String tenantId, String modelVersionId, String actorId, String correlationId) {
    Optional<ModelVersion> existing = repository.find(tenantId, modelVersionId);
    if (existing.isEmpty()) {
      return MlAdvisoryResult.failure("NOT_FOUND");
    }
    ModelVersion current = existing.orElseThrow();
    if (!lifecyclePolicy.canSubmitReview(current.status())) {
      return MlAdvisoryResult.failure("ML_MODEL_INVALID_TRANSITION");
    }
    ModelVersion updated = current.withStatus(ModelStatus.IN_REVIEW, "", null, current.version() + 1);
    repository.save(updated);
    return MlAdvisoryResult.success(recordTransition(updated, current.status().name(), ModelStatus.IN_REVIEW, actorId, APPROVAL_CHANGED_EVENT, "", "submit-review", correlationId));
  }

  public MlAdvisoryResult<ModelVersionResponse> approve(ApproveModelVersionCommand command) {
    Optional<ModelVersion> existing = repository.find(command.tenantId(), command.modelVersionId());
    if (existing.isEmpty()) {
      return MlAdvisoryResult.failure("NOT_FOUND");
    }
    ModelVersion current = existing.orElseThrow();
    if (!lifecyclePolicy.canApprove(current.status(), command.targetStatus())) {
      return MlAdvisoryResult.failure("ML_MODEL_INVALID_TRANSITION");
    }
    MlAdvisoryResult<ModelVersion> policy = approvalPolicy.validate(current, command);
    if (!policy.valid()) {
      return MlAdvisoryResult.failure(policy.errorCode().orElseThrow());
    }
    ModelVersion updated = current.withStatus(command.targetStatus(), command.actorId(), clock.instant(), current.version() + 1);
    repository.save(updated);
    return MlAdvisoryResult.success(recordTransition(updated, current.status().name(), command.targetStatus(), command.actorId(), APPROVAL_CHANGED_EVENT, "", command.governanceTicket(), command.correlationId()));
  }

  public MlAdvisoryResult<ModelVersionResponse> suspend(SuspendModelVersionCommand command) {
    Optional<ModelVersion> existing = repository.find(command.tenantId(), command.modelVersionId());
    if (existing.isEmpty()) {
      return MlAdvisoryResult.failure("NOT_FOUND");
    }
    ModelVersion current = existing.orElseThrow();
    if (!lifecyclePolicy.canSuspend(current.status())) {
      return MlAdvisoryResult.failure("ML_MODEL_INVALID_TRANSITION");
    }
    ModelVersion updated = current.withStatus(ModelStatus.SUSPENDED, "", null, current.version() + 1);
    repository.save(updated);
    return MlAdvisoryResult.success(recordTransition(updated, current.status().name(), ModelStatus.SUSPENDED, command.actorId(), SUSPENDED_EVENT, "", command.governanceTicket(), command.correlationId()));
  }

  public List<ModelVersion> list(String tenantId, ModelStatus status, AdvisoryType advisoryType) {
    if (!isUuid(tenantId)) {
      return List.of();
    }
    return repository.list(tenantId, status, advisoryType);
  }

  public boolean discoverable(String tenantId, String modelVersionId, String expectedFeatureSchemaVersion) {
    Optional<ModelVersion> existing = repository.find(tenantId, modelVersionId);
    return existing.isPresent()
        && lifecyclePolicy.discoverable(existing.orElseThrow().status())
        && new ModelCompatibilityChecker().validateFeatureSchema(existing.orElseThrow(), expectedFeatureSchemaVersion).valid();
  }

  public List<MlAdvisoryOutboxEvent> outboxEvents() {
    return repository.outboxEvents();
  }

  public List<MlAdvisoryAuditRecord> auditRecords() {
    return repository.auditRecords();
  }

  private MlAdvisoryResult<RegisterModelVersionCommand> validateRegister(RegisterModelVersionCommand command) {
    if (command == null
        || !isUuid(command.tenantId())
        || isBlank(command.idempotencyKey())
        || isBlank(command.actorId())
        || isBlank(command.modelName())
        || isBlank(command.semanticVersion())
        || command.advisoryTypes().isEmpty()
        || command.allowedUse() != AllowedUse.ADVISORY_ONLY
        || isBlank(command.artifactUri())
        || isBlank(command.featureSchemaVersion())
        || isBlank(command.correlationId())) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (isBlank(command.artifactChecksum())) {
      return MlAdvisoryResult.failure("ML_MODEL_CHECKSUM_REQUIRED");
    }
    return MlAdvisoryResult.success(command);
  }

  private ModelVersionResponse recordTransition(
      ModelVersion version,
      String beforeStatus,
      ModelStatus afterStatus,
      String actorId,
      String eventType,
      String idempotencyKey,
      String reason,
      String correlationId) {
    Instant now = clock.instant();
    String eventId = deterministicId(version.modelVersionId(), correlationId, eventType, afterStatus.name());
    String auditId = deterministicId(version.modelVersionId(), actorId, afterStatus.name(), "audit");
    String historyId = deterministicId(version.modelVersionId(), beforeStatus, afterStatus.name(), correlationId, "history");
    String cacheInvalidationRef = afterStatus == ModelStatus.SUSPENDED || afterStatus == ModelStatus.RETIRED ? deterministicId(version.modelVersionId(), "cache-invalidation", correlationId) : "";
    MlAdvisoryOutboxEvent event =
        new MlAdvisoryOutboxEvent(
            eventId,
            eventType,
            version.tenantId(),
            version.modelVersionId(),
            actorId,
            correlationId,
            idempotencyKey,
            now,
            Map.of(
                "modelVersionId", version.modelVersionId(),
                "status", afterStatus.name(),
                "allowedUse", version.allowedUse().name(),
                "actor", actorId,
                "reason", reason == null ? "" : reason,
                "governanceTicket", reason == null ? "" : reason));
    MlAdvisoryAuditRecord audit =
        new MlAdvisoryAuditRecord(
            auditId,
            version.tenantId(),
            actorId,
            "ML_MODEL_VERSION_GOVERNANCE_COMPLETED",
            beforeStatus,
            afterStatus.name() + ";modelVersion=" + version.modelVersionId(),
            correlationId,
            now);
    repository.saveStatusHistory(
        new ModelVersionStatusHistory(
            historyId,
            version.modelVersionId(),
            version.tenantId(),
            beforeStatus,
            afterStatus.name(),
            actorId,
            reason == null ? "" : reason,
            reason == null ? "" : reason,
            correlationId,
            now));
    repository.saveOutboxEvent(event);
    repository.saveAuditRecord(audit);
    return new ModelVersionResponse(
        version.modelVersionId(),
        version.tenantId(),
        version.modelName(),
        version.semanticVersion(),
        afterStatus,
        version.allowedUse(),
        version.version(),
        eventId,
        auditId,
        cacheInvalidationRef,
        correlationId);
  }

  private String canonicalRegister(RegisterModelVersionCommand command) {
    return String.join(
        "|",
        command.tenantId(),
        command.modelName(),
        command.semanticVersion(),
        command.allowedUse().name(),
        command.artifactUri(),
        command.artifactChecksum(),
        command.featureSchemaVersion(),
        command.advisoryTypes().stream().map(Enum::name).sorted().reduce("", (left, right) -> left + "," + right));
  }

  private boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String deterministicId(String... parts) {
    return hash(String.join("|", parts)).substring(0, 32);
  }

  private String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private static IllegalStateException failClosedMissingPersistence() {
    return new IllegalStateException(
        "Model version governance persistence requires an explicit JDBC/PostgreSQL-backed ModelVersionRepository; in-memory production fallback is disabled.");
  }
}
