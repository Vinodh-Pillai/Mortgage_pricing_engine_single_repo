package com.wcpe.integration;

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
import java.util.Optional;
import java.util.UUID;

public final class DeadLetterReplayService {
  public static final String BASE_PATH = "/api/v1/tenants/{tenantId}/dead-letters";
  public static final String READ_PERMISSION = "integrations.dlq.read";
  public static final String REPLAY_PERMISSION = "integrations.dlq.replay";
  public static final String DISCARD_PERMISSION = "integrations.dlq.discard";
  public static final String OPENED_EVENT_TYPE = "integration.dead-letter.opened.v1";
  public static final String REPLAY_REQUESTED_EVENT_TYPE = "integration.dead-letter.replay-requested.v1";
  public static final String REPLAYED_EVENT_TYPE = "integration.dead-letter.replayed.v1";
  public static final String DISCARDED_EVENT_TYPE = "integration.dead-letter.discarded.v1";
  public static final String BLOCKED_EVENT_TYPE = "integration.dead-letter.blocked.v1";
  public static final String AUDIT_REPLAY_ACTION = "DEAD_LETTER_REPLAY_COMPLETED";
  public static final String AUDIT_DISCARD_ACTION = "DEAD_LETTER_DISCARDED";
  public static final String AUDIT_BLOCKED_ACTION = "DEAD_LETTER_REPLAY_BLOCKED";

  private final Clock clock;
  private final Map<String, DeadLetterItem> items = new HashMap<>();
  private final Map<String, ReplayAttempt> attempts = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final Map<String, ReplayHandler> handlers = new HashMap<>();
  private final Map<String, List<Integer>> compatibleSchemas = new HashMap<>();
  private final List<DeadLetterOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<DeadLetterAuditRecord> auditRecords = new ArrayList<>();
  private final Map<String, Long> metrics = new HashMap<>();

  public DeadLetterReplayService() {
    this(Clock.systemUTC());
  }

  public DeadLetterReplayService(Clock clock) {
    this.clock = clock;
  }

  public void registerHandler(SourceType sourceType, ReplayHandler handler) {
    if (sourceType != null && handler != null) {
      handlers.put(sourceType.name(), handler);
    }
  }

  public void allowLatestCompatibleSchema(SourceType sourceType, String eventType, int originalSchemaVersion, int latestCompatibleVersion) {
    if (sourceType != null && !isBlank(eventType) && originalSchemaVersion > 0 && latestCompatibleVersion > 0) {
      compatibleSchemas.computeIfAbsent(schemaKey(sourceType, eventType, originalSchemaVersion), ignored -> new ArrayList<>()).add(latestCompatibleVersion);
    }
  }

  public DeadLetterResult<DeadLetterResponse> recordDeadLetter(RecordDeadLetterCommand command) {
    DeadLetterError validation = validateRecord(command);
    if (validation != null) {
      return DeadLetterResult.failure(validation);
    }
    Instant now = clock.instant();
    DeadLetterItem item =
        new DeadLetterItem(
            command.tenantId(),
            command.deadLetterId(),
            command.sourceType(),
            command.sourceId(),
            command.eventType(),
            command.schemaVersion(),
            command.originalConfigRef(),
            command.payloadRef(),
            command.payloadHash(),
            command.failureClass(),
            DeadLetterStatus.OPEN,
            0,
            command.legalHold(),
            command.correlationId(),
            now,
            now,
            null);
    items.put(itemKey(item.tenantId(), item.deadLetterId()), item);
    emit(OPENED_EVENT_TYPE, item, command.actorId(), "", Map.of("failureClass", item.failureClass(), "payloadHash", item.payloadHash()));
    recordMetric("dlq_items_open", 1);
    return DeadLetterResult.success(response(item, "opened", List.of()));
  }

