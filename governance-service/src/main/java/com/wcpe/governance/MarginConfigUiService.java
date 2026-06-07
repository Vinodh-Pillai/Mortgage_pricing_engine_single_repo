package com.wcpe.governance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MarginConfigUiService {
  public static final String METADATA_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/margins/metadata";
  public static final String LIST_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/margins";
  public static final String IMPACT_SIMULATION_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/margins/{versionId}/impact-simulations";
  public static final String WRITE_PERMISSION = "admin.config.write";
  public static final String SIMULATE_PERMISSION = "admin.config.simulate";
  public static final String APPROVE_PERMISSION = "admin.config.approve";
  public static final String PUBLISH_PERMISSION = "admin.config.publish";
  public static final String COMPLETED_EVENT_TYPE = "margin_config_ui.completed.v1";
  public static final String AUDIT_ACTION = "MARGIN_CONFIG_UI_COMPLETED";
  public static final String ARTIFACT_TYPE = "MARGIN_CONFIG";

  private final Clock clock;
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final Map<String, MarginConfigUiResult> versionsByTenantAndId = new HashMap<>();
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public MarginConfigUiService() {
    this(Clock.systemUTC());
  }

  public MarginConfigUiService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<MarginConfigUiResult> saveDraft(MarginConfigUiCommand command) {
    return apply(command, MarginLifecycleAction.SAVE_DRAFT);
  }

  public GovernanceValidationResult<MarginConfigUiResult> submit(MarginConfigUiCommand command) {
    return apply(command, MarginLifecycleAction.SUBMIT);
  }

  public GovernanceValidationResult<MarginConfigUiResult> approveAndPublish(MarginConfigUiCommand command) {
    return apply(command, MarginLifecycleAction.APPROVE_AND_PUBLISH);
  }

  public List<MarginConfigUiResult> versionsForTenant(String tenantId) {
    return versionsByTenantAndId.values().stream()
        .filter(version -> version.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(MarginConfigUiResult::updatedAt).thenComparing(MarginConfigUiResult::id))
        .toList();
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private GovernanceValidationResult<MarginConfigUiResult> apply(MarginConfigUiCommand command, MarginLifecycleAction action) {
    GovernanceValidationResult<MarginConfigUiCommand> validation = validate(command, action);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    String requestHash = hash(canonicalCommand(command, action));
    String idempotencyLookupKey = command.tenantId() + ":" + command.idempotencyKey();
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyLookupKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        return GovernanceValidationResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return GovernanceValidationResult.success(existing.result());
    }

    Instant now = clock.instant();
    List<MarginConfigRowEvidence> rowEvidence = command.rows().stream().map(this::rowEvidence).toList();
    String id = deterministicId(command.tenantId(), ARTIFACT_TYPE, command.metadata().schemaVersion(), canonicalRows(command.rows()));
    String versionId = deterministicId(id, requestHash, action.name());
    String replayRef = hash(canonicalCommand(command, action) + "|" + canonicalRowEvidence(rowEvidence));
    String auditRef = deterministicId(versionId, command.actorId(), "audit");
    String eventId = deterministicId(versionId, command.correlationId(), "event");
    String impactHash = command.impactSimulation() == null ? "" : hash(canonicalImpact(command.impactSimulation()));
    String status = switch (action) {
      case SAVE_DRAFT -> "DRAFT";
      case SUBMIT -> "SUBMITTED";
      case APPROVE_AND_PUBLISH -> "PUBLISHED";
    };

    MarginConfigUiResult result =
        new MarginConfigUiResult(
            command.tenantId(),
            id,
            versionId,
            status,
            1,
            Map.of(
                "artifactType", ARTIFACT_TYPE,
                "schemaVersion", command.metadata().schemaVersion(),
                "dimensionCount", Integer.toString(command.metadata().dimensions().size()),
                "rowCount", Integer.toString(command.rows().size()),
                "impactSimulationRef", command.impactSimulation() == null ? "not-requested" : command.impactSimulation().simulationId(),
                "impactResultHash", impactHash),
            List.of(),
            rowEvidence,
            auditRef,
            replayRef,
            command.correlationId(),
            now);

    String previousVersionHash = currentVersionHash(command.tenantId(), id);
    versionsByTenantAndId.put(command.tenantId() + ":" + id, result);
    outboxEvents.add(
        new ConfigApiOutboxEvent(
            eventId,
            COMPLETED_EVENT_TYPE,
            1,
            command.tenantId(),
            id,
            versionId,
            command.actorId(),
            command.correlationId(),
            command.correlationId(),
            command.idempotencyKey(),
            now,
            Map.of(
                "artifactType", ARTIFACT_TYPE,
                "status", status,
                "rowCount", Integer.toString(command.rows().size()),
                "replayRef", replayRef,
                "impactSimulationRef", command.impactSimulation() == null ? "not-requested" : command.impactSimulation().simulationId())));
    auditRecords.add(
        new ConfigApiAuditRecord(
            auditRef,
            command.tenantId(),
            id,
            versionId,
            command.actorId(),
            AUDIT_ACTION,
            "previousHash=" + previousVersionHash,
            "afterHash=" + replayRef,
            command.correlationId(),
            now));
    idempotencyEntries.put(idempotencyLookupKey, new IdempotencyEntry(requestHash, result));
    return GovernanceValidationResult.success(result);
  }

  private GovernanceValidationResult<MarginConfigUiCommand> validate(MarginConfigUiCommand command, MarginLifecycleAction action) {
    if (command == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: command is required");
    }
    if (!isUuid(command.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (isBlank(command.idempotencyKey())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: idempotency key is required");
    }
    if (isBlank(command.actorId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: actorId is required");
    }
    if (isBlank(command.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: correlationId is required");
    }
    if (!hasPermission(command.permissions(), permissionFor(action))) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (command.metadata() == null || isBlank(command.metadata().schemaVersion())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: margin metadata schema is required");
    }
    if (command.metadata().dimensions() == null || command.metadata().dimensions().isEmpty()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: margin dimensions must come from metadata");
    }
    if (command.metadata().reasonCodes() == null || command.metadata().reasonCodes().isEmpty()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: reason code catalog is required");
    }
    if (command.metadata().valuePolicy() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: margin value policy is required");
    }
    if (command.rows() == null || command.rows().isEmpty()) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: at least one margin row is required");
    }
    if ((action == MarginLifecycleAction.SUBMIT || action == MarginLifecycleAction.APPROVE_AND_PUBLISH) && command.impactSimulation() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: impact simulation evidence is required");
    }
    if (action == MarginLifecycleAction.APPROVE_AND_PUBLISH) {
      if (!hasPermission(command.permissions(), APPROVE_PERMISSION) || !hasPermission(command.permissions(), PUBLISH_PERMISSION)) {
        return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
      }
      if (isBlank(command.approverId())) {
        return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: approverId is required");
      }
      if (command.actorId().equals(command.approverId())) {
        return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: requester cannot approve their own margin config");
      }
    }

    Map<String, MarginDimensionMetadata> dimensions = new HashMap<>();
    for (MarginDimensionMetadata dimension : command.metadata().dimensions()) {
      if (dimension == null || isBlank(dimension.name())) {
        return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: dimension metadata must include a name");
      }
      dimensions.put(dimension.name(), dimension);
    }
    Set<String> reasonCodes = new HashSet<>(command.metadata().reasonCodes());
    Set<String> rowContextWindows = new HashSet<>();
    Set<Integer> priorities = new HashSet<>();
    for (MarginConfigRow row : command.rows()) {
      GovernanceValidationResult<Void> rowValidation = validateRow(row, dimensions, reasonCodes, command.metadata().valuePolicy());
      if (!rowValidation.valid()) {
        return GovernanceValidationResult.failure(rowValidation.error().orElseThrow());
      }
      String contextWindow = canonicalMap(row.context()) + "|" + row.effectiveStart() + "|" + row.effectiveEnd();
      if (!command.metadata().allowOverlappingBands() && !rowContextWindows.add(contextWindow)) {
        return GovernanceValidationResult.failure("VALIDATION_FAILED: overlapping margin bands require configured precedence");
      }
      if (!priorities.add(row.priority())) {
        return GovernanceValidationResult.failure("VALIDATION_FAILED: duplicate margin priority is not allowed");
      }
    }
    return GovernanceValidationResult.success(command);
  }

  private GovernanceValidationResult<Void> validateRow(
      MarginConfigRow row,
      Map<String, MarginDimensionMetadata> dimensions,
      Set<String> reasonCodes,
      MarginValuePolicy valuePolicy) {
    if (row == null || isBlank(row.rowId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: margin row id is required");
    }
    if (row.context() == null || row.context().isEmpty()) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: margin row context is required");
    }
    for (String key : row.context().keySet()) {
      if (!dimensions.containsKey(key)) {
        return GovernanceValidationResult.failure("VALIDATION_FAILED: unsupported margin dimension " + key);
      }
    }
    for (MarginDimensionMetadata dimension : dimensions.values()) {
      if (dimension.required() && isBlank(row.context().get(dimension.name()))) {
        return GovernanceValidationResult.failure("VALIDATION_FAILED: required margin dimension missing " + dimension.name());
      }
    }
    if (row.marginValue() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: margin value is required");
    }
    if (row.marginValue().scale() > valuePolicy.scale()) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: margin precision exceeds metadata policy");
    }
    if (!valuePolicy.negativeAllowed() && row.marginValue().signum() < 0) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: negative margins require tenant policy");
    }
    if (valuePolicy.minimum() != null && row.marginValue().compareTo(valuePolicy.minimum()) < 0) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: margin value below metadata minimum");
    }
    if (valuePolicy.maximum() != null && row.marginValue().compareTo(valuePolicy.maximum()) > 0) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: margin value above metadata maximum");
    }
    if (row.cap() != null && row.floor() != null && row.cap().compareTo(row.floor()) < 0) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: cap cannot be below floor");
    }
    if (isBlank(row.reasonCode()) || !reasonCodes.contains(row.reasonCode())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: margin change reason code is required");
    }
    if (row.effectiveStart() == null || (row.effectiveEnd() != null && !row.effectiveEnd().isAfter(row.effectiveStart()))) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: effective window is invalid");
    }
    return GovernanceValidationResult.success(null);
  }

  private String permissionFor(MarginLifecycleAction action) {
    return switch (action) {
      case SAVE_DRAFT, SUBMIT -> WRITE_PERMISSION;
      case APPROVE_AND_PUBLISH -> PUBLISH_PERMISSION;
    };
  }

  private boolean hasPermission(List<String> permissions, String permission) {
    return permissions != null && permissions.contains(permission);
  }

  private MarginConfigRowEvidence rowEvidence(MarginConfigRow row) {
    String rowHash = hash(canonicalRow(row));
    return new MarginConfigRowEvidence(row.rowId(), canonicalMap(row.context()), row.reasonCode(), rowHash);
  }

  private String currentVersionHash(String tenantId, String id) {
    MarginConfigUiResult previous = versionsByTenantAndId.get(tenantId + ":" + id);
    return previous == null ? "none" : previous.replayRef();
  }

  private String canonicalCommand(MarginConfigUiCommand command, MarginLifecycleAction action) {
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.approverId() == null ? "" : command.approverId(),
        action.name(),
        canonicalList(command.permissions()),
        canonicalMetadata(command.metadata()),
        canonicalRows(command.rows()),
        command.impactSimulation() == null ? "" : canonicalImpact(command.impactSimulation()),
        command.correlationId());
  }

  private String canonicalMetadata(MarginConfigMetadata metadata) {
    if (metadata == null) {
      return "";
    }
    return String.join(
        ":",
        metadata.schemaVersion(),
        metadata.dimensions().stream()
            .sorted(Comparator.comparing(MarginDimensionMetadata::name))
            .map(dimension -> dimension.name() + "=" + dimension.required())
            .reduce((left, right) -> left + "," + right)
            .orElse(""),
        canonicalList(metadata.reasonCodes()),
        canonicalValuePolicy(metadata.valuePolicy()),
        Boolean.toString(metadata.allowOverlappingBands()));
  }

  private String canonicalValuePolicy(MarginValuePolicy policy) {
    if (policy == null) {
      return "";
    }
    return policy.minimum() + ":" + policy.maximum() + ":" + policy.scale() + ":" + policy.negativeAllowed();
  }

  private String canonicalRows(List<MarginConfigRow> rows) {
    return rows.stream().sorted(Comparator.comparing(MarginConfigRow::rowId)).map(this::canonicalRow).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalRow(MarginConfigRow row) {
    return String.join(
        ":",
        row.rowId(),
        canonicalMap(row.context()),
        row.marginValue().toPlainString(),
        row.unit(),
        row.cap() == null ? "" : row.cap().toPlainString(),
        row.floor() == null ? "" : row.floor().toPlainString(),
        row.reasonCode(),
        Integer.toString(row.priority()),
        row.effectiveStart().toString(),
        row.effectiveEnd() == null ? "" : row.effectiveEnd().toString());
  }

  private String canonicalRowEvidence(List<MarginConfigRowEvidence> evidence) {
    return evidence.stream().sorted(Comparator.comparing(MarginConfigRowEvidence::rowId)).map(item -> item.rowId() + ":" + item.rowHash()).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalImpact(MarginImpactSimulationRef impact) {
    return String.join(":", impact.simulationId(), impact.fixtureId(), impact.resultSummaryHash());
  }

  private String canonicalMap(Map<String, String> values) {
    return values.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey().toLowerCase(Locale.ROOT) + "=" + entry.getValue())
        .reduce((left, right) -> left + "," + right)
        .orElse("");
  }

  private String canonicalList(List<String> values) {
    if (values == null) {
      return "";
    }
    return values.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
  }

  private boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
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

  private record IdempotencyEntry(String requestHash, MarginConfigUiResult result) {}
}

