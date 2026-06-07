package com.wcpe.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RateSheetReviewService {
  public static final String QUEUE_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/rate-sheets";
  public static final String REVIEW_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/rate-sheets/{versionId}/review";
  public static final String REVIEW_NOTES_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/rate-sheets/{versionId}/review-notes";
  public static final String COMPARE_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/rate-sheets/{versionId}/compare";
  public static final String READ_PERMISSION = "admin.config.read";
  public static final String WRITE_PERMISSION = "admin.config.write";
  public static final String STARTED_EVENT_TYPE = "RateSheetReviewStarted.v1";
  public static final String ANNOTATED_EVENT_TYPE = "RateSheetVarianceAnnotated.v1";
  public static final String AUDIT_ACTION = "RATE_SHEET_REVIEW_UI_COMPLETED";

  private final Clock clock;
  private final Map<String, IdempotencyEntry<?>> idempotencyEntries = new HashMap<>();
  private final Map<String, List<ReviewNote>> notesByTenantAndVersion = new HashMap<>();
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public RateSheetReviewService() {
    this(Clock.systemUTC());
  }

  public RateSheetReviewService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<RateSheetQueueSnapshot> queue(RateSheetQueueRequest request) {
    GovernanceValidationResult<RateSheetQueueRequest> validation = validateQueue(request);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }
    String requestHash = hash(canonicalQueueRequest(request));
    GovernanceValidationResult<RateSheetQueueSnapshot> replay = replay(request.tenantId(), request.idempotencyKey(), requestHash);
    if (replay != null) {
      return replay;
    }

    List<RateSheetHeader> rows =
        request.headers().stream()
            .filter(header -> request.tenantId().equals(header.tenantId()))
            .filter(header -> isBlank(request.filter().status()) || request.filter().status().equals(header.status()))
            .filter(header -> isBlank(request.filter().investorRef()) || request.filter().investorRef().equals(header.investorRef()))
            .filter(header -> isBlank(request.filter().productRef()) || request.filter().productRef().equals(header.productRef()))
            .filter(header -> isBlank(request.filter().channelRef()) || request.filter().channelRef().equals(header.channelRef()))
            .filter(header -> request.filter().effectiveOn() == null || request.filter().effectiveOn().equals(header.effectiveDate()))
            .sorted(Comparator.comparing(RateSheetHeader::updatedAt).reversed().thenComparing(RateSheetHeader::versionId))
            .toList();
    RateSheetQueueSnapshot snapshot =
        new RateSheetQueueSnapshot(request.tenantId(), rows, statusCounts(rows), request.correlationId(), clock.instant());
    idempotencyEntries.put(key(request.tenantId(), request.idempotencyKey()), new IdempotencyEntry<>(requestHash, snapshot));
    return GovernanceValidationResult.success(snapshot);
  }

  public GovernanceValidationResult<RateSheetReviewSnapshot> review(RateSheetReviewRequest request) {
    GovernanceValidationResult<RateSheetReviewRequest> validation = validateReview(request);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }
    String requestHash = hash(canonicalReviewRequest(request));
    GovernanceValidationResult<RateSheetReviewSnapshot> replay = replay(request.header().tenantId(), request.idempotencyKey(), requestHash);
    if (replay != null) {
      return replay;
    }

    List<RateSheetRow> tenantRows =
        request.rows().stream()
            .filter(row -> request.header().tenantId().equals(row.tenantId()))
            .filter(row -> request.header().versionId().equals(row.versionId()))
            .sorted(Comparator.comparing(RateSheetRow::rowId))
            .toList();
    List<RateSheetRow> page = page(tenantRows, request.page());
    List<VarianceFinding> findings = sortedFindings(request.findings(), request.header().tenantId(), request.header().versionId());
    List<String> missingMappingRows = tenantRows.stream().filter(row -> !"MAPPED".equals(row.mappingStatus())).map(RateSheetRow::rowId).sorted().toList();
    boolean requiredNoteMissing = requiredNoteMissing(findings, request.policy(), notesByTenantAndVersion.getOrDefault(versionKey(request.header().tenantId(), request.header().versionId()), List.of()));
    boolean publishBlocked = !missingMappingRows.isEmpty() || requiredNoteMissing || findings.stream().anyMatch(VarianceFinding::blocking);
    String replayRef = hash(canonicalRowHashes(tenantRows) + "|" + canonicalFindings(findings) + "|" + request.policy().policyVersionId());
    String auditRef = deterministicId(request.header().tenantId(), request.header().versionId(), request.actorId(), "review-audit");
    String eventId = deterministicId(request.header().tenantId(), request.header().versionId(), request.correlationId(), "review-started");
    RateSheetReviewSnapshot snapshot =
        new RateSheetReviewSnapshot(
            request.header(),
            page,
            request.page(),
            new VarianceSummary(findings.size(), findings.stream().filter(VarianceFinding::blocking).count(), missingMappingRows.size(), requiredNoteMissing),
            findings,
            request.checklist().withPublishBlocked(publishBlocked),
            missingMappingRows,
            permissions(request.permissions()),
            auditRef,
            eventId,
            replayRef,
            request.correlationId(),
            clock.instant());

    outboxEvents.add(
        new ConfigApiOutboxEvent(
            eventId,
            STARTED_EVENT_TYPE,
            1,
            request.header().tenantId(),
            request.header().artifactId(),
            request.header().versionId(),
            request.actorId(),
            request.correlationId(),
            request.correlationId(),
            request.idempotencyKey(),
            clock.instant(),
            Map.of(
                "versionId", request.header().versionId(),
                "rowCount", Integer.toString(tenantRows.size()),
                "rowHashSet", hash(canonicalRowHashes(tenantRows)),
                "findingCount", Integer.toString(findings.size()),
                "policyVersionId", request.policy().policyVersionId(),
                "replayRef", replayRef)));
    auditRecords.add(
        new ConfigApiAuditRecord(
            auditRef,
            request.header().tenantId(),
            request.header().artifactId(),
            request.header().versionId(),
            request.actorId(),
            AUDIT_ACTION,
            "rate-sheet-review-requested:" + request.header().status(),
            "rate-sheet-review-ready:" + replayRef,
            request.correlationId(),
            clock.instant()));
    idempotencyEntries.put(key(request.header().tenantId(), request.idempotencyKey()), new IdempotencyEntry<>(requestHash, snapshot));
    return GovernanceValidationResult.success(snapshot);
  }

  public GovernanceValidationResult<ReviewNote> addNote(ReviewNoteCommand command) {
    GovernanceValidationResult<ReviewNoteCommand> validation = validateNote(command);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }
    String requestHash = hash(canonicalNoteCommand(command));
    GovernanceValidationResult<ReviewNote> replay = replay(command.tenantId(), command.idempotencyKey(), requestHash);
    if (replay != null) {
      return replay;
    }

    ReviewNote note =
        new ReviewNote(
            deterministicId(command.tenantId(), command.versionId(), command.rowId() == null ? "global" : command.rowId(), command.findingCode(), command.actorId()),
            command.tenantId(),
            command.versionId(),
            command.rowId(),
            command.findingCode(),
            command.reasonCode(),
            command.comment(),
            command.actorId(),
            clock.instant());
    notesByTenantAndVersion.computeIfAbsent(versionKey(command.tenantId(), command.versionId()), ignored -> new ArrayList<>()).add(note);
    String eventId = deterministicId(command.tenantId(), command.versionId(), command.findingCode(), command.correlationId(), "variance-note");
    outboxEvents.add(
        new ConfigApiOutboxEvent(
            eventId,
            ANNOTATED_EVENT_TYPE,
            1,
            command.tenantId(),
            command.artifactId(),
            command.versionId(),
            command.actorId(),
            command.correlationId(),
            note.noteId(),
            command.idempotencyKey(),
            clock.instant(),
            Map.of(
                "versionId", command.versionId(),
                "rowId", command.rowId() == null ? "global" : command.rowId(),
                "findingCode", command.findingCode(),
                "reasonCode", command.reasonCode(),
                "commentHash", hash(command.comment()))));
    idempotencyEntries.put(key(command.tenantId(), command.idempotencyKey()), new IdempotencyEntry<>(requestHash, note));
    return GovernanceValidationResult.success(note);
  }

  public GovernanceValidationResult<RateSheetComparison> compare(RateSheetCompareRequest request) {
    GovernanceValidationResult<RateSheetCompareRequest> validation = validateCompare(request);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }
    Map<String, RateSheetRow> priorByRow = rowsById(request.priorRows(), request.tenantId(), request.priorVersionId());
    Map<String, RateSheetRow> currentByRow = rowsById(request.currentRows(), request.tenantId(), request.currentVersionId());
    Set<String> rowIds = new HashSet<>();
    rowIds.addAll(priorByRow.keySet());
    rowIds.addAll(currentByRow.keySet());
    List<RowComparison> comparisons =
        rowIds.stream()
            .sorted()
            .map(rowId -> compare(rowId, priorByRow.get(rowId), currentByRow.get(rowId)))
            .toList();
    RateSheetComparison comparison =
        new RateSheetComparison(
            request.tenantId(),
            request.currentVersionId(),
            request.priorVersionId(),
            comparisons,
            comparisons.stream().filter(row -> !"UNCHANGED".equals(row.status())).count(),
            hash(canonicalComparisons(comparisons)),
            request.correlationId(),
            clock.instant());
    return GovernanceValidationResult.success(comparison);
  }

  public List<ReviewNote> notes(String tenantId, String versionId) {
    return List.copyOf(notesByTenantAndVersion.getOrDefault(versionKey(tenantId, versionId), List.of()));
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  @SuppressWarnings("unchecked")
  private <T> GovernanceValidationResult<T> replay(String tenantId, String idempotencyKey, String requestHash) {
    IdempotencyEntry<?> existing = idempotencyEntries.get(key(tenantId, idempotencyKey));
    if (existing == null) {
      return null;
    }
    if (!existing.requestHash().equals(requestHash)) {
      return GovernanceValidationResult.failure("IDEMPOTENCY_CONFLICT");
    }
    return GovernanceValidationResult.success((T) existing.value());
  }

  private GovernanceValidationResult<RateSheetQueueRequest> validateQueue(RateSheetQueueRequest request) {
    if (request == null || !isUuid(request.tenantId()) || isBlank(request.idempotencyKey()) || isBlank(request.actorId()) || isBlank(request.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenant, idempotency, actor, and correlation are required");
    }
    if (request.permissions() == null || !request.permissions().contains(READ_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (request.headers() == null || request.filter() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: headers and filter are required");
    }
    return GovernanceValidationResult.success(request);
  }

  private GovernanceValidationResult<RateSheetReviewRequest> validateReview(RateSheetReviewRequest request) {
    if (request == null || request.header() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: review header is required");
    }
    if (!isUuid(request.header().tenantId()) || isBlank(request.idempotencyKey()) || isBlank(request.actorId()) || isBlank(request.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenant, idempotency, actor, and correlation are required");
    }
    if (request.permissions() == null || !request.permissions().contains(READ_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (request.policy() == null || isBlank(request.policy().policyVersionId()) || !request.policy().varianceTolerancesConfigured()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: rate sheet review policy and tolerance configuration are required");
    }
    if (request.checklist() == null || isBlank(request.checklist().checklistVersion()) || request.checklist().items().isEmpty()) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: review checklist metadata is required");
    }
    if (request.rows() == null || request.rows().isEmpty() || request.findings() == null || request.page() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: rows, findings, and page are required");
    }
    return GovernanceValidationResult.success(request);
  }

  private GovernanceValidationResult<ReviewNoteCommand> validateNote(ReviewNoteCommand command) {
    if (command == null || !isUuid(command.tenantId()) || isBlank(command.versionId()) || isBlank(command.artifactId()) || isBlank(command.idempotencyKey())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenant, artifact, version, and idempotency are required");
    }
    if (command.permissions() == null || !command.permissions().contains(WRITE_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (isBlank(command.findingCode()) || isBlank(command.reasonCode()) || isBlank(command.comment()) || isBlank(command.actorId()) || isBlank(command.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: finding, reason, comment, actor, and correlation are required");
    }
    return GovernanceValidationResult.success(command);
  }

  private GovernanceValidationResult<RateSheetCompareRequest> validateCompare(RateSheetCompareRequest request) {
    if (request == null || !isUuid(request.tenantId()) || isBlank(request.currentVersionId()) || isBlank(request.priorVersionId()) || isBlank(request.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenant, current version, prior version, and correlation are required");
    }
    if (request.permissions() == null || !request.permissions().contains(READ_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (request.currentRows() == null || request.priorRows() == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: current and prior rows are required");
    }
    return GovernanceValidationResult.success(request);
  }

  private List<RateSheetRow> page(List<RateSheetRow> rows, RateSheetPage page) {
    int size = Math.max(1, page.size());
    int start = Math.max(0, page.index()) * size;
    if (start >= rows.size()) {
      return List.of();
    }
    return rows.subList(start, Math.min(rows.size(), start + size));
  }

  private List<VarianceFinding> sortedFindings(List<VarianceFinding> findings, String tenantId, String versionId) {
    return findings.stream()
        .filter(finding -> tenantId.equals(finding.tenantId()))
        .filter(finding -> versionId.equals(finding.versionId()))
        .sorted(Comparator.comparing(VarianceFinding::blocking).reversed().thenComparing(VarianceFinding::severity).thenComparing(VarianceFinding::rowId))
        .toList();
  }

  private boolean requiredNoteMissing(List<VarianceFinding> findings, RateSheetReviewPolicy policy, List<ReviewNote> notes) {
    Set<String> notedFindingCodes = new HashSet<>();
    for (ReviewNote note : notes) {
      notedFindingCodes.add(note.findingCode());
    }
    return findings.stream()
        .filter(finding -> policy.noteRequiredSeverities().contains(finding.severity()))
        .map(VarianceFinding::code)
        .anyMatch(code -> !notedFindingCodes.contains(code));
  }

  private Map<String, String> permissions(List<String> permissions) {
    return Map.of(
        "read", Boolean.toString(permissions.contains(READ_PERMISSION)),
        "annotate", Boolean.toString(permissions.contains(WRITE_PERMISSION)),
        "submit", Boolean.toString(permissions.contains("admin.config.submit")),
        "approve", Boolean.toString(permissions.contains("admin.config.approve")),
        "publish", Boolean.toString(permissions.contains("admin.config.publish")));
  }

  private Map<String, Integer> statusCounts(List<RateSheetHeader> rows) {
    Map<String, Integer> counts = new HashMap<>();
    for (RateSheetHeader row : rows) {
      counts.merge(row.status(), 1, Integer::sum);
    }
    return Map.copyOf(counts);
  }

  private Map<String, RateSheetRow> rowsById(List<RateSheetRow> rows, String tenantId, String versionId) {
    Map<String, RateSheetRow> result = new HashMap<>();
    for (RateSheetRow row : rows) {
      if (tenantId.equals(row.tenantId()) && versionId.equals(row.versionId())) {
        result.put(row.rowId(), row);
      }
    }
    return result;
  }

  private RowComparison compare(String rowId, RateSheetRow prior, RateSheetRow current) {
    if (prior == null) {
      return new RowComparison(rowId, "ADDED", "", current.rowHash(), current.adjustmentHash());
    }
    if (current == null) {
      return new RowComparison(rowId, "REMOVED", prior.rowHash(), "", prior.adjustmentHash());
    }
    String status = prior.rowHash().equals(current.rowHash()) ? "UNCHANGED" : "CHANGED";
    return new RowComparison(rowId, status, prior.rowHash(), current.rowHash(), current.adjustmentHash());
  }

  private String canonicalQueueRequest(RateSheetQueueRequest request) {
    return String.join("|", request.tenantId(), request.idempotencyKey(), request.actorId(), canonicalList(request.permissions()), canonicalHeaders(request.headers()), canonicalFilter(request.filter()), request.correlationId());
  }

  private String canonicalReviewRequest(RateSheetReviewRequest request) {
    return String.join("|", request.header().tenantId(), request.idempotencyKey(), request.actorId(), canonicalHeader(request.header()), canonicalRowHashes(request.rows()), canonicalFindings(request.findings()), request.policy().policyVersionId(), request.checklist().checklistVersion(), request.correlationId());
  }

  private String canonicalNoteCommand(ReviewNoteCommand command) {
    return String.join("|", command.tenantId(), command.idempotencyKey(), command.actorId(), command.versionId(), command.rowId() == null ? "" : command.rowId(), command.findingCode(), command.reasonCode(), command.comment(), command.correlationId());
  }

  private String canonicalHeaders(List<RateSheetHeader> headers) {
    return headers.stream().sorted(Comparator.comparing(RateSheetHeader::versionId)).map(this::canonicalHeader).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalHeader(RateSheetHeader header) {
    return String.join("|", header.tenantId(), header.artifactId(), header.versionId(), header.investorRef(), header.productRef(), header.channelRef(), header.effectiveDate().toString(), header.status(), header.rowHashSet());
  }

  private String canonicalRowHashes(List<RateSheetRow> rows) {
    return rows.stream().sorted(Comparator.comparing(RateSheetRow::rowId)).map(row -> row.rowId() + ":" + row.rowHash() + ":" + row.mappingStatus()).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalFindings(List<VarianceFinding> findings) {
    return findings.stream().sorted(Comparator.comparing(VarianceFinding::code).thenComparing(VarianceFinding::rowId)).map(finding -> finding.rowId() + ":" + finding.code() + ":" + finding.severity() + ":" + finding.blocking()).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalComparisons(List<RowComparison> comparisons) {
    return comparisons.stream().map(row -> row.rowId() + ":" + row.status() + ":" + row.priorRowHash() + ":" + row.currentRowHash()).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalFilter(RateSheetQueueFilter filter) {
    return String.join("|", blank(filter.status()), blank(filter.investorRef()), blank(filter.productRef()), blank(filter.channelRef()), filter.effectiveOn() == null ? "" : filter.effectiveOn().toString());
  }

  private String canonicalList(List<String> values) {
    return values == null ? "" : values.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
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

  private String key(String tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private String versionKey(String tenantId, String versionId) {
    return tenantId + ":" + versionId;
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

  private String blank(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private record IdempotencyEntry<T>(String requestHash, T value) {}
}

record RateSheetQueueRequest(String tenantId, String idempotencyKey, String actorId, List<String> permissions, List<RateSheetHeader> headers, RateSheetQueueFilter filter, String correlationId) {}

record RateSheetQueueFilter(String status, String investorRef, String productRef, String channelRef, LocalDate effectiveOn) {}

record RateSheetQueueSnapshot(String tenantId, List<RateSheetHeader> rows, Map<String, Integer> statusCounts, String correlationId, Instant generatedAt) {}

record RateSheetReviewRequest(String idempotencyKey, String actorId, List<String> permissions, RateSheetHeader header, List<RateSheetRow> rows, List<VarianceFinding> findings, RateSheetReviewChecklist checklist, RateSheetReviewPolicy policy, RateSheetPage page, String correlationId) {}

record RateSheetHeader(String tenantId, String artifactId, String versionId, String investorRef, String productRef, String channelRef, LocalDate effectiveDate, String status, int version, String rowHashSet, Instant updatedAt) {}

record RateSheetRow(String tenantId, String versionId, String rowId, String productRef, String investorRef, String channelRef, String lockPeriod, String declaredRate, String declaredPrice, String adjustmentHash, String rowHash, String mappingStatus) {}

record VarianceFinding(String tenantId, String versionId, String rowId, String code, String severity, String messageKey, boolean blocking) {}

record RateSheetReviewChecklist(String checklistVersion, List<String> items, boolean publishBlocked) {
  RateSheetReviewChecklist withPublishBlocked(boolean blocked) {
    return new RateSheetReviewChecklist(checklistVersion, List.copyOf(items), blocked);
  }
}

record RateSheetReviewPolicy(String policyVersionId, boolean varianceTolerancesConfigured, List<String> noteRequiredSeverities) {}

record RateSheetPage(int index, int size) {}

record VarianceSummary(int findingCount, long blockingFindingCount, int missingMappingRowCount, boolean requiredNoteMissing) {}

record RateSheetReviewSnapshot(RateSheetHeader header, List<RateSheetRow> rowsPage, RateSheetPage page, VarianceSummary varianceSummary, List<VarianceFinding> findings, RateSheetReviewChecklist checklist, List<String> mappingExceptionRowIds, Map<String, String> permissions, String auditRef, String eventId, String replayRef, String correlationId, Instant generatedAt) {}

record ReviewNoteCommand(String tenantId, String artifactId, String versionId, String idempotencyKey, String actorId, List<String> permissions, String rowId, String findingCode, String reasonCode, String comment, String correlationId) {}

record ReviewNote(String noteId, String tenantId, String versionId, String rowId, String findingCode, String reasonCode, String comment, String actorId, Instant createdAt) {}

record RateSheetCompareRequest(String tenantId, String currentVersionId, String priorVersionId, List<String> permissions, List<RateSheetRow> currentRows, List<RateSheetRow> priorRows, String correlationId) {}

record RateSheetComparison(String tenantId, String currentVersionId, String priorVersionId, List<RowComparison> rows, long changedRowCount, String replayRef, String correlationId, Instant generatedAt) {}

record RowComparison(String rowId, String status, String priorRowHash, String currentRowHash, String adjustmentHash) {}