  public List<DeadLetterResponse> search(SearchDeadLettersQuery query) {
    if (query == null || !isUuid(query.tenantId())) {
      return List.of();
    }
    return items.values().stream()
        .filter(item -> item.tenantId().equals(query.tenantId()))
        .filter(item -> query.status() == null || item.status() == query.status())
        .filter(item -> query.sourceType() == null || item.sourceType() == query.sourceType())
        .filter(item -> isBlank(query.eventType()) || item.eventType().equals(query.eventType()))
        .sorted(Comparator.comparing(DeadLetterItem::updatedAt).reversed().thenComparing(DeadLetterItem::deadLetterId))
        .map(item -> response(item, "search result", List.of()))
        .toList();
  }

  public DeadLetterResult<DeadLetterResponse> detail(String tenantId, String deadLetterId, String correlationId) {
    if (!isUuid(tenantId) || isBlank(deadLetterId)) {
      return DeadLetterResult.failure(error("400", "VALIDATION_FAILED", "tenantId and deadLetterId are required", correlationId, false));
    }
    DeadLetterItem item = items.get(itemKey(tenantId, deadLetterId));
    if (item == null) {
      return DeadLetterResult.failure(error("404", "NOT_FOUND", "Dead letter item was not found", correlationId, false));
    }
    return DeadLetterResult.success(response(item, "detail", List.of()));
  }

