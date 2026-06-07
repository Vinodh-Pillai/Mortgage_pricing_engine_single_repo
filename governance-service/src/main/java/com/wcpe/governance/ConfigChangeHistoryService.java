package com.wcpe.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ConfigChangeHistoryService {
  public static final String LIST_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-history";
  public static final String DETAIL_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-history/{historyId}";
  public static final String DIFF_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-history/diffs";
  public static final String EVIDENCE_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-history/{historyId}/evidence";
  public static final String READ_PERMISSION = "admin.config.history.read";
  public static final String DIFF_PERMISSION = "admin.config.history.diff";
  public static final String EVIDENCE_PERMISSION = "admin.config.history.evidence";
  public static final String COMPLETED_EVENT_TYPE = "config_change_history.completed.v1";
  public static final String EVIDENCE_EVENT_TYPE = "config_change_history.evidence_exported.v1";
  public static final String AUDIT_ACTION = "CHANGE_HISTORY_UI_COMPLETED";
  public static final String EVIDENCE_AUDIT_ACTION = "CHANGE_HISTORY_UI_EVIDENCE_EXPORTED";

  private final Clock clock;
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public ConfigChangeHistoryService() {
    this(Clock.systemUTC());
  }

  public ConfigChangeHistoryService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<ConfigHistorySearchResult> search(ConfigHistoryQuery query) {
    GovernanceValidationResult<ConfigHistoryQuery> validation = validateQuery(query);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    Instant startedAt = clock.instant();
    List<ConfigHistoryEntry> rows = query.entries().stream()
        .filter(entry -> query.tenantId().equals(entry.tenantId()))
        .filter(entry -> matches(query.filter(), entry))
        .sorted(Comparator.comparing(ConfigHistoryEntry::occurredAt).thenComparingLong(ConfigHistoryEntry::sequence).reversed())
        .map(entry -> redact(entry, query.redactionPolicy()))
        .toList();
    String replayRef = hash(canonicalEntries(rows) + canonicalFilter(query.filter()) + query.redactionPolicy().policyId());
    String auditRef = deterministicId(query.tenantId(), query.actorId(), replayRef, "history-search-audit");
    String eventId = deterministicId(query.tenantId(), query.correlationId(), replayRef, "history-search-event");
    ConfigHistorySearchResult result = new ConfigHistorySearchResult(
        query.tenantId(),
        rows,
        Map.of(
            "queryLatencyBucket", "local-domain",
            "searchErrorCount", "0",
            "redactionDenialCount", Long.toString(rows.stream().filter(ConfigHistoryEntry::redacted).count())),
        replayRef,
        auditRef,
        eventId,
        query.correlationId(),
        startedAt);
    outboxEvents.add(new ConfigApiOutboxEvent(
        eventId,
        COMPLETED_EVENT_TYPE,
        1,
        query.tenantId(),
        "config-history",
        replayRef,
        query.actorId(),
        query.correlationId(),
        query.correlationId(),
        "history-search",
        startedAt,
        Map.of("resultCount", Integer.toString(rows.size()), "replayRef", replayRef, "redactionPolicy", query.redactionPolicy().policyId())));
    auditRecords.add(new ConfigApiAuditRecord(
        auditRef,
        query.tenantId(),
        "config-history",
        replayRef,
        query.actorId(),
        AUDIT_ACTION,
        "filterHash=" + hash(canonicalFilter(query.filter())),
        "resultHash=" + replayRef,
        query.correlationId(),
        startedAt));
    return GovernanceValidationResult.success(result);
  }

  public GovernanceValidationResult<ConfigHistoryDetail> detail(ConfigHistoryQuery query, String historyId) {
    GovernanceValidationResult<ConfigHistoryQuery> validation = validateQuery(query);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }
    if (isBlank(historyId)) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: historyId is required");
    }
    return query.entries().stream()
        .filter(entry -> query.tenantId().equals(entry.tenantId()))
        .filter(entry -> historyId.equals(entry.historyId()))
        .findFirst()
        .map(entry -> GovernanceValidationResult.success(new ConfigHistoryDetail(redact(entry, query.redactionPolicy()), query.correlationId(), hash(canonicalEntry(entry)))))
        .orElseGet(() -> GovernanceValidationResult.failure("NOT_FOUND"));
  }

  public GovernanceValidationResult<ConfigDiffResult> createDiff(ConfigDiffRequest request) {
    GovernanceValidationResult<ConfigDiffRequest> validation = validateDiffRequest(request);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }
    ConfigHistoryEntry from = findTenantEntry(request.tenantId(), request.fromVersionId(), request.entries());
    ConfigHistoryEntry to = findTenantEntry(request.tenantId(), request.toVersionId(), request.entries());
    if (from == null || to == null || !from.artifactId().equals(to.artifactId())) {
      return GovernanceValidationResult.failure("NOT_FOUND");
    }
    Map<String, String> redacted = new HashMap<>();
    for (Map.Entry<String, String> item : to.diffSummary().entrySet()) {
      redacted.put(item.getKey(), maskIfConfidential(item.getKey(), item.getValue(), request.redactionPolicy()));
    }
    String canonical = canonicalMap(redacted) + request.redactionPolicy().policyId() + from.versionId() + to.versionId();
    return GovernanceValidationResult.success(new ConfigDiffResult(
        deterministicId(request.tenantId(), from.versionId(), to.versionId(), request.redactionPolicy().policyId(), "diff"),
        request.tenantId(),
        from.versionId(),
        to.versionId(),
        Map.copyOf(redacted),
        request.redactionPolicy().policyId(),
        hash(canonical),
        request.correlationId(),
        clock.instant()));
  }

  public GovernanceValidationResult<ConfigEvidencePackage> evidence(ConfigEvidenceRequest request) {
    GovernanceValidationResult<ConfigEvidenceRequest> validation = validateEvidenceRequest(request);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }
    ConfigHistoryEntry entry = findTenantHistory(request.tenantId(), request.historyId(), request.entries());
    if (entry == null) {
      return GovernanceValidationResult.failure("NOT_FOUND");
    }
    Instant now = clock.instant();
    String exportId = deterministicId(request.tenantId(), request.historyId(), request.actorId(), request.correlationId(), "evidence-export");
    String replayHash = hash(canonicalEntry(entry) + canonicalLinks(entry.evidenceLinks()));
    outboxEvents.add(new ConfigApiOutboxEvent(
        deterministicId(exportId, "event"),
        EVIDENCE_EVENT_TYPE,
        1,
        request.tenantId(),
        entry.artifactId(),
        entry.versionId(),
        request.actorId(),
        request.correlationId(),
        entry.replayReference().eventId(),
        exportId,
        now,
        Map.of("historyId", entry.historyId(), "evidenceCount", Integer.toString(entry.evidenceLinks().size()), "replayHash", replayHash)));
    auditRecords.add(new ConfigApiAuditRecord(
        deterministicId(exportId, "audit"),
        request.tenantId(),
        entry.artifactId(),
        entry.versionId(),
        request.actorId(),
        EVIDENCE_AUDIT_ACTION,
        "historyHash=" + hash(canonicalEntry(entry)),
        "exportHash=" + replayHash,
        request.correlationId(),
        now));
    return GovernanceValidationResult.success(new ConfigEvidencePackage(exportId, request.tenantId(), entry.historyId(), entry.evidenceLinks(), replayHash, request.correlationId(), now));
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private GovernanceValidationResult<ConfigHistoryQuery> validateQuery(ConfigHistoryQuery query) {
    if (query == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: query is required");
    }
    if (!isUuid(query.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (isBlank(query.actorId()) || isBlank(query.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: actorId and correlationId are required");
    }
    if (!hasPermission(query.permissions(), READ_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (query.redactionPolicy() == null || isBlank(query.redactionPolicy().policyId())) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: redaction policy is required");
    }
    if (query.entries() == null || query.filter() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: entries and filter are required");
    }
    return GovernanceValidationResult.success(query);
  }

  private GovernanceValidationResult<ConfigDiffRequest> validateDiffRequest(ConfigDiffRequest request) {
    if (request == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: diff request is required");
    }
    if (!isUuid(request.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (!hasPermission(request.permissions(), DIFF_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (isBlank(request.fromVersionId()) || isBlank(request.toVersionId()) || isBlank(request.actorId()) || isBlank(request.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: fromVersionId, toVersionId, actorId, and correlationId are required");
    }
    if (request.redactionPolicy() == null || request.entries() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: redaction policy and entries are required");
    }
    return GovernanceValidationResult.success(request);
  }

  private GovernanceValidationResult<ConfigEvidenceRequest> validateEvidenceRequest(ConfigEvidenceRequest request) {
    if (request == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: evidence request is required");
    }
    if (!isUuid(request.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (!hasPermission(request.permissions(), EVIDENCE_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (isBlank(request.historyId()) || isBlank(request.actorId()) || isBlank(request.correlationId()) || request.entries() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: historyId, actorId, correlationId, and entries are required");
    }
    return GovernanceValidationResult.success(request);
  }

  private boolean matches(ConfigHistoryFilter filter, ConfigHistoryEntry entry) {
    return (isBlank(filter.artifactId()) || filter.artifactId().equals(entry.artifactId()))
        && (isBlank(filter.actorId()) || filter.actorId().equals(entry.actorId()))
        && (isBlank(filter.eventType()) || filter.eventType().equals(entry.eventType()))
        && (isBlank(filter.reasonCode()) || filter.reasonCode().equals(entry.reasonCode()))
        && (filter.from() == null || !entry.occurredAt().isBefore(filter.from()))
        && (filter.to() == null || !entry.occurredAt().isAfter(filter.to()))
        && (isBlank(filter.status()) || filter.status().equals(entry.statusTo()));
  }

  private ConfigHistoryEntry redact(ConfigHistoryEntry entry, RedactionPolicyRef policy) {
    Map<String, String> redacted = new HashMap<>();
    for (Map.Entry<String, String> item : entry.diffSummary().entrySet()) {
      redacted.put(item.getKey(), maskIfConfidential(item.getKey(), item.getValue(), policy));
    }
    boolean changed = !redacted.equals(entry.diffSummary());
    return entry.withDiffSummary(Map.copyOf(redacted), changed);
  }

  private String maskIfConfidential(String key, String value, RedactionPolicyRef policy) {
    if (policy.confidentialKeys().contains(key) && !policy.revealConfidentialValues()) {
      return "***REDACTED***#" + hash(value).substring(0, 12);
    }
    return value;
  }

  private ConfigHistoryEntry findTenantEntry(String tenantId, String versionId, List<ConfigHistoryEntry> entries) {
    return entries.stream().filter(entry -> tenantId.equals(entry.tenantId())).filter(entry -> versionId.equals(entry.versionId())).findFirst().orElse(null);
  }

  private ConfigHistoryEntry findTenantHistory(String tenantId, String historyId, List<ConfigHistoryEntry> entries) {
    return entries.stream().filter(entry -> tenantId.equals(entry.tenantId())).filter(entry -> historyId.equals(entry.historyId())).findFirst().orElse(null);
  }

  private boolean hasPermission(List<String> permissions, String permission) {
    return permissions != null && permissions.contains(permission);
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

  private String canonicalEntries(List<ConfigHistoryEntry> entries) {
    return entries.stream().map(this::canonicalEntry).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalEntry(ConfigHistoryEntry entry) {
    return String.join(
        "|",
        entry.tenantId(),
        entry.historyId(),
        entry.artifactId(),
        entry.versionId(),
        entry.eventType(),
        entry.actorId(),
        entry.statusFrom(),
        entry.statusTo(),
        entry.reasonCode(),
        entry.occurredAt().toString(),
        Long.toString(entry.sequence()),
        canonicalMap(entry.diffSummary()),
        entry.replayReference().eventId(),
        entry.replayReference().replayHash());
  }

  private String canonicalFilter(ConfigHistoryFilter filter) {
    return String.join(
        "|",
        nullToBlank(filter.artifactId()),
        nullToBlank(filter.actorId()),
        nullToBlank(filter.eventType()),
        nullToBlank(filter.reasonCode()),
        nullToBlank(filter.status()),
        filter.from() == null ? "" : filter.from().toString(),
        filter.to() == null ? "" : filter.to().toString());
  }

  private String canonicalLinks(List<EvidenceLink> links) {
    return links.stream()
        .sorted(Comparator.comparing(EvidenceLink::evidenceId))
        .map(link -> link.evidenceId() + ":" + link.evidenceType() + ":" + link.reference() + ":" + link.hash())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String canonicalMap(Map<String, String> values) {
    return values.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + "," + right)
        .orElse("");
  }

  private String nullToBlank(String value) {
    return value == null ? "" : value;
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }
}

record ConfigHistoryQuery(
    String tenantId,
    String actorId,
    List<String> permissions,
    ConfigHistoryFilter filter,
    RedactionPolicyRef redactionPolicy,
    List<ConfigHistoryEntry> entries,
    String correlationId) {}

record ConfigHistoryFilter(
    String artifactId,
    String actorId,
    String eventType,
    String reasonCode,
    String status,
    Instant from,
    Instant to) {}

record RedactionPolicyRef(String policyId, List<String> confidentialKeys, boolean revealConfidentialValues) {}

record ReplayReference(String eventId, String replayHash, String auditRecordId) {}

record EvidenceLink(String evidenceId, String evidenceType, String reference, String hash, boolean exportable) {}

record ConfigHistoryEntry(
    String tenantId,
    String historyId,
    String artifactId,
    String artifactType,
    String versionId,
    String eventType,
    int eventVersion,
    String actorId,
    String actorGroup,
    String statusFrom,
    String statusTo,
    String reasonCode,
    String summary,
    Map<String, String> diffSummary,
    String auditRecordId,
    String outboxEventId,
    ReplayReference replayReference,
    List<EvidenceLink> evidenceLinks,
    Instant occurredAt,
    long sequence,
    boolean redacted) {
  ConfigHistoryEntry withDiffSummary(Map<String, String> redactedDiff, boolean redacted) {
    return new ConfigHistoryEntry(
        tenantId,
        historyId,
        artifactId,
        artifactType,
        versionId,
        eventType,
        eventVersion,
        actorId,
        actorGroup,
        statusFrom,
        statusTo,
        reasonCode,
        summary,
        redactedDiff,
        auditRecordId,
        outboxEventId,
        replayReference,
        evidenceLinks,
        occurredAt,
        sequence,
        redacted);
  }
}

record ConfigHistorySearchResult(
    String tenantId,
    List<ConfigHistoryEntry> entries,
    Map<String, String> metrics,
    String replayRef,
    String auditRef,
    String eventId,
    String correlationId,
    Instant generatedAt) {}

record ConfigHistoryDetail(ConfigHistoryEntry entry, String correlationId, String replayRef) {}

record ConfigDiffRequest(
    String tenantId,
    String actorId,
    List<String> permissions,
    String fromVersionId,
    String toVersionId,
    RedactionPolicyRef redactionPolicy,
    List<ConfigHistoryEntry> entries,
    String correlationId) {}

record ConfigDiffResult(
    String diffId,
    String tenantId,
    String fromVersionId,
    String toVersionId,
    Map<String, String> diffJsonRedacted,
    String redactionPolicyId,
    String diffHash,
    String correlationId,
    Instant createdAt) {}

record ConfigEvidenceRequest(
    String tenantId,
    String historyId,
    String actorId,
    List<String> permissions,
    List<ConfigHistoryEntry> entries,
    String correlationId) {}

record ConfigEvidencePackage(
    String exportId,
    String tenantId,
    String historyId,
    List<EvidenceLink> evidenceLinks,
    String replayHash,
    String correlationId,
    Instant exportedAt) {}
