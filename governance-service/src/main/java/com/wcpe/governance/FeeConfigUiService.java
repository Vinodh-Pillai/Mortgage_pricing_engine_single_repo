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

public final class FeeConfigUiService {
  public static final String METADATA_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/fees/metadata";
  public static final String LIST_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/fees";
  public static final String IMPACT_SIMULATION_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/fees/{versionId}/impact-simulations";
  public static final String WRITE_PERMISSION = "admin.config.write";
  public static final String SIMULATE_PERMISSION = "admin.config.simulate";
  public static final String APPROVE_PERMISSION = "admin.config.approve";
  public static final String PUBLISH_PERMISSION = "admin.config.publish";
  public static final String COMPLETED_EVENT_TYPE = "fee_config_ui.completed.v1";
  public static final String AUDIT_ACTION = "FEE_CONFIG_UI_COMPLETED";
  public static final String ARTIFACT_TYPE = "FEE_CONFIG";

  private final Clock clock;
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final Map<String, FeeConfigUiResult> versionsByTenantAndId = new HashMap<>();
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public FeeConfigUiService() {
    this(Clock.systemUTC());
  }

  public FeeConfigUiService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<FeeConfigUiResult> saveDraft(FeeConfigUiCommand command) {
    return apply(command, FeeLifecycleAction.SAVE_DRAFT);
  }

  public GovernanceValidationResult<FeeConfigUiResult> submit(FeeConfigUiCommand command) {
    return apply(command, FeeLifecycleAction.SUBMIT);
  }

  public GovernanceValidationResult<FeeConfigUiResult> approveAndPublish(FeeConfigUiCommand command) {
    return apply(command, FeeLifecycleAction.APPROVE_AND_PUBLISH);
  }