  public DeadLetterResult<ReplayResponse> replay(ReplayDeadLetterCommand command) {
    DeadLetterError validation = validateReplay(command);
    if (validation != null) {
      return DeadLetterResult.failure(validation);
    }
    String requestHash = hash(canonicalReplay(command));
    String idempotencyKey = command.tenantId() + ":" + command.idempotencyKey();
    IdempotencyEntry replay = idempotencyEntries.get(idempotencyKey);
    if (replay != null) {
      if (replay.requestHash().equals(requestHash)) {
        return DeadLetterResult.success(replay.response());
      }
      return DeadLetterResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different replay request", command.correlationId(), false));
    }

    DeadLetterItem item = items.get(itemKey(command.tenantId(), command.deadLetterId()));
    if (item == null) {
      return DeadLetterResult.failure(error("404", "NOT_FOUND", "Dead letter item was not found", command.correlationId(), false));
    }
    DeadLetterError policy = replayPolicyFailure(item, command);
    if (policy != null) {
      ReplayResponse blocked = recordAttempt(item, command, ReplayAttemptStatus.BLOCKED, policy.reason(), policy.message(), Map.of());
      idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, blocked));
      return DeadLetterResult.success(blocked);
    }

    ReplayHandler handler = handlers.get(item.sourceType().name());
    ReplayHandlerResult handlerResult = handler.replay(new ReplayEnvelope(item.tenantId(), item.deadLetterId(), item.sourceType(), item.sourceId(), item.eventType(), item.payloadRef(), item.payloadHash(), item.schemaVersion(), command.mode(), command.correlationId()));
    ReplayAttemptStatus status = command.mode() == ReplayMode.DRY_RUN ? ReplayAttemptStatus.DRY_RUN_PASSED : (handlerResult.success() ? ReplayAttemptStatus.REPLAYED : ReplayAttemptStatus.BLOCKED);
    ReplayResponse response = recordAttempt(item, command, status, handlerResult.reason(), handlerResult.summary(), handlerResult.metadata());
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return DeadLetterResult.success(response);
  }

  public DeadLetterResult<ReplayResponse> discard(DiscardDeadLetterCommand command) {
    if (command == null || !isUuid(command.tenantId()) || isBlank(command.deadLetterId()) || isBlank(command.reason()) || isBlank(command.actorId()) || isBlank(command.idempotencyKey()) || isBlank(command.correlationId())) {
      return DeadLetterResult.failure(error("400", "VALIDATION_FAILED", "tenantId, deadLetterId, reason, actorId, idempotencyKey, and correlationId are required", command == null ? "" : command.correlationId(), false));
    }
    DeadLetterItem item = items.get(itemKey(command.tenantId(), command.deadLetterId()));
    if (item == null) {
      return DeadLetterResult.failure(error("404", "NOT_FOUND", "Dead letter item was not found", command.correlationId(), false));
    }
    DeadLetterItem updated = item.withStatus(DeadLetterStatus.DISCARDED, item.replayCount(), clock.instant());
    items.put(itemKey(updated.tenantId(), updated.deadLetterId()), updated);
    ReplayResponse response = new ReplayResponse(deterministicId(command.tenantId(), command.deadLetterId(), command.idempotencyKey()), command.deadLetterId(), DeadLetterStatus.DISCARDED, ReplayAttemptStatus.DISCARDED, item.replayCount(), Map.of("summary", "discarded", "reason", command.reason()), List.of(), deterministicId(command.tenantId(), command.deadLetterId(), "audit", "discard"), command.correlationId());
    emit(DISCARDED_EVENT_TYPE, updated, command.actorId(), command.idempotencyKey(), response.resultSummary());
    writeAudit(AUDIT_DISCARD_ACTION, command.actorId(), updated, command.correlationId(), response.resultSummary());
    return DeadLetterResult.success(response);
  }

  public List<ReplayResponse> bulkReplay(BulkReplayCommand command) {
    if (command == null || command.maxItems() < 1) {
      return List.of();
    }
    return search(new SearchDeadLettersQuery(command.tenantId(), DeadLetterStatus.OPEN, command.sourceType(), command.eventType())).stream()
        .limit(command.maxItems())
        .map(item -> replay(new ReplayDeadLetterCommand(command.tenantId(), item.id(), command.mode(), command.reason(), item.schemaVersion(), command.actorId(), command.idempotencyKey() + ":" + item.id(), command.correlationId())).value().orElseThrow())
        .toList();
  }

  public List<ReplayAttempt> attemptsForDeadLetter(String tenantId, String deadLetterId) {
    return attempts.values().stream().filter(attempt -> attempt.tenantId().equals(tenantId)).filter(attempt -> attempt.deadLetterId().equals(deadLetterId)).sorted(Comparator.comparing(ReplayAttempt::requestedAt)).toList();
  }

  public List<DeadLetterOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<DeadLetterAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  public Map<String, Long> metrics() {
    return Map.copyOf(metrics);
  }

  private ReplayResponse recordAttempt(DeadLetterItem item, ReplayDeadLetterCommand command, ReplayAttemptStatus attemptStatus, String reason, String summary, Map<String, String> metadata) {
    Instant now = clock.instant();
    boolean replayed = attemptStatus == ReplayAttemptStatus.REPLAYED;
    boolean dryRun = attemptStatus == ReplayAttemptStatus.DRY_RUN_PASSED;
    DeadLetterStatus nextStatus = replayed ? DeadLetterStatus.REPLAYED : (attemptStatus == ReplayAttemptStatus.BLOCKED ? DeadLetterStatus.BLOCKED : item.status());
    int replayCount = replayed ? item.replayCount() + 1 : item.replayCount();
    DeadLetterItem updated = dryRun ? item : item.withStatus(nextStatus, replayCount, now);
    items.put(itemKey(updated.tenantId(), updated.deadLetterId()), updated);

    String attemptId = deterministicId(command.tenantId(), command.deadLetterId(), command.idempotencyKey());
    Map<String, String> resultSummary = safeSummary(resultSummary(summary, reason, metadata));
    ReplayAttempt attempt = new ReplayAttempt(command.tenantId(), attemptId, command.deadLetterId(), command.mode(), attemptStatus, command.actorId(), command.reason(), hash(resultSummary.toString()), resultSummary, now, command.correlationId());
    attempts.put(attemptId, attempt);
    recordMetric("dlq_replay_attempts_total", 1);
    if (replayed) {
      recordMetric("dlq_replay_success_total", 1);
      emit(REPLAY_REQUESTED_EVENT_TYPE, updated, command.actorId(), command.idempotencyKey(), resultSummary);
      emit(REPLAYED_EVENT_TYPE, updated, command.actorId(), command.idempotencyKey(), resultSummary);
      writeAudit(AUDIT_REPLAY_ACTION, command.actorId(), updated, command.correlationId(), resultSummary);
    } else if (attemptStatus == ReplayAttemptStatus.BLOCKED) {
      recordMetric("dlq_replay_blocked_total", 1);
      emit(BLOCKED_EVENT_TYPE, updated, command.actorId(), command.idempotencyKey(), resultSummary);
      writeAudit(AUDIT_BLOCKED_ACTION, command.actorId(), updated, command.correlationId(), resultSummary);
    }
    return new ReplayResponse(attemptId, command.deadLetterId(), updated.status(), attemptStatus, updated.replayCount(), resultSummary, List.of(), auditRef(updated, attemptStatus), command.correlationId());
  }

  private DeadLetterError replayPolicyFailure(DeadLetterItem item, ReplayDeadLetterCommand command) {
    if (item.status() == DeadLetterStatus.DISCARDED || item.status() == DeadLetterStatus.REPLAYED) {
      return error("422", "POLICY_NOT_SATISFIED", "Dead letter item status does not allow replay", command.correlationId(), false);
    }
    if (item.legalHold()) {
      return error("422", "LEGAL_HOLD_CONFLICT", "Legal hold blocks replay", command.correlationId(), false);
    }
    if (forbiddenPayloadReference(item)) {
      return error("422", "FORBIDDEN_PAYLOAD_MATERIAL", "Payload reference is not replay eligible", command.correlationId(), false);
    }
    if (!handlers.containsKey(item.sourceType().name())) {
      return error("503", "DEPENDENCY_UNAVAILABLE", "Replay handler is not available for source type", command.correlationId(), false);
    }
    if (command.mode() == ReplayMode.ORIGINAL_CONFIG && (isBlank(item.originalConfigRef()) || command.expectedSchemaVersion() != item.schemaVersion())) {
      return error("422", "STALE_SCHEMA_OR_CONFIG", "Original configuration and schema version must match the dead letter item", command.correlationId(), false);
    }
    if (command.mode() == ReplayMode.LATEST_COMPATIBLE_CONFIG && !compatibleSchemas.getOrDefault(schemaKey(item.sourceType(), item.eventType(), item.schemaVersion()), List.of()).contains(command.expectedSchemaVersion())) {
      return error("422", "SCHEMA_COMPATIBILITY_NOT_PROVEN", "Latest compatible config replay requires registered schema compatibility", command.correlationId(), false);
    }
    return null;
  }

  private DeadLetterError validateRecord(RecordDeadLetterCommand command) {
    if (command == null || !isUuid(command.tenantId()) || isBlank(command.deadLetterId()) || command.sourceType() == null || isBlank(command.sourceId()) || isBlank(command.eventType()) || command.schemaVersion() < 1 || isBlank(command.payloadRef()) || isBlank(command.payloadHash()) || isBlank(command.failureClass()) || isBlank(command.actorId()) || isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "tenantId, deadLetterId, source, event, schema, payload, failure, actor, and correlationId are required", command == null ? "" : command.correlationId(), false);
    }
    return null;
  }

  private DeadLetterError validateReplay(ReplayDeadLetterCommand command) {
    if (command == null || !isUuid(command.tenantId()) || isBlank(command.deadLetterId()) || command.mode() == null || isBlank(command.reason()) || command.expectedSchemaVersion() < 1 || isBlank(command.actorId()) || isBlank(command.idempotencyKey()) || isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "tenantId, deadLetterId, mode, reason, expectedSchemaVersion, actorId, idempotencyKey, and correlationId are required", command == null ? "" : command.correlationId(), false);
    }
    return null;
  }

  private DeadLetterResponse response(DeadLetterItem item, String summary, List<String> validationMessages) {
    return new DeadLetterResponse(item.deadLetterId(), item.status(), item.sourceType(), item.sourceId(), item.eventType(), item.schemaVersion(), item.failureClass(), item.replayCount(), Map.of("summary", summary, "payloadHash", item.payloadHash(), "payloadRef", "<redacted>"), validationMessages, auditRef(item, null), item.correlationId());
  }

  private void emit(String eventType, DeadLetterItem item, String actorId, String idempotencyKey, Map<String, String> payload) {
    outboxEvents.add(new DeadLetterOutboxEvent(item.tenantId() + ":" + item.deadLetterId(), eventType, "1", "integration-service", actorId, item.correlationId(), idempotencyKey, clock.instant(), safeSummary(payload)));
  }

  private void writeAudit(String action, String actorId, DeadLetterItem item, String correlationId, Map<String, String> summary) {
    auditRecords.add(new DeadLetterAuditRecord(auditRef(item, null), action, actorId, item.tenantId(), item.deadLetterId(), item.status().name(), correlationId, hash(action + item.tenantId() + item.deadLetterId() + summary), safeSummary(summary), clock.instant()));
  }

  private static Map<String, String> resultSummary(String summary, String reason, Map<String, String> metadata) {
    Map<String, String> result = new HashMap<>();
    result.put("summary", nullToEmpty(summary));
    result.put("reason", nullToEmpty(reason));
    if (metadata != null) {
      result.putAll(metadata);
    }
    return result;
  }

  private static Map<String, String> safeSummary(Map<String, String> summary) {
    if (summary == null || summary.isEmpty()) {
      return Map.of();
    }
    Map<String, String> safe = new HashMap<>();
    for (Map.Entry<String, String> entry : summary.entrySet()) {
      String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
      String value = entry.getValue() == null ? "" : entry.getValue();
      String lowerValue = value.toLowerCase(Locale.ROOT);
      if (key.contains("payloadref") || key.contains("payload") || key.contains("secret") || key.contains("borrower") || lowerValue.contains("borrower") || lowerValue.contains("secret")) {
        safe.put(entry.getKey(), "<redacted>");
      } else {
        safe.put(entry.getKey(), value);
      }
    }
    return Map.copyOf(safe);
  }

  private static boolean forbiddenPayloadReference(DeadLetterItem item) {
    String candidate = (item.payloadRef() + " " + item.payloadHash()).toLowerCase(Locale.ROOT);
    return candidate.contains("secret") || candidate.contains("raw-secret");
  }

  private static String itemKey(String tenantId, String deadLetterId) {
    return tenantId + ":" + deadLetterId;
  }

  private static String schemaKey(SourceType sourceType, String eventType, int schemaVersion) {
    return sourceType.name() + ":" + eventType + ":" + schemaVersion;
  }

  private static String canonicalReplay(ReplayDeadLetterCommand command) {
    return command.tenantId() + "|" + command.deadLetterId() + "|" + command.mode() + "|" + command.reason() + "|" + command.expectedSchemaVersion();
  }

  private static String auditRef(DeadLetterItem item, ReplayAttemptStatus status) {
    return deterministicId(item.tenantId(), item.deadLetterId(), "audit", status == null ? item.status().name() : status.name());
  }

  private static void recordMetric(Map<String, Long> metrics, String name, long amount) {
    metrics.merge(name, amount, Long::sum);
  }

  private void recordMetric(String name, long amount) {
    recordMetric(metrics, name, amount);
  }

  private static DeadLetterError error(String code, String reason, String message, String correlationId, boolean retryable) {
    return new DeadLetterError(code, reason, message, correlationId, retryable);
  }

  private static boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String deterministicId(String... parts) {
    return UUID.nameUUIDFromBytes(String.join(":", parts).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private static String hash(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(nullToEmpty(input).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required", ex);
    }
  }

  public enum SourceType {
    WEBHOOK_DELIVERY,
    INVESTOR_API,
    SFTP_FEED,
    OUTBOX,
    LOS_QUOTE_EVENT
  }

  public enum DeadLetterStatus {
    OPEN,
    REPLAYING,
    REPLAYED,
    DISCARDED,
    BLOCKED
  }

  public enum ReplayMode {
    ORIGINAL_CONFIG,
    LATEST_COMPATIBLE_CONFIG,
    DRY_RUN
  }

  public enum ReplayAttemptStatus {
    DRY_RUN_PASSED,
    REPLAYED,
    DISCARDED,
    BLOCKED
  }

  public interface ReplayHandler {
    ReplayHandlerResult replay(ReplayEnvelope envelope);
  }

  public record ReplayHandlerResult(boolean success, String reason, String summary, Map<String, String> metadata) {}

  public record ReplayEnvelope(String tenantId, String deadLetterId, SourceType sourceType, String sourceId, String eventType, String payloadRef, String payloadHash, int schemaVersion, ReplayMode mode, String correlationId) {}

  public record RecordDeadLetterCommand(String tenantId, String deadLetterId, SourceType sourceType, String sourceId, String eventType, int schemaVersion, String originalConfigRef, String payloadRef, String payloadHash, String failureClass, boolean legalHold, String actorId, String correlationId) {}

  public record SearchDeadLettersQuery(String tenantId, DeadLetterStatus status, SourceType sourceType, String eventType) {}

  public record ReplayDeadLetterCommand(String tenantId, String deadLetterId, ReplayMode mode, String reason, int expectedSchemaVersion, String actorId, String idempotencyKey, String correlationId) {}

  public record BulkReplayCommand(String tenantId, SourceType sourceType, String eventType, ReplayMode mode, String reason, int maxItems, String actorId, String idempotencyKey, String correlationId) {}

  public record DiscardDeadLetterCommand(String tenantId, String deadLetterId, String reason, String actorId, String idempotencyKey, String correlationId) {}

  public record DeadLetterItem(String tenantId, String deadLetterId, SourceType sourceType, String sourceId, String eventType, int schemaVersion, String originalConfigRef, String payloadRef, String payloadHash, String failureClass, DeadLetterStatus status, int replayCount, boolean legalHold, String correlationId, Instant createdAt, Instant updatedAt, Instant lastReplayAt) {
    DeadLetterItem withStatus(DeadLetterStatus nextStatus, int nextReplayCount, Instant now) {
      return new DeadLetterItem(tenantId, deadLetterId, sourceType, sourceId, eventType, schemaVersion, originalConfigRef, payloadRef, payloadHash, failureClass, nextStatus, nextReplayCount, legalHold, correlationId, createdAt, now, nextStatus == DeadLetterStatus.REPLAYED ? now : lastReplayAt);
    }
  }

  public record ReplayAttempt(String tenantId, String attemptId, String deadLetterId, ReplayMode mode, ReplayAttemptStatus status, String requestedBy, String reason, String resultHash, Map<String, String> resultSummary, Instant requestedAt, String correlationId) {}

  public record DeadLetterResponse(String id, DeadLetterStatus status, SourceType sourceType, String sourceId, String eventType, int schemaVersion, String failureClass, int replayCount, Map<String, String> resultSummary, List<String> validationMessages, String auditRef, String correlationId) {}

  public record ReplayResponse(String id, String deadLetterId, DeadLetterStatus status, ReplayAttemptStatus attemptStatus, int replayCount, Map<String, String> resultSummary, List<String> validationMessages, String auditRef, String correlationId) {}

  public record DeadLetterOutboxEvent(String eventKey, String eventType, String eventVersion, String sourceService, String actorId, String correlationId, String idempotencyKey, Instant occurredAt, Map<String, String> payload) {}

  public record DeadLetterAuditRecord(String auditId, String action, String actorId, String tenantId, String deadLetterId, String beforeAfterSummary, String correlationId, String replayHash, Map<String, String> summary, Instant occurredAt) {}

  public record DeadLetterError(String code, String reason, String message, String correlationId, boolean retryable) {}

  private record IdempotencyEntry(String requestHash, ReplayResponse response) {}

  public record DeadLetterResult<T>(boolean valid, Optional<T> value, Optional<DeadLetterError> error) {
    static <T> DeadLetterResult<T> success(T value) {
      return new DeadLetterResult<>(true, Optional.of(value), Optional.empty());
    }

    static <T> DeadLetterResult<T> failure(DeadLetterError error) {
      return new DeadLetterResult<>(false, Optional.empty(), Optional.of(error));
    }
  }
}