enum MarginLifecycleAction {
  SAVE_DRAFT,
  SUBMIT,
  APPROVE_AND_PUBLISH
}

record MarginConfigUiCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String approverId,
    List<String> permissions,
    MarginConfigMetadata metadata,
    List<MarginConfigRow> rows,
    MarginImpactSimulationRef impactSimulation,
    String correlationId) {}

record MarginConfigMetadata(
    String schemaVersion,
    List<MarginDimensionMetadata> dimensions,
    List<String> reasonCodes,
    MarginValuePolicy valuePolicy,
    boolean allowOverlappingBands) {}

record MarginDimensionMetadata(String name, boolean required) {}

record MarginValuePolicy(BigDecimal minimum, BigDecimal maximum, int scale, boolean negativeAllowed) {}

record MarginConfigRow(
    String rowId,
    Map<String, String> context,
    BigDecimal marginValue,
    String unit,
    BigDecimal cap,
    BigDecimal floor,
    String reasonCode,
    int priority,
    Instant effectiveStart,
    Instant effectiveEnd) {}

record MarginImpactSimulationRef(String simulationId, String fixtureId, String resultSummaryHash) {}

record MarginConfigRowEvidence(String rowId, String contextHashInput, String reasonCode, String rowHash) {}

record MarginConfigUiResult(
    String tenantId,
    String id,
    String versionId,
    String status,
    int version,
    Map<String, String> resultSummary,
    List<String> validationMessages,
    List<MarginConfigRowEvidence> rowEvidence,
    String auditRef,
    String replayRef,
    String correlationId,
    Instant updatedAt) {}
