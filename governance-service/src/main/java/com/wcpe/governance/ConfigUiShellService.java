package com.wcpe.governance;

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

public final class ConfigUiShellService {
  public static final String METADATA_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-ui/metadata";
  public static final String INVENTORY_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-artifacts";
  public static final String APPROVAL_QUEUE_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-approval-queue";
  public static final String READ_PERMISSION = "governance:config-ui-shell:read";
  public static final String COMPLETED_EVENT_TYPE = "config_ui_shell.completed.v1";
  public static final String AUDIT_ACTION = "CONFIG_UI_SHELL_COMPLETED";

  private final Clock clock;
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public ConfigUiShellService() {
    this(Clock.systemUTC());
  }

  public ConfigUiShellService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<ConfigUiShellSnapshot> load(ConfigUiShellQuery query) {
    GovernanceValidationResult<ConfigUiShellQuery> validation = validate(query);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    String requestHash = hash(canonicalQuery(query));
    String idempotencyLookupKey = query.tenantId() + ":" + query.idempotencyKey();
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyLookupKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        return GovernanceValidationResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return GovernanceValidationResult.success(existing.snapshot());
    }

    List<ConfigInventoryRow> rows = filterAndSortInventory(query);
    Map<String, List<ConfigActionDescriptor>> actionsByArtifactId = actionsByArtifactId(query, rows);
    List<ApprovalQueueItem> approvalQueue = approvalQueue(query, actionsByArtifactId);
    String cacheKey = cacheKey(query);
    String auditRef = deterministicId(query.tenantId(), cacheKey, query.actorId(), "audit");
    String eventId = deterministicId(query.tenantId(), cacheKey, query.correlationId(), "event");
    Instant now = clock.instant();
    ConfigUiShellSnapshot snapshot =
        new ConfigUiShellSnapshot(
            query.tenantId(),
            query.metadataVersion(),
            query.permissionsHash(),
            cacheKey,
            List.copyOf(query.artifactTypes()),
            rows,
            approvalQueue,
            actionsByArtifactId,
            statusCounts(rows),
            auditRef,
            eventId,
            requestHash,
            query.correlationId(),
            now);