  public List<FeeConfigUiResult> versionsForTenant(String tenantId) {
    return versionsByTenantAndId.values().stream()
        .filter(version -> version.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(FeeConfigUiResult::updatedAt).thenComparing(FeeConfigUiResult::id))
        .toList();
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private GovernanceValidationResult<FeeConfigUiResult> apply(FeeConfigUiCommand command, FeeLifecycleAction action) {
    GovernanceValidationResult<FeeConfigUiCommand> validation = validate(command, action);
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
    List<FeeConfigRowEvidence> rowEvidence = command.rows().stream().map(this::rowEvidence).toList();
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

    FeeConfigUiResult result =
        new FeeConfigUiResult(
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

  private GovernanceValidationResult<FeeConfigUiCommand> validate(FeeConfigUiCommand command, FeeLifecycleAction action) {
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
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: fee metadata schema is required");
    }
    if (command.metadata().dimensions() == null || command.metadata().dimensions().isEmpty()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: fee dimensions must come from metadata");
    }
    if (command.metadata().reasonCodes() == null || command.metadata().reasonCodes().isEmpty()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: reason code catalog is required");
    }
    if (command.metadata().calculationMethods() == null || command.metadata().calculationMethods().isEmpty()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: fee calculation methods must come from metadata");
    }
    if (command.metadata().valuePolicy() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: fee value policy is required");
    }
    if (command.rows() == null || command.rows().isEmpty()) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: at least one fee row is required");
    }
    if ((action == FeeLifecycleAction.SUBMIT || action == FeeLifecycleAction.APPROVE_AND_PUBLISH) && command.impactSimulation() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: fee impact simulation evidence is required");
    }
    if (action == FeeLifecycleAction.APPROVE_AND_PUBLISH) {
      if (!hasPermission(command.permissions(), APPROVE_PERMISSION) || !hasPermission(command.permissions(), PUBLISH_PERMISSION)) {
        return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
      }
      if (isBlank(command.approverId())) {
        return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: approverId is required");
      }
      if (command.actorId().equals(command.approverId())) {
        return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: requester cannot approve their own fee config");
      }
    }

    Map<String, FeeDimensionMetadata> dimensions = new HashMap<>();
    for (FeeDimensionMetadata dimension : command.metadata().dimensions()) {
      if (dimension == null || isBlank(dimension.name())) {
        return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: dimension metadata must include a name");
      }
      if (dimension.allowedValues() == null || dimension.allowedValues().isEmpty()) {
        return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: dimension metadata must include supported values " + dimension.name());
      }
      for (String allowedValue : dimension.allowedValues()) {
        if (isBlank(allowedValue)) {
          return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: dimension metadata has blank supported value " + dimension.name());
        }
      }
      dimensions.put(dimension.name(), dimension);
    }
    Set<String> reasonCodes = new HashSet<>(command.metadata().reasonCodes());
    Set<String> calculationMethods = new HashSet<>(command.metadata().calculationMethods());
    Map<String, List<FeeConfigRow>> activeFeesByConfiguredKey = new HashMap<>();
    Set<Integer> priorities = new HashSet<>();
    for (FeeConfigRow row : command.rows()) {
      GovernanceValidationResult<Void> rowValidation = validateRow(row, dimensions, reasonCodes, calculationMethods, command.metadata());
      if (!rowValidation.valid()) {
        return GovernanceValidationResult.failure(rowValidation.error().orElseThrow());
      }
      String activeFeeKey = row.feeCode() + "|" + canonicalMap(row.context());
      if (!command.metadata().allowDuplicateActiveFees()) {
        List<FeeConfigRow> rowsWithSameKey = activeFeesByConfiguredKey.computeIfAbsent(activeFeeKey, ignored -> new ArrayList<>());
        for (FeeConfigRow existingRow : rowsWithSameKey) {
          if (effectiveWindowsOverlap(existingRow, row)) {
            return GovernanceValidationResult.failure("VALIDATION_FAILED: overlapping active fee requires configured precedence");
          }
        }
        rowsWithSameKey.add(row);
      }
      if (!priorities.add(row.priority())) {
        return GovernanceValidationResult.failure("VALIDATION_FAILED: duplicate fee priority is not allowed");
      }
    }
    return GovernanceValidationResult.success(command);
  }

  private GovernanceValidationResult<Void> validateRow(
      FeeConfigRow row,
      Map<String, FeeDimensionMetadata> dimensions,
      Set<String> reasonCodes,
      Set<String> calculationMethods,
      FeeConfigMetadata metadata) {
    if (row == null || isBlank(row.rowId()) || isBlank(row.feeCode())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fee row id and fee code are required");
    }
    if (isBlank(row.category()) || isBlank(row.displayLabel()) || isBlank(row.calculationMethod())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fee category, label, and calculation method are required");
    }
    if (!calculationMethods.contains(row.calculationMethod())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: unsupported fee calculation method " + row.calculationMethod());
    }
    if (row.context() == null || row.context().isEmpty()) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fee row context is required");
    }
    for (String key : row.context().keySet()) {
      if (!dimensions.containsKey(key)) {
        return GovernanceValidationResult.failure("VALIDATION_FAILED: unsupported fee dimension " + key);
      }
    }
    for (FeeDimensionMetadata dimension : dimensions.values()) {
      String contextValue = row.context().get(dimension.name());
      if (dimension.required() && isBlank(contextValue)) {
        return GovernanceValidationResult.failure("VALIDATION_FAILED: required fee dimension missing " + dimension.name());
      }
      if (!isBlank(contextValue) && !dimension.allowedValues().contains(contextValue)) {
        return GovernanceValidationResult.failure("VALIDATION_FAILED: unsupported fee dimension value " + dimension.name());
      }
    }
    if (row.amountValue() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fee amount is required");
    }
    if (row.amountValue().scale() > metadata.valuePolicy().scale()) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fee precision exceeds metadata policy");
    }
    if (!metadata.valuePolicy().negativeAllowed() && row.amountValue().signum() < 0) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: negative fees require tenant policy");
    }
    if (metadata.valuePolicy().minimum() != null && row.amountValue().compareTo(metadata.valuePolicy().minimum()) < 0) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fee value below metadata minimum");
    }
    if (metadata.valuePolicy().maximum() != null && row.amountValue().compareTo(metadata.valuePolicy().maximum()) > 0) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fee value above metadata maximum");
    }
    if (row.capValue() != null && row.floorValue() != null && row.capValue().compareTo(row.floorValue()) < 0) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fee cap cannot be below floor");
    }
    if (metadata.disclosureRefRequired() && isBlank(row.disclosureRef())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: disclosure reference is required for fee config");
    }
    if (metadata.toleranceRefRequired() && isBlank(row.toleranceRef())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: tolerance policy reference is required for fee config");
    }
    if (isBlank(row.reasonCode()) || !reasonCodes.contains(row.reasonCode())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fee change reason code is required");
    }
    if (row.effectiveStart() == null || (row.effectiveEnd() != null && !row.effectiveEnd().isAfter(row.effectiveStart()))) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: effective window is invalid");
    }
    return GovernanceValidationResult.success(null);
  }

  private String permissionFor(FeeLifecycleAction action) {
    return switch (action) {
      case SAVE_DRAFT, SUBMIT -> WRITE_PERMISSION;
      case APPROVE_AND_PUBLISH -> PUBLISH_PERMISSION;
    };
  }

  private boolean hasPermission(List<String> permissions, String permission) {
    return permissions != null && permissions.contains(permission);
  }

  private boolean effectiveWindowsOverlap(FeeConfigRow left, FeeConfigRow right) {
    boolean leftStartsBeforeRightEnds = right.effectiveEnd() == null || left.effectiveStart().isBefore(right.effectiveEnd());
    boolean rightStartsBeforeLeftEnds = left.effectiveEnd() == null || right.effectiveStart().isBefore(left.effectiveEnd());
    return leftStartsBeforeRightEnds && rightStartsBeforeLeftEnds;
  }

  private FeeConfigRowEvidence rowEvidence(FeeConfigRow row) {
    String rowHash = hash(canonicalRow(row));
    return new FeeConfigRowEvidence(row.rowId(), row.feeCode(), canonicalMap(row.context()), row.disclosureRef(), row.toleranceRef(), rowHash);
  }

  private String currentVersionHash(String tenantId, String id) {
    FeeConfigUiResult previous = versionsByTenantAndId.get(tenantId + ":" + id);
    return previous == null ? "none" : previous.replayRef();
  }

  private String canonicalCommand(FeeConfigUiCommand command, FeeLifecycleAction action) {
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

  private String canonicalMetadata(FeeConfigMetadata metadata) {
    if (metadata == null) {
      return "";
    }
    return String.join(
        ":",
        metadata.schemaVersion(),
        metadata.dimensions().stream()
            .sorted(Comparator.comparing(FeeDimensionMetadata::name))
            .map(dimension -> dimension.name() + "=" + dimension.required() + "[" + canonicalList(dimension.allowedValues()) + "]")
            .reduce((left, right) -> left + "," + right)
            .orElse(""),
        canonicalList(metadata.reasonCodes()),
        canonicalList(metadata.calculationMethods()),
        canonicalValuePolicy(metadata.valuePolicy()),
        Boolean.toString(metadata.allowDuplicateActiveFees()),
        Boolean.toString(metadata.disclosureRefRequired()),
        Boolean.toString(metadata.toleranceRefRequired()));
  }

  private String canonicalValuePolicy(FeeValuePolicy policy) {
    if (policy == null) {
      return "";
    }
    return policy.minimum() + ":" + policy.maximum() + ":" + policy.scale() + ":" + policy.negativeAllowed();
  }

  private String canonicalRows(List<FeeConfigRow> rows) {
    return rows.stream().sorted(Comparator.comparing(FeeConfigRow::rowId)).map(this::canonicalRow).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalRow(FeeConfigRow row) {
    return String.join(
        ":",
        row.rowId(),
        row.feeCode(),
        row.category(),
        row.displayLabel(),
        row.calculationMethod(),
        canonicalMap(row.context()),
        row.amountValue().toPlainString(),
        row.amountType(),
        row.capValue() == null ? "" : row.capValue().toPlainString(),
        row.floorValue() == null ? "" : row.floorValue().toPlainString(),
        row.waiverCondition() == null ? "" : row.waiverCondition(),
        row.disclosureRef() == null ? "" : row.disclosureRef(),
        row.toleranceRef() == null ? "" : row.toleranceRef(),
        row.reasonCode(),
        Integer.toString(row.priority()),
        row.effectiveStart().toString(),
        row.effectiveEnd() == null ? "" : row.effectiveEnd().toString());
  }

  private String canonicalRowEvidence(List<FeeConfigRowEvidence> evidence) {
    return evidence.stream().sorted(Comparator.comparing(FeeConfigRowEvidence::rowId)).map(item -> item.rowId() + ":" + item.rowHash()).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalImpact(FeeImpactSimulationRef impact) {
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

  private record IdempotencyEntry(String requestHash, FeeConfigUiResult result) {}
}

enum FeeLifecycleAction {
  SAVE_DRAFT,
  SUBMIT,
  APPROVE_AND_PUBLISH
}

record FeeConfigUiCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String approverId,
    List<String> permissions,
    FeeConfigMetadata metadata,
    List<FeeConfigRow> rows,
    FeeImpactSimulationRef impactSimulation,
    String correlationId) {}

record FeeConfigMetadata(
    String schemaVersion,
    List<FeeDimensionMetadata> dimensions,
    List<String> reasonCodes,
    List<String> calculationMethods,
    FeeValuePolicy valuePolicy,
    boolean allowDuplicateActiveFees,
    boolean disclosureRefRequired,
    boolean toleranceRefRequired) {}

record FeeDimensionMetadata(String name, boolean required, List<String> allowedValues) {}

record FeeValuePolicy(BigDecimal minimum, BigDecimal maximum, int scale, boolean negativeAllowed) {}

record FeeConfigRow(
    String rowId,
    String feeCode,
    String category,
    String displayLabel,
    String calculationMethod,
    Map<String, String> context,
    BigDecimal amountValue,
    String amountType,
    BigDecimal capValue,
    BigDecimal floorValue,
    String waiverCondition,
    String disclosureRef,
    String toleranceRef,
    String reasonCode,
    int priority,
    Instant effectiveStart,
    Instant effectiveEnd) {}

record FeeImpactSimulationRef(String simulationId, String fixtureId, String resultSummaryHash) {}

record FeeConfigRowEvidence(String rowId, String feeCode, String contextHashInput, String disclosureRef, String toleranceRef, String rowHash) {}

record FeeConfigUiResult(
    String tenantId,
    String id,
    String versionId,
    String status,
    int version,
    Map<String, String> resultSummary,
    List<String> validationMessages,
    List<FeeConfigRowEvidence> rowEvidence,
    String auditRef,
    String replayRef,
    String correlationId,
    Instant updatedAt) {}
