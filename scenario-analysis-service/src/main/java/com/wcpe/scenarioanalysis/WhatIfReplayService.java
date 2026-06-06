package com.wcpe.scenarioanalysis;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class WhatIfReplayService {
  static final String PACKAGE_SCHEMA_VERSION = "what-if-replay-package-v1";
  private static final Set<String> SOURCE_TYPES = Set.of("saved-analysis", "variant", "batch-grid", "export");
  private static final Set<String> MODES = Set.of("same versions", "current versions comparison");
  private static final Set<String> REQUIRED_VERSION_TYPES = Set.of("pricing", "eligibility", "product", "mi", "lock", "engine");

  private final ReplayRepository repository;
  private final Clock clock;

  public WhatIfReplayService() {
    this(new InMemoryReplayRepository(), Clock.systemUTC());
  }

  WhatIfReplayService(ReplayRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public ReplayResponse runReplay(CreateReplayCommand command) {
    CreateReplayCommand valid = validate(command);
    String idempotencyKeyHash = sha256Hex(valid.idempotencyKey());
    String requestHash = sha256Hex(canonicalRequest(valid));
    Optional<StoredReplay> existing = repository.findByIdempotencyKeyHash(valid.tenantId(), idempotencyKeyHash);
    if (existing.isPresent()) {
      StoredReplay stored = existing.get();
      if (!stored.requestHash().equals(requestHash)) {
        throw new IdempotencyConflictException("idempotency key was already used with a different replay request");
      }
      return stored.response();
    }

    UUID replayId = UUID.randomUUID();
    Instant now = Instant.now(clock);
    String replayHash = replayHash(valid.replayPackage());
    MismatchResult mismatch = classify(valid, replayHash);
    String status = mismatch.failed() ? "FAILED" : "COMPLETED";
    List<String> eventTypes = eventTypes(mismatch);
    String auditRef = auditRef("WHAT_IF_REPLAY_COMPLETED", replayId, now);
    String evidenceJson = evidenceJson(valid, replayId, replayHash, mismatch, auditRef, eventTypes, now);
    ReplayResponse response = new ReplayResponse(
        replayId,
        status,
        valid.sourceType(),
        valid.sourceId(),
        valid.mode(),
        valid.replayPackage().originalResultHash(),
        replayHash,
        mismatch.category(),
        mismatch.summary(),
        mismatch.diffs(),
        versionSummary(valid),
        auditRef,
        "replay:what-if-replay:" + replayHash.substring("sha256:".length()),
        evidenceJson,
        eventTypes,
        valid.correlationId(),
        now);
    repository.save(new StoredReplay(
        valid.tenantId(),
        replayId,
        requestHash,
        idempotencyKeyHash,
        valid.actorId(),
        response,
        List.of(new ReplayEvent(UUID.randomUUID(), "whatif.replay.requested.v1", valid.tenantId(), replayId, valid.actorId(), valid.correlationId(), valid.causationId(), valid.idempotencyKey(), now)),
        now));
    return response;
  }

  public ReplayResponse getReplay(String tenantId, UUID replayId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (replayId == null) {
      throw new ValidationException("replayId is required");
    }
    return repository.findByReplayId(normalizedTenantId, replayId)
        .map(StoredReplay::response)
        .orElseThrow(() -> new NotFoundException("what-if replay was not found"));
  }

  private CreateReplayCommand validate(CreateReplayCommand command) {
    if (command == null) {
      throw new ValidationException("replay request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    String sourceType = requireText(command.sourceType(), "sourceType is required").toLowerCase();
    if (!SOURCE_TYPES.contains(sourceType)) {
      throw new ValidationException("sourceType must be saved-analysis, variant, batch-grid, or export");
    }
    String sourceId = requireText(command.sourceId(), "sourceId is required");
    String mode = requireText(command.mode(), "mode is required").toLowerCase();
    if (!MODES.contains(mode)) {
      throw new ValidationException("mode must be same versions or current versions comparison");
    }
    String reasonCode = requireText(command.reasonCode(), "reasonCode is required");
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    ReplayPackage replayPackage = validatePackage(command.replayPackage());
    return new CreateReplayCommand(
        tenantId,
        sourceType,
        sourceId,
        mode,
        reasonCode,
        command.includeLedger(),
        command.toleranceProfileId(),
        replayPackage,
        command.currentVersionRefs() == null ? List.of() : List.copyOf(command.currentVersionRefs()),
        command.replayDiffs() == null ? List.of() : List.copyOf(command.replayDiffs()),
        idempotencyKey,
        actorId,
        correlationId,
        causationId);
  }

  private ReplayPackage validatePackage(ReplayPackage replayPackage) {
    if (replayPackage == null) {
      throw new ReplayPackageIncompleteException("replayPackage is required");
    }
    if (!PACKAGE_SCHEMA_VERSION.equals(replayPackage.schemaVersion())) {
      throw new ReplayPackageIncompleteException("schemaVersion must be " + PACKAGE_SCHEMA_VERSION);
    }
    requireText(replayPackage.inputSnapshotHash(), "inputSnapshotHash is required");
    requireText(replayPackage.changeSetHash(), "changeSetHash is required");
    requireText(replayPackage.calculationLedgerHash(), "calculationLedgerHash is required");
    requireText(replayPackage.originalResultHash(), "originalResultHash is required");
    List<VersionRef> versionRefs = replayPackage.versionRefs() == null ? List.of() : List.copyOf(replayPackage.versionRefs());
    LinkedHashSet<String> presentTypes = new LinkedHashSet<>();
    for (VersionRef ref : versionRefs) {
      if (ref == null) {
        throw new ReplayPackageIncompleteException("versionRefs cannot contain null entries");
      }
      String type = requireText(ref.type(), "versionRef type is required").toLowerCase();
      String version = requireText(ref.version(), "versionRef version is required");
      if (version.equalsIgnoreCase("unavailable") || version.equalsIgnoreCase("missing")) {
        throw new ReplayVersionUnavailableException("archived " + type + " version is unavailable");
      }
      presentTypes.add(type);
    }
    List<String> missing = REQUIRED_VERSION_TYPES.stream()
        .filter(type -> !presentTypes.contains(type))
        .toList();
    if (!missing.isEmpty()) {
      throw new ReplayPackageIncompleteException("replayPackage is missing required version refs: " + missing);
    }
    List<String> eventIds = collapseText(replayPackage.eventIds());
    if (eventIds.isEmpty()) {
      throw new ReplayPackageIncompleteException("eventIds are required");
    }
    return new ReplayPackage(
        replayPackage.schemaVersion(),
        replayPackage.inputSnapshotHash().trim(),
        replayPackage.changeSetHash().trim(),
        versionRefs,
        replayPackage.calculationLedgerHash().trim(),
        eventIds,
        replayPackage.originalResultHash().trim());
  }

  private static MismatchResult classify(CreateReplayCommand command, String replayHash) {
    List<ReplayDiff> materialDiffs = command.replayDiffs() == null ? List.of() : command.replayDiffs();
    boolean sameHash = command.replayPackage().originalResultHash().equals(replayHash);
    if ("current versions comparison".equals(command.mode()) && configChanged(command.replayPackage().versionRefs(), command.currentVersionRefs())) {
      return new MismatchResult(false, "CONFIG_CHANGED", "current-version replay used different configuration versions", materialDiffs);
    }
    if (!materialDiffs.isEmpty() && materialDiffs.stream().allMatch(WhatIfReplayService::withinTolerance)) {
      return new MismatchResult(false, "ROUNDING_DRIFT", "replay differences are within the selected numeric tolerance profile", materialDiffs);
    }
    if (sameHash) {
      return new MismatchResult(false, "MATCH", "replay hash matches original result hash", List.of());
    }
    return new MismatchResult(false, "DATA_CORRUPTION", "same-version replay hash did not match the captured original hash", materialDiffs);
  }

  private static boolean configChanged(List<VersionRef> originalRefs, List<VersionRef> currentRefs) {
    if (currentRefs == null || currentRefs.isEmpty()) {
      return false;
    }
    Map<String, String> originalByType = new LinkedHashMap<>();
    for (VersionRef ref : originalRefs) {
      originalByType.put(ref.type().toLowerCase(), ref.version());
    }
    for (VersionRef ref : currentRefs) {
      if (ref != null && !Objects.equals(originalByType.get(defaultText(ref.type(), "").toLowerCase()), ref.version())) {
        return true;
      }
    }
    return false;
  }

  private static boolean withinTolerance(ReplayDiff diff) {
    if (diff == null || diff.originalValue() == null || diff.replayValue() == null) {
      return false;
    }
    BigDecimal delta = diff.originalValue().subtract(diff.replayValue()).abs();
    return switch (defaultText(diff.metricType(), "").toLowerCase()) {
      case "money" -> delta.compareTo(new BigDecimal("0.01")) <= 0;
      case "rate", "points" -> delta.compareTo(new BigDecimal("0.00001")) <= 0;
      case "bps" -> delta.compareTo(new BigDecimal("0.1")) <= 0;
      default -> false;
    };
  }

  static String replayHash(ReplayPackage replayPackage) {
    String canonical = replayPackage.schemaVersion() + '|' + replayPackage.inputSnapshotHash() + '|'
        + replayPackage.changeSetHash() + '|' + replayPackage.versionRefs() + '|'
        + replayPackage.calculationLedgerHash() + '|' + replayPackage.eventIds();
    return "sha256:" + sha256Hex(canonical);
  }

  private static List<String> eventTypes(MismatchResult mismatch) {
    List<String> events = new ArrayList<>();
    events.add("whatif.replay.requested.v1");
    events.add("whatif.replay.completed.v1");
    if (!"MATCH".equals(mismatch.category())) {
      events.add("whatif.replay.mismatch_detected.v1");
    }
    return List.copyOf(events);
  }

  private static List<VersionRef> versionSummary(CreateReplayCommand command) {
    if ("current versions comparison".equals(command.mode()) && command.currentVersionRefs() != null && !command.currentVersionRefs().isEmpty()) {
      return List.copyOf(command.currentVersionRefs());
    }
    return List.copyOf(command.replayPackage().versionRefs());
  }

  private static String evidenceJson(CreateReplayCommand command, UUID replayId, String replayHash, MismatchResult mismatch, String auditRef, List<String> eventTypes, Instant generatedAt) {
    StringBuilder builder = new StringBuilder();
    builder.append('{')
        .append("\"manifest\":{")
        .append("\"schemaVersion\":\"what-if-replay-evidence-v1\",")
        .append("\"replayId\":\"").append(replayId).append("\",")
        .append("\"sourceType\":\"").append(jsonEscape(command.sourceType())).append("\",")
        .append("\"sourceId\":\"").append(jsonEscape(command.sourceId())).append("\",")
        .append("\"mode\":\"").append(jsonEscape(command.mode())).append("\",")
        .append("\"generatedAt\":\"").append(generatedAt).append("\"},")
        .append("\"hashes\":{")
        .append("\"original\":\"").append(jsonEscape(command.replayPackage().originalResultHash())).append("\",")
        .append("\"replay\":\"").append(jsonEscape(replayHash)).append("\"},")
        .append("\"mismatch\":{")
        .append("\"category\":\"").append(jsonEscape(mismatch.category())).append("\",")
        .append("\"summary\":\"").append(jsonEscape(mismatch.summary())).append("\"},")
        .append("\"audit\":{")
        .append("\"reasonCode\":\"").append(jsonEscape(command.reasonCode())).append("\",")
        .append("\"actorId\":\"").append(jsonEscape(command.actorId())).append("\",")
        .append("\"correlationId\":\"").append(jsonEscape(command.correlationId())).append("\",")
        .append("\"auditRef\":\"").append(jsonEscape(auditRef)).append("\"},")
        .append("\"eventTypes\":[").append(jsonArray(eventTypes)).append("],")
        .append("\"diffs\":[").append(diffJson(mismatch.diffs())).append("],")
        .append("\"privacy\":\"replay evidence omits raw borrower PII and source input snapshots\"")
        .append('}');
    return builder.toString();
  }

  private static String diffJson(List<ReplayDiff> diffs) {
    if (diffs == null || diffs.isEmpty()) {
      return "";
    }
    List<String> encoded = new ArrayList<>();
    for (ReplayDiff diff : diffs) {
      encoded.add("{\"path\":\"" + jsonEscape(diff.path()) + "\",\"category\":\"" + jsonEscape(diff.category()) + "\",\"metricType\":\"" + jsonEscape(diff.metricType()) + "\",\"tolerance\":\"" + jsonEscape(diff.tolerance()) + "\"}");
    }
    return String.join(",", encoded);
  }

  private static String canonicalRequest(CreateReplayCommand command) {
    return command.tenantId() + '|' + command.sourceType() + '|' + command.sourceId() + '|' + command.mode() + '|'
        + command.reasonCode() + '|' + command.includeLedger() + '|' + command.toleranceProfileId() + '|'
        + command.replayPackage() + '|' + command.currentVersionRefs() + '|' + command.replayDiffs() + '|' + command.actorId();
  }

  private static List<String> collapseText(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> collapsed = new LinkedHashSet<>();
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        collapsed.add(value.trim());
      }
    }
    return List.copyOf(collapsed);
  }

  private static String auditRef(String action, UUID replayId, Instant occurredAt) {
    return "audit:" + action + ':' + replayId + ':' + occurredAt;
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(message);
    }
    return value.trim();
  }

  private static String defaultText(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private static String jsonArray(List<String> values) {
    return values.stream().map(value -> "\"" + jsonEscape(value) + "\"").reduce((left, right) -> left + "," + right).orElse("");
  }

  private static String jsonEscape(String value) {
    return defaultText(value, "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }

  private static String sha256Hex(String value) {
    return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(bytes);
      StringBuilder hex = new StringBuilder(hashed.length * 2);
      for (byte b : hashed) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  public record CreateReplayCommand(
      String tenantId,
      String sourceType,
      String sourceId,
      String mode,
      String reasonCode,
      boolean includeLedger,
      String toleranceProfileId,
      ReplayPackage replayPackage,
      List<VersionRef> currentVersionRefs,
      List<ReplayDiff> replayDiffs,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record ReplayPackage(
      String schemaVersion,
      String inputSnapshotHash,
      String changeSetHash,
      List<VersionRef> versionRefs,
      String calculationLedgerHash,
      List<String> eventIds,
      String originalResultHash) {}

  public record VersionRef(String type, String version) {}

  public record ReplayDiff(
      String path,
      String metricType,
      BigDecimal originalValue,
      BigDecimal replayValue,
      String tolerance,
      String category) {}

  public record ReplayResponse(
      UUID replayId,
      String status,
      String sourceType,
      String sourceId,
      String mode,
      String originalHash,
      String replayHash,
      String mismatchCategory,
      String mismatchSummary,
      List<ReplayDiff> diffs,
      List<VersionRef> pricingConfigVersions,
      String auditRef,
      String replayRef,
      String evidenceJson,
      List<String> eventTypes,
      String correlationId,
      Instant createdAt) {}

  private record MismatchResult(boolean failed, String category, String summary, List<ReplayDiff> diffs) {}

  public record ReplayEvent(
      UUID eventId,
      String eventType,
      String tenantId,
      UUID replayId,
      String actorId,
      String correlationId,
      String causationId,
      String idempotencyKey,
      Instant occurredAt) {}

  public record StoredReplay(
      String tenantId,
      UUID replayId,
      String requestHash,
      String idempotencyKeyHash,
      String createdBy,
      ReplayResponse response,
      List<ReplayEvent> events,
      Instant createdAt) {}

  public interface ReplayRepository {
    Optional<StoredReplay> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash);

    Optional<StoredReplay> findByReplayId(String tenantId, UUID replayId);

    void save(StoredReplay replay);
  }

  public static class InMemoryReplayRepository implements ReplayRepository {
    private final Map<String, StoredReplay> replaysByTenantAndId = new ConcurrentHashMap<>();
    private final Map<String, StoredReplay> replaysByTenantAndIdempotency = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredReplay> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
      return Optional.ofNullable(replaysByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
    }

    @Override
    public Optional<StoredReplay> findByReplayId(String tenantId, UUID replayId) {
      return Optional.ofNullable(replaysByTenantAndId.get(key(tenantId, replayId.toString())));
    }

    @Override
    public void save(StoredReplay replay) {
      replaysByTenantAndId.put(key(replay.tenantId(), replay.replayId().toString()), replay);
      replaysByTenantAndIdempotency.put(key(replay.tenantId(), replay.idempotencyKeyHash()), replay);
    }

    public int size() {
      return replaysByTenantAndId.size();
    }

    private static String key(String tenantId, String id) {
      return tenantId + ':' + id;
    }
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class ReplayPackageIncompleteException extends RuntimeException {
    public ReplayPackageIncompleteException(String message) {
      super(message);
    }
  }

  public static class ReplayVersionUnavailableException extends RuntimeException {
    public ReplayVersionUnavailableException(String message) {
      super(message);
    }
  }

  public static class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
      super(message);
    }
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }
}