    outboxEvents.add(
        new ConfigApiOutboxEvent(
            eventId,
            COMPLETED_EVENT_TYPE,
            1,
            query.tenantId(),
            cacheKey,
            query.metadataVersion(),
            query.actorId(),
            query.correlationId(),
            query.correlationId(),
            query.idempotencyKey(),
            now,
            Map.of(
                "metadataVersion", query.metadataVersion(),
                "inventoryCount", Integer.toString(rows.size()),
                "approvalQueueCount", Integer.toString(approvalQueue.size()),
                "cacheKey", cacheKey)));
    auditRecords.add(
        new ConfigApiAuditRecord(
            auditRef,
            query.tenantId(),
            cacheKey,
            query.metadataVersion(),
            query.actorId(),
            AUDIT_ACTION,
            "",
            requestHash,
            query.correlationId(),
            now));
    idempotencyEntries.put(idempotencyLookupKey, new IdempotencyEntry(requestHash, snapshot));
    return GovernanceValidationResult.success(snapshot);
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private GovernanceValidationResult<ConfigUiShellQuery> validate(ConfigUiShellQuery query) {
    if (query == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: query is required");
    }
    if (!isUuid(query.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (isBlank(query.idempotencyKey())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: idempotency key is required");
    }
    if (isBlank(query.actorId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: actorId is required");
    }
    if (isBlank(query.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: correlationId is required");
    }
    if (isBlank(query.metadataVersion()) || isBlank(query.permissionsHash())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: metadataVersion and permissionsHash are required");
    }
    if (query.permissions() == null || !query.permissions().contains(READ_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (query.artifactTypes() == null || query.artifactTypes().isEmpty()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: artifact type metadata is required");
    }
    for (ConfigArtifactTypeMetadata metadata : query.artifactTypes()) {
      GovernanceValidationResult<Void> metadataValidation = validate(metadata);
      if (!metadataValidation.valid()) {
        return GovernanceValidationResult.failure(metadataValidation.error().orElseThrow());
      }
    }
    if (query.inventoryRows() == null || query.approvalQueueItems() == null || query.filter() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: inventory, approval queue, and filter inputs are required");
    }
    return GovernanceValidationResult.success(query);
  }

  private GovernanceValidationResult<Void> validate(ConfigArtifactTypeMetadata metadata) {
    if (metadata == null || isBlank(metadata.artifactType()) || isBlank(metadata.displayLabel()) || isBlank(metadata.route())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: artifact metadata must include type, label, and route");
    }
    if (metadata.fields() == null || metadata.fields().isEmpty()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: field metadata is required");
    }
    if (metadata.actions() == null || metadata.actions().isEmpty()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: workflow action metadata is required");
    }
    return GovernanceValidationResult.success(null);
  }

  private List<ConfigInventoryRow> filterAndSortInventory(ConfigUiShellQuery query) {
    ConfigInventoryFilter filter = query.filter();
    String search = filter.searchText() == null ? "" : filter.searchText().toLowerCase(Locale.ROOT);
    return query.inventoryRows().stream()
        .filter(row -> query.tenantId().equals(row.tenantId()))
        .filter(row -> isBlank(filter.artifactType()) || filter.artifactType().equals(row.artifactType()))
        .filter(row -> isBlank(filter.status()) || filter.status().equals(row.status()))
        .filter(row -> filter.effectiveOn() == null || effectiveOn(row, filter.effectiveOn()))
        .filter(row -> search.isBlank() || row.name().toLowerCase(Locale.ROOT).contains(search) || row.context().toLowerCase(Locale.ROOT).contains(search))
        .sorted(comparator(filter))
        .toList();
  }

  private boolean effectiveOn(ConfigInventoryRow row, Instant effectiveOn) {
    return (row.effectiveStart() == null || !row.effectiveStart().isAfter(effectiveOn))
        && (row.effectiveEnd() == null || row.effectiveEnd().isAfter(effectiveOn));
  }

  private Comparator<ConfigInventoryRow> comparator(ConfigInventoryFilter filter) {
    Comparator<ConfigInventoryRow> comparator =
        switch (filter.sortBy() == null ? "lastChangedAt" : filter.sortBy()) {
          case "artifactType" -> Comparator.comparing(ConfigInventoryRow::artifactType);
          case "name" -> Comparator.comparing(ConfigInventoryRow::name);
          case "status" -> Comparator.comparing(ConfigInventoryRow::status);
          case "version" -> Comparator.comparingInt(ConfigInventoryRow::version);
          default -> Comparator.comparing(ConfigInventoryRow::lastChangedAt);
        };
    comparator = comparator.thenComparing(ConfigInventoryRow::artifactId);
    return filter.ascending() ? comparator : comparator.reversed();
  }

  private Map<String, List<ConfigActionDescriptor>> actionsByArtifactId(ConfigUiShellQuery query, List<ConfigInventoryRow> rows) {
    Set<String> permissions = new HashSet<>(query.permissions());
    Map<String, ConfigArtifactTypeMetadata> metadataByType = new HashMap<>();
    for (ConfigArtifactTypeMetadata metadata : query.artifactTypes()) {
      metadataByType.put(metadata.artifactType(), metadata);
    }
    Map<String, List<ConfigActionDescriptor>> result = new HashMap<>();
    for (ConfigInventoryRow row : rows) {
      ConfigArtifactTypeMetadata metadata = metadataByType.get(row.artifactType());
      List<ConfigActionDescriptor> available = metadata == null ? List.of() : metadata.actions().stream()
          .filter(action -> permissions.contains(action.requiredPermission()))
          .filter(action -> action.allowedStatuses().isEmpty() || action.allowedStatuses().contains(row.status()))
          .sorted(Comparator.comparing(ConfigActionDescriptor::actionKey))
          .toList();
      result.put(row.artifactId(), available);
    }
    return Map.copyOf(result);
  }

  private List<ApprovalQueueItem> approvalQueue(ConfigUiShellQuery query, Map<String, List<ConfigActionDescriptor>> actionsByArtifactId) {
    return query.approvalQueueItems().stream()
        .filter(item -> query.tenantId().equals(item.tenantId()))
        .map(item -> item.withAvailableActions(actionsByArtifactId.getOrDefault(item.artifactId(), List.of()).stream().filter(ConfigActionDescriptor::approvalAction).toList()))
        .sorted(Comparator.comparing(ApprovalQueueItem::submittedAt).thenComparing(ApprovalQueueItem::approvalRequestId))
        .toList();
  }

  private Map<String, Integer> statusCounts(List<ConfigInventoryRow> rows) {
    Map<String, Integer> counts = new HashMap<>();
    for (ConfigInventoryRow row : rows) {
      counts.merge(row.status(), 1, Integer::sum);
    }
    return Map.copyOf(counts);
  }

  private String cacheKey(ConfigUiShellQuery query) {
    return hash(String.join("|", query.tenantId(), query.permissionsHash(), query.metadataVersion(), canonicalFilter(query.filter()))).substring(0, 32);
  }

  private String canonicalQuery(ConfigUiShellQuery query) {
    return String.join(
        "|",
        query.tenantId(),
        query.idempotencyKey(),
        query.actorId(),
        query.metadataVersion(),
        query.permissionsHash(),
        canonicalList(query.permissions()),
        canonicalArtifactTypes(query.artifactTypes()),
        canonicalInventory(query.inventoryRows()),
        canonicalApprovalQueue(query.approvalQueueItems()),
        canonicalFilter(query.filter()),
        query.correlationId());
  }

  private String canonicalArtifactTypes(List<ConfigArtifactTypeMetadata> metadata) {
    return metadata.stream()
        .sorted(Comparator.comparing(ConfigArtifactTypeMetadata::artifactType))
        .map(item -> item.artifactType() + ":" + item.displayLabel() + ":" + item.route() + ":" + canonicalFields(item.fields()) + ":" + canonicalActions(item.actions()))
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String canonicalFields(List<ConfigFieldMetadata> fields) {
    return fields.stream()
        .sorted(Comparator.comparing(ConfigFieldMetadata::fieldKey))
        .map(field -> field.fieldKey() + ":" + field.label() + ":" + field.sortable() + ":" + field.filterable() + ":" + field.valueType())
        .reduce((left, right) -> left + "," + right)
        .orElse("");
  }

  private String canonicalActions(List<ConfigActionDescriptor> actions) {
    return actions.stream()
        .sorted(Comparator.comparing(ConfigActionDescriptor::actionKey))
        .map(action -> action.actionKey() + ":" + action.label() + ":" + action.route() + ":" + action.requiredPermission() + ":" + canonicalList(action.allowedStatuses()) + ":" + action.approvalAction())
        .reduce((left, right) -> left + "," + right)
        .orElse("");
  }

  private String canonicalInventory(List<ConfigInventoryRow> rows) {
    return rows.stream()
        .sorted(Comparator.comparing(ConfigInventoryRow::tenantId).thenComparing(ConfigInventoryRow::artifactId))
        .map(row -> row.tenantId() + ":" + row.artifactId() + ":" + row.artifactType() + ":" + row.name() + ":" + row.context() + ":" + row.status() + ":" + row.version() + ":" + row.lastChangedAt())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String canonicalApprovalQueue(List<ApprovalQueueItem> items) {
    return items.stream()
        .sorted(Comparator.comparing(ApprovalQueueItem::tenantId).thenComparing(ApprovalQueueItem::approvalRequestId))
        .map(item -> item.tenantId() + ":" + item.approvalRequestId() + ":" + item.artifactId() + ":" + item.status() + ":" + item.submittedAt())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String canonicalFilter(ConfigInventoryFilter filter) {
    return String.join(
        ":",
        filter.searchText() == null ? "" : filter.searchText(),
        filter.artifactType() == null ? "" : filter.artifactType(),
        filter.status() == null ? "" : filter.status(),
        filter.effectiveOn() == null ? "" : filter.effectiveOn().toString(),
        filter.sortBy() == null ? "" : filter.sortBy(),
        Boolean.toString(filter.ascending()));
  }

  private String canonicalList(List<String> values) {
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

  private record IdempotencyEntry(String requestHash, ConfigUiShellSnapshot snapshot) {}
}

record ConfigUiShellQuery(
    String tenantId,
    String idempotencyKey,
    String actorId,
    String metadataVersion,
    String permissionsHash,
    List<String> permissions,
    List<ConfigArtifactTypeMetadata> artifactTypes,
    List<ConfigInventoryRow> inventoryRows,
    List<ApprovalQueueItem> approvalQueueItems,
    ConfigInventoryFilter filter,
    String correlationId) {}

record ConfigArtifactTypeMetadata(
    String artifactType,
    String displayLabel,
    String route,
    List<ConfigFieldMetadata> fields,
    List<ConfigActionDescriptor> actions) {}

record ConfigFieldMetadata(String fieldKey, String label, boolean sortable, boolean filterable, String valueType) {}

record ConfigActionDescriptor(
    String actionKey,
    String label,
    String route,
    String requiredPermission,
    List<String> allowedStatuses,
    boolean approvalAction) {}

record ConfigInventoryFilter(
    String searchText,
    String artifactType,
    String status,
    Instant effectiveOn,
    String sortBy,
    boolean ascending) {}

record ConfigInventoryRow(
    String tenantId,
    String artifactId,
    String artifactType,
    String name,
    String context,
    String status,
    int version,
    Instant effectiveStart,
    Instant effectiveEnd,
    String validationStatus,
    String approvalStatus,
    String lastChangedBy,
    Instant lastChangedAt,
    String publishStatus) {}

record ApprovalQueueItem(
    String tenantId,
    String approvalRequestId,
    String artifactId,
    String artifactType,
    String name,
    String status,
    String submittedBy,
    Instant submittedAt,
    Instant dueAt,
    List<ConfigActionDescriptor> availableActions) {
  ApprovalQueueItem withAvailableActions(List<ConfigActionDescriptor> actions) {
    return new ApprovalQueueItem(tenantId, approvalRequestId, artifactId, artifactType, name, status, submittedBy, submittedAt, dueAt, List.copyOf(actions));
  }
}

record ConfigUiShellSnapshot(
    String tenantId,
    String metadataVersion,
    String permissionsHash,
    String cacheKey,
    List<ConfigArtifactTypeMetadata> artifactTypes,
    List<ConfigInventoryRow> inventoryRows,
    List<ApprovalQueueItem> approvalQueueItems,
    Map<String, List<ConfigActionDescriptor>> actionsByArtifactId,
    Map<String, Integer> statusCounts,
    String auditRef,
    String eventId,
    String replayRef,
    String correlationId,
    Instant generatedAt) {}
