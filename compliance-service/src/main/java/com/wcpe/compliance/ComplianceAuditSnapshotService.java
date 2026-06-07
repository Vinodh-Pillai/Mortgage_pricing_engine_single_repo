package com.wcpe.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ComplianceAuditSnapshotService {
  public static final String CREATED_EVENT_TYPE = "ComplianceAuditSnapshotCreated.v1";
  public static final String VERIFIED_EVENT_TYPE = "ComplianceAuditSnapshotVerified.v1";
  public static final String LEGAL_HOLD_PLACED_EVENT_TYPE = "ComplianceAuditSnapshotLegalHoldPlaced.v1";
  public static final String LEGAL_HOLD_RELEASED_EVENT_TYPE = "ComplianceAuditSnapshotLegalHoldReleased.v1";
  public static final String SNAPSHOT_CREATED = "snapshot_created";
  public static final String SNAPSHOT_VERIFIED = "snapshot_verified";
  public static final String HASH_MISMATCH = "HASH_MISMATCH";
  public static final String REDACTION_REQUIRED = "REDACTION_REQUIRED";
  public static final String LEGAL_HOLD_ACTIVE = "legal_hold_active";
  public static final String LEGAL_HOLD_RELEASED = "legal_hold_released";
  public static final String NO_LEGAL_HOLD = "no_legal_hold";

  private ComplianceAuditSnapshotService() {}

  public static ComplianceAuditSnapshot createSnapshot(CreateComplianceAuditSnapshot request) {
    validateCreateRequest(request);
    List<SnapshotEvidenceItem> items = sortedItems(request.items());
    String payloadHash = payloadHash(items);
    String resultHash = resultHash(request, items, payloadHash);
    return new ComplianceAuditSnapshot(
        request.snapshotId(),
        request.tenantId(),
        request.subjectRef(),
        request.snapshotType(),
        request.asOfTimestamp(),
        request.configVersionGraph(),
        request.eventSequenceRefs(),
        request.calculationLedgerRefs(),
        request.reasonCodeRefs(),
        items,
        payloadHash,
        resultHash,
        request.retentionPolicyRef(),
        NO_LEGAL_HOLD,
        request.createdByService(),
        request.actorId(),
        request.idempotencyKey(),
        request.correlationId(),
        "compliance-audit-snapshot:sha256:" + resultHash.substring("sha256:".length()),
        List.of(CREATED_EVENT_TYPE),
        List.of());
  }

  public static SnapshotVerificationResult verify(ComplianceAuditSnapshot snapshot) {
    validateSnapshot(snapshot);
    String payloadHash = payloadHash(snapshot.items());
    String resultHash = resultHash(snapshot, payloadHash);
    boolean matches = Objects.equals(payloadHash, snapshot.payloadHash())
        && Objects.equals(resultHash, snapshot.resultHash());
    return new SnapshotVerificationResult(
        snapshot.snapshotId(),
        snapshot.tenantId(),
        matches ? SNAPSHOT_VERIFIED : HASH_MISMATCH,
        payloadHash,
        resultHash,
        matches ? List.of() : mismatchReasons(snapshot, payloadHash, resultHash),
        "compliance-audit-snapshot-verify:" + resultHash,
        List.of(VERIFIED_EVENT_TYPE),
        snapshot.correlationId());
  }

  public static ComplianceAuditSnapshot placeLegalHold(
      ComplianceAuditSnapshot snapshot, LegalHoldCommand command) {
    validateLegalHoldCommand(snapshot, command);
    if (LEGAL_HOLD_ACTIVE.equals(snapshot.legalHoldState())) {
      throw new ComplianceShellValidationError(
          "Compliance audit legal hold validation failed.", List.of("LEGAL_HOLD_CONFLICT"));
    }
    LegalHoldAction action =
        new LegalHoldAction(
            LEGAL_HOLD_ACTIVE,
            command.actorId().trim(),
            command.reason().trim(),
            command.occurredAt(),
            command.correlationId());
    return withLegalHold(snapshot, LEGAL_HOLD_ACTIVE, action, LEGAL_HOLD_PLACED_EVENT_TYPE);
  }

  public static ComplianceAuditSnapshot releaseLegalHold(
      ComplianceAuditSnapshot snapshot, LegalHoldCommand command) {
    validateLegalHoldCommand(snapshot, command);
    if (!LEGAL_HOLD_ACTIVE.equals(snapshot.legalHoldState())) {
      throw new ComplianceShellValidationError(
          "Compliance audit legal hold validation failed.", List.of("LEGAL_HOLD_CONFLICT"));
    }
    LegalHoldAction action =
        new LegalHoldAction(
            LEGAL_HOLD_RELEASED,
            command.actorId().trim(),
            command.reason().trim(),
            command.occurredAt(),
            command.correlationId());
    return withLegalHold(snapshot, LEGAL_HOLD_RELEASED, action, LEGAL_HOLD_RELEASED_EVENT_TYPE);
  }

  public static ComplianceAuditSnapshotView viewForRole(
      ComplianceAuditSnapshot snapshot, String actorRole) {
    validateSnapshot(snapshot);
    boolean privileged = actorRole != null && actorRole.equalsIgnoreCase("compliance-manager");
    List<SnapshotEvidenceViewItem> visibleItems =
        snapshot.items().stream()
            .map(item -> toViewItem(item, privileged))
            .toList();
    long redactedCount = visibleItems.stream().filter(SnapshotEvidenceViewItem::redacted).count();
    return new ComplianceAuditSnapshotView(
        snapshot.snapshotId(),
        snapshot.tenantId(),
        snapshot.subjectRef(),
        snapshot.snapshotType(),
        snapshot.resultHash(),
        snapshot.auditRef(),
        visibleItems,
        redactedCount,
        redactedCount == 0 ? List.of() : List.of(REDACTION_REQUIRED),
        snapshot.correlationId());
  }

  private static ComplianceAuditSnapshot withLegalHold(
      ComplianceAuditSnapshot snapshot, String state, LegalHoldAction action, String eventType) {
    List<LegalHoldAction> actions = new ArrayList<>(snapshot.legalHoldActions());
    actions.add(action);
    return new ComplianceAuditSnapshot(
        snapshot.snapshotId(),
        snapshot.tenantId(),
        snapshot.subjectRef(),
        snapshot.snapshotType(),
        snapshot.asOfTimestamp(),
        snapshot.configVersionGraph(),
        snapshot.eventSequenceRefs(),
        snapshot.calculationLedgerRefs(),
        snapshot.reasonCodeRefs(),
        snapshot.items(),
        snapshot.payloadHash(),
        snapshot.resultHash(),
        snapshot.retentionPolicyRef(),
        state,
        snapshot.createdByService(),
        snapshot.actorId(),
        snapshot.idempotencyKey(),
        snapshot.correlationId(),
        snapshot.auditRef(),
        List.of(eventType),
        actions);
  }

  private static SnapshotEvidenceViewItem toViewItem(SnapshotEvidenceItem item, boolean privileged) {
    boolean redacted = item.sensitive() && !privileged;
    return new SnapshotEvidenceViewItem(
        item.sequence(),
        item.itemType(),
        item.sourceRef(),
        item.schemaVersion(),
        redacted ? "<redacted>" : item.payload(),
        item.payloadHash(),
        item.sensitivityClassification(),
        item.redactionPolicyRef(),
        redacted);
  }

  private static void validateCreateRequest(CreateComplianceAuditSnapshot request) {
    List<String> details = new ArrayList<>();
    if (request == null) {
      details.add("request");
    } else {
      requireNonBlank(request.tenantId(), "tenantId", details);
      requireNonBlank(request.snapshotId(), "snapshotId", details);
      requireNonBlank(request.snapshotType(), "snapshotType", details);
      requireNonBlank(request.createdByService(), "createdByService", details);
      requireNonBlank(request.actorId(), "actorId", details);
      requireNonBlank(request.idempotencyKey(), "idempotencyKey", details);
      requireNonBlank(request.correlationId(), "correlationId", details);
      requireNonBlank(request.retentionPolicyRef(), "retentionPolicyRef", details);
      if (request.asOfTimestamp() == null) {
        details.add("asOfTimestamp must be provided");
      }
      if (request.subjectRef() == null || request.subjectRef().missingRequiredFields()) {
        details.add("subjectRef must include subjectType and subjectId");
      }
      if (request.items() == null || request.items().isEmpty()) {
        details.add("items must include at least one evidence item");
      } else {
        request.items().forEach(item -> validateEvidenceItem(item, details));
      }
    }
    if (!details.isEmpty()) {
      throw new ComplianceShellValidationError("Compliance audit snapshot validation failed.", details);
    }
  }

  private static void validateSnapshot(ComplianceAuditSnapshot snapshot) {
    if (snapshot == null) {
      throw new ComplianceShellValidationError(
          "Compliance audit snapshot validation failed.", List.of("snapshot"));
    }
  }

  private static void validateEvidenceItem(SnapshotEvidenceItem item, List<String> details) {
    if (item == null) {
      details.add("items must not include null values");
      return;
    }
    requireNonBlank(item.itemType(), "itemType", details);
    requireNonBlank(item.sourceRef(), "sourceRef", details);
    requireNonBlank(item.schemaVersion(), "schemaVersion", details);
    requireNonBlank(item.payload(), "payload", details);
    requireNonBlank(item.payloadHash(), "payloadHash", details);
    requireNonBlank(item.sensitivityClassification(), "sensitivityClassification", details);
    requireNonBlank(item.redactionPolicyRef(), "redactionPolicyRef", details);
    if (!Objects.equals(item.payloadHash(), hashPayload(item.payload()))) {
      details.add("payloadHash mismatch for sequence " + item.sequence());
    }
  }

  private static void validateLegalHoldCommand(
      ComplianceAuditSnapshot snapshot, LegalHoldCommand command) {
    validateSnapshot(snapshot);
    List<String> details = new ArrayList<>();
    if (command == null) {
      details.add("command");
    } else {
      requireNonBlank(command.actorId(), "actorId", details);
      requireNonBlank(command.reason(), "reason", details);
      requireNonBlank(command.correlationId(), "correlationId", details);
      if (command.occurredAt() == null) {
        details.add("occurredAt must be provided");
      }
    }
    if (!details.isEmpty()) {
      throw new ComplianceShellValidationError(
          "Compliance audit legal hold validation failed.", details);
    }
  }

  public static String hashPayload(String payload) {
    if (payload == null || payload.isBlank()) {
      throw new ComplianceShellValidationError(
          "Compliance audit snapshot validation failed.", List.of("payload must be a non-empty string"));
    }
    return "sha256:" + sha256("payload|" + payload.trim());
  }

  private static String payloadHash(List<SnapshotEvidenceItem> items) {
    String material =
        sortedItems(items).stream().map(SnapshotEvidenceItem::hashMaterial).reduce("", (a, b) -> a + b);
    return "sha256:" + sha256("schema:v1|items|" + material);
  }

  private static String resultHash(
      CreateComplianceAuditSnapshot request,
      List<SnapshotEvidenceItem> items,
      String payloadHash) {
    String material =
        String.join(
            "|",
            "schema:v1",
            request.tenantId(),
            request.snapshotId(),
            request.subjectRef().hashMaterial(),
            request.snapshotType(),
            request.asOfTimestamp().toString(),
            request.configVersionGraph().hashMaterial(),
            String.join(",", sorted(request.eventSequenceRefs())),
            String.join(",", sorted(request.calculationLedgerRefs())),
            String.join(",", sorted(request.reasonCodeRefs())),
            payloadHash,
            request.retentionPolicyRef(),
            items.stream().map(SnapshotEvidenceItem::hashMaterial).reduce("", (a, b) -> a + b));
    return "sha256:" + sha256(material);
  }

  private static String resultHash(ComplianceAuditSnapshot snapshot, String payloadHash) {
    String material =
        String.join(
            "|",
            "schema:v1",
            snapshot.tenantId(),
            snapshot.snapshotId(),
            snapshot.subjectRef().hashMaterial(),
            snapshot.snapshotType(),
            snapshot.asOfTimestamp().toString(),
            snapshot.configVersionGraph().hashMaterial(),
            String.join(",", sorted(snapshot.eventSequenceRefs())),
            String.join(",", sorted(snapshot.calculationLedgerRefs())),
            String.join(",", sorted(snapshot.reasonCodeRefs())),
            payloadHash,
            snapshot.retentionPolicyRef(),
            sortedItems(snapshot.items()).stream()
                .map(SnapshotEvidenceItem::hashMaterial)
                .reduce("", (a, b) -> a + b));
    return "sha256:" + sha256(material);
  }

  private static List<String> mismatchReasons(
      ComplianceAuditSnapshot snapshot, String payloadHash, String resultHash) {
    List<String> reasons = new ArrayList<>();
    if (!Objects.equals(payloadHash, snapshot.payloadHash())) {
      reasons.add("PAYLOAD_HASH_MISMATCH");
    }
    if (!Objects.equals(resultHash, snapshot.resultHash())) {
      reasons.add("RESULT_HASH_MISMATCH");
    }
    return List.copyOf(reasons);
  }

  private static List<SnapshotEvidenceItem> sortedItems(List<SnapshotEvidenceItem> items) {
    return items == null
        ? List.of()
        : items.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(SnapshotEvidenceItem::sequence))
            .toList();
  }

  private static List<String> sorted(List<String> values) {
    return values == null ? List.of() : values.stream().filter(Objects::nonNull).sorted().toList();
  }

  private static void requireNonBlank(String value, String field, List<String> details) {
    if (value == null || value.trim().isEmpty()) {
      details.add(field + " must be a non-empty string");
    }
  }

  private static String sha256(String material) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
      StringBuilder encoded = new StringBuilder();
      for (byte value : hash) {
        encoded.append(String.format(Locale.ROOT, "%02x", value));
      }
      return encoded.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is required for compliance audit snapshots", exception);
    }
  }

  public record CreateComplianceAuditSnapshot(
      String tenantId,
      String snapshotId,
      SnapshotSubjectRef subjectRef,
      String snapshotType,
      Instant asOfTimestamp,
      ConfigVersionGraph configVersionGraph,
      List<String> eventSequenceRefs,
      List<String> calculationLedgerRefs,
      List<String> reasonCodeRefs,
      List<SnapshotEvidenceItem> items,
      String retentionPolicyRef,
      String createdByService,
      String actorId,
      String idempotencyKey,
      String correlationId) {
    public CreateComplianceAuditSnapshot {
      configVersionGraph = configVersionGraph == null ? ConfigVersionGraph.empty() : configVersionGraph;
      eventSequenceRefs = eventSequenceRefs == null ? List.of() : List.copyOf(eventSequenceRefs);
      calculationLedgerRefs = calculationLedgerRefs == null ? List.of() : List.copyOf(calculationLedgerRefs);
      reasonCodeRefs = reasonCodeRefs == null ? List.of() : List.copyOf(reasonCodeRefs);
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public record SnapshotSubjectRef(String subjectType, String subjectId) {
    boolean missingRequiredFields() {
      return subjectType == null || subjectType.isBlank() || subjectId == null || subjectId.isBlank();
    }

    String hashMaterial() {
      return (subjectType == null ? "" : subjectType.trim()) + ":" + (subjectId == null ? "" : subjectId.trim());
    }
  }

  public record ConfigVersionGraph(List<String> versionRefs) {
    public ConfigVersionGraph {
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
    }

    static ConfigVersionGraph empty() {
      return new ConfigVersionGraph(List.of());
    }

    String hashMaterial() {
      return String.join(",", sorted(versionRefs));
    }
  }

  public record SnapshotEvidenceItem(
      int sequence,
      String itemType,
      String sourceRef,
      String schemaVersion,
      String payload,
      String payloadHash,
      String sensitivityClassification,
      String redactionPolicyRef) {
    boolean sensitive() {
      return sensitivityClassification != null
          && !sensitivityClassification.equalsIgnoreCase("public")
          && !sensitivityClassification.equalsIgnoreCase("internal");
    }

    String hashMaterial() {
      return String.join(
          "|",
          String.valueOf(sequence),
          itemType == null ? "" : itemType.trim(),
          sourceRef == null ? "" : sourceRef.trim(),
          schemaVersion == null ? "" : schemaVersion.trim(),
          payloadHash == null ? "" : payloadHash.trim(),
          sensitivityClassification == null ? "" : sensitivityClassification.trim(),
          redactionPolicyRef == null ? "" : redactionPolicyRef.trim());
    }
  }

  public record ComplianceAuditSnapshot(
      String snapshotId,
      String tenantId,
      SnapshotSubjectRef subjectRef,
      String snapshotType,
      Instant asOfTimestamp,
      ConfigVersionGraph configVersionGraph,
      List<String> eventSequenceRefs,
      List<String> calculationLedgerRefs,
      List<String> reasonCodeRefs,
      List<SnapshotEvidenceItem> items,
      String payloadHash,
      String resultHash,
      String retentionPolicyRef,
      String legalHoldState,
      String createdByService,
      String actorId,
      String idempotencyKey,
      String correlationId,
      String auditRef,
      List<String> outboxEventTypes,
      List<LegalHoldAction> legalHoldActions) {
    public ComplianceAuditSnapshot {
      configVersionGraph = configVersionGraph == null ? ConfigVersionGraph.empty() : configVersionGraph;
      eventSequenceRefs = eventSequenceRefs == null ? List.of() : List.copyOf(eventSequenceRefs);
      calculationLedgerRefs = calculationLedgerRefs == null ? List.of() : List.copyOf(calculationLedgerRefs);
      reasonCodeRefs = reasonCodeRefs == null ? List.of() : List.copyOf(reasonCodeRefs);
      items = items == null ? List.of() : List.copyOf(items);
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
      legalHoldActions = legalHoldActions == null ? List.of() : List.copyOf(legalHoldActions);
    }
  }

  public record SnapshotVerificationResult(
      String snapshotId,
      String tenantId,
      String status,
      String recalculatedPayloadHash,
      String recalculatedResultHash,
      List<String> reasonCodes,
      String auditRef,
      List<String> outboxEventTypes,
      String correlationId) {
    public SnapshotVerificationResult {
      reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }

  public record LegalHoldCommand(
      String actorId, String reason, Instant occurredAt, String correlationId) {}

  public record LegalHoldAction(
      String action, String actorId, String reason, Instant occurredAt, String correlationId) {}

  public record ComplianceAuditSnapshotView(
      String snapshotId,
      String tenantId,
      SnapshotSubjectRef subjectRef,
      String snapshotType,
      String resultHash,
      String auditRef,
      List<SnapshotEvidenceViewItem> items,
      long redactedItemCount,
      List<String> warnings,
      String correlationId) {
    public ComplianceAuditSnapshotView {
      items = items == null ? List.of() : List.copyOf(items);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  public record SnapshotEvidenceViewItem(
      int sequence,
      String itemType,
      String sourceRef,
      String schemaVersion,
      String payload,
      String payloadHash,
      String sensitivityClassification,
      String redactionPolicyRef,
      boolean redacted) {}
}
