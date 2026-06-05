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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ChannelApiFoundationService {
  public static final String BASE_PATH = "/api/v1/tenants/{tenantId}/channels";
  public static final String WRITE_PERMISSION = "integrations.channel.write";
  public static final String READ_PERMISSION = "integrations.channel.read";
  public static final String REGISTERED_EVENT_TYPE = "integration.channel-client.registered.v1";
  public static final String UPDATED_EVENT_TYPE = "integration.channel-client.updated.v1";
  public static final String AUDIT_REGISTERED_ACTION = "CHANNEL_API_FOUNDATION_REGISTERED";
  public static final String AUDIT_UPDATED_ACTION = "CHANNEL_API_FOUNDATION_UPDATED";

  private static final Set<ChannelStatus> TERMINAL_STATUSES = Set.of(ChannelStatus.DECOMMISSIONED);

  private final Clock clock;
  private final Map<String, ChannelClient> channels = new HashMap<>();
  private final Map<String, String> channelIdsByExternalRef = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final List<ChannelOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ChannelAuditRecord> auditRecords = new ArrayList<>();

  public ChannelApiFoundationService() {
    this(Clock.systemUTC());
  }

  public ChannelApiFoundationService(Clock clock) {
    this.clock = clock;
  }

  public ChannelResult<ChannelResponse> register(RegisterChannelClient command) {
    ChannelResult<RegisterChannelClient> validation = validateRegister(command);
    if (!validation.valid()) {
      return ChannelResult.failure(validation.error().orElseThrow());
    }

    String requestHash = hash(canonicalRegister(command));
    String idempotencyKey = command.tenantId() + ":register:" + command.idempotencyKey();
    ChannelResult<ChannelResponse> replay = replayOrConflict(idempotencyKey, requestHash);
    if (replay != null) {
      return replay;
    }

    String externalRefKey = command.tenantId() + ":" + command.externalRef();
    if (channelIdsByExternalRef.containsKey(externalRefKey)) {
      return ChannelResult.failure(error("409", "DUPLICATE_EXTERNAL_REF", "Channel externalRef already exists", command.correlationId(), false));
    }

    Instant now = clock.instant();
    String channelId = deterministicId(command.tenantId(), command.externalRef());
    ChannelClient channel =
        new ChannelClient(
            command.tenantId(),
            channelId,
            command.externalRef(),
            command.name(),
            command.channelType(),
            ChannelStatus.DRAFT,
            List.copyOf(command.allowedProducts()),
            Map.copyOf(command.rateLimitPolicy()),
            Map.copyOf(command.metadata()),
            1,
            command.actorId(),
            now,
            now,
            command.correlationId());

    channels.put(key(command.tenantId(), channelId), channel);
    channelIdsByExternalRef.put(externalRefKey, channelId);

    ChannelResponse response = response(channel, "registered");
    writeOutboxAndAudit(REGISTERED_EVENT_TYPE, AUDIT_REGISTERED_ACTION, command.actorId(), command.idempotencyKey(), channel, List.of("registered"), null);
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return ChannelResult.success(response);
  }

  public ChannelResult<ChannelResponse> update(UpdateChannelClient command) {
    ChannelResult<UpdateChannelClient> validation = validateUpdate(command);
    if (!validation.valid()) {
      return ChannelResult.failure(validation.error().orElseThrow());
    }

    String requestHash = hash(canonicalUpdate(command));
    String idempotencyKey = command.tenantId() + ":" + command.channelId() + ":" + command.idempotencyKey();
    ChannelResult<ChannelResponse> replay = replayOrConflict(idempotencyKey, requestHash);
    if (replay != null) {
      return replay;
    }

    ChannelClient existing = channels.get(key(command.tenantId(), command.channelId()));
    if (existing == null) {
      return ChannelResult.failure(error("404", "NOT_FOUND", "Channel was not found", command.correlationId(), false));
    }
    if (TERMINAL_STATUSES.contains(existing.status())) {
      return ChannelResult.failure(error("409", "VERSION_CONFLICT", "Terminal channels cannot be updated", command.correlationId(), false));
    }
    if (!isAllowedTransition(existing.status(), command.status())) {
      return ChannelResult.failure(error("422", "POLICY_NOT_SATISFIED", "Unsupported channel status transition", command.correlationId(), false));
    }
    if (command.expectedVersion() != existing.version()) {
      return ChannelResult.failure(error("409", "VERSION_CONFLICT", "Channel version does not match", command.correlationId(), false));
    }

    Instant now = clock.instant();
    ChannelClient updated =
        new ChannelClient(
            existing.tenantId(),
            existing.channelId(),
            existing.externalRef(),
            existing.name(),
            existing.channelType(),
            command.status(),
            List.copyOf(command.allowedProducts()),
            Map.copyOf(command.rateLimitPolicy()),
            Map.copyOf(command.metadata()),
            existing.version() + 1,
            command.actorId(),
            existing.createdAt(),
            now,
            command.correlationId());

    channels.put(key(command.tenantId(), command.channelId()), updated);
    ChannelResponse response = response(updated, "updated");
    writeOutboxAndAudit(UPDATED_EVENT_TYPE, AUDIT_UPDATED_ACTION, command.actorId(), command.idempotencyKey(), updated, changedFields(existing, updated), existing);
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return ChannelResult.success(response);
  }

  public ChannelResult<ChannelResponse> fetch(String tenantId, String channelId, String correlationId) {
    if (!isUuid(tenantId) || isBlank(channelId)) {
      return ChannelResult.failure(error("400", "VALIDATION_FAILED", "tenantId and channelId are required", correlationId, false));
    }
    ChannelClient channel = channels.get(key(tenantId, channelId));
    if (channel == null) {
      return ChannelResult.failure(error("404", "NOT_FOUND", "Channel was not found", correlationId, false));
    }
    return ChannelResult.success(response(channel, "fetched"));
  }

  public ChannelReadiness validateReadiness(String tenantId, String channelId, String correlationId) {
    ChannelClient channel = channels.get(key(tenantId, channelId));
    if (channel == null) {
      return new ChannelReadiness(false, List.of(error("404", "NOT_FOUND", "Channel was not found", correlationId, false)), correlationId);
    }
    List<ChannelError> messages = new ArrayList<>();
    if (channel.status() != ChannelStatus.ACTIVE) {
      messages.add(error("422", "POLICY_NOT_SATISFIED", "Channel is not active", correlationId, false));
    }
    if (channel.allowedProducts().isEmpty()) {
      messages.add(error("422", "POLICY_NOT_SATISFIED", "Allowed product references are required", correlationId, false));
    }
    return new ChannelReadiness(messages.isEmpty(), messages, correlationId);
  }

  public List<ChannelClient> channelsForTenant(String tenantId) {
    return channels.values().stream()
        .filter(channel -> channel.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(ChannelClient::externalRef))
        .toList();
  }

  public List<ChannelOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ChannelAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private ChannelResult<RegisterChannelClient> validateRegister(RegisterChannelClient command) {
    if (command == null) {
      return ChannelResult.failure(error("400", "VALIDATION_FAILED", "command is required", "", false));
    }
    ChannelError common = validateCommon(command.tenantId(), command.actorId(), command.idempotencyKey(), command.correlationId());
    if (common != null) {
      return ChannelResult.failure(common);
    }
    if (isBlank(command.externalRef())) {
      return ChannelResult.failure(error("400", "VALIDATION_FAILED", "externalRef is required", command.correlationId(), false));
    }
    if (isBlank(command.name())) {
      return ChannelResult.failure(error("400", "VALIDATION_FAILED", "name is required", command.correlationId(), false));
    }
    if (command.channelType() == null) {
      return ChannelResult.failure(error("400", "VALIDATION_FAILED", "channelType is required", command.correlationId(), false));
    }
    return validatePolicyBackedFields(command.allowedProducts(), command.rateLimitPolicy(), command.correlationId()).map(ChannelResult::<RegisterChannelClient>failure).orElseGet(() -> ChannelResult.success(command));
  }

  private ChannelResult<UpdateChannelClient> validateUpdate(UpdateChannelClient command) {
    if (command == null) {
      return ChannelResult.failure(error("400", "VALIDATION_FAILED", "command is required", "", false));
    }
    ChannelError common = validateCommon(command.tenantId(), command.actorId(), command.idempotencyKey(), command.correlationId());
    if (common != null) {
      return ChannelResult.failure(common);
    }
    if (isBlank(command.channelId())) {
      return ChannelResult.failure(error("400", "VALIDATION_FAILED", "channelId is required", command.correlationId(), false));
    }
    if (command.expectedVersion() < 1) {
      return ChannelResult.failure(error("400", "VALIDATION_FAILED", "expectedVersion is required", command.correlationId(), false));
    }
    if (command.status() == null) {
      return ChannelResult.failure(error("400", "VALIDATION_FAILED", "status is required", command.correlationId(), false));
    }
    return validatePolicyBackedFields(command.allowedProducts(), command.rateLimitPolicy(), command.correlationId()).map(ChannelResult::<UpdateChannelClient>failure).orElseGet(() -> ChannelResult.success(command));
  }

  private ChannelError validateCommon(String tenantId, String actorId, String idempotencyKey, String correlationId) {
    if (!isUuid(tenantId)) {
      return error("400", "VALIDATION_FAILED", "tenantId must be a UUID", correlationId, false);
    }
    if (isBlank(actorId)) {
      return error("401", "UNAUTHENTICATED", "actorId is required", correlationId, false);
    }
    if (isBlank(idempotencyKey)) {
      return error("400", "VALIDATION_FAILED", "Idempotency-Key is required", correlationId, false);
    }
    if (isBlank(correlationId)) {
      return error("400", "VALIDATION_FAILED", "correlationId is required", "", false);
    }
    return null;
  }

  private Optional<ChannelError> validatePolicyBackedFields(List<String> allowedProducts, Map<String, String> rateLimitPolicy, String correlationId) {
    if (allowedProducts == null || allowedProducts.isEmpty() || allowedProducts.stream().anyMatch(this::isBlank)) {
      return Optional.of(error("422", "POLICY_NOT_SATISFIED", "Allowed product references must be tenant-governed IDs", correlationId, false));
    }
    if (rateLimitPolicy == null || rateLimitPolicy.isEmpty()) {
      return Optional.of(error("422", "POLICY_NOT_SATISFIED", "rateLimitPolicy reference/configuration is required", correlationId, false));
    }
    return Optional.empty();
  }

  private ChannelResult<ChannelResponse> replayOrConflict(String idempotencyKey, String requestHash) {
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing == null) {
      return null;
    }
    if (!existing.requestHash().equals(requestHash)) {
      return ChannelResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request", existing.response().correlationId(), false));
    }
    return ChannelResult.success(existing.response());
  }

  private boolean isAllowedTransition(ChannelStatus from, ChannelStatus to) {
    return switch (from) {
      case DRAFT -> to == ChannelStatus.ACTIVE;
      case ACTIVE -> to == ChannelStatus.SUSPENDED || to == ChannelStatus.DECOMMISSIONED;
      case SUSPENDED -> to == ChannelStatus.ACTIVE || to == ChannelStatus.DECOMMISSIONED;
      case DECOMMISSIONED -> false;
    };
  }

  private void writeOutboxAndAudit(
      String eventType,
      String auditAction,
      String actorId,
      String idempotencyKey,
      ChannelClient after,
      List<String> changedFields,
      ChannelClient before) {
    String eventId = deterministicId(after.tenantId(), after.channelId(), after.version() + ":event");
    String auditId = deterministicId(after.tenantId(), after.channelId(), after.version() + ":audit");
    String payloadHash = hash(after.tenantId() + ":" + after.channelId() + ":" + after.version() + ":" + canonicalMap(after.metadata()));
    outboxEvents.add(
        new ChannelOutboxEvent(
            eventId,
            eventType,
            1,
            after.tenantId(),
            after.channelId(),
            actorId,
            after.correlationId(),
            after.correlationId(),
            idempotencyKey,
            payloadHash,
            after.updatedAt(),
            Map.of(
                "channelId", after.channelId(),
                "externalRef", after.externalRef(),
                "channelType", after.channelType().name(),
                "status", after.status().name(),
                "version", String.valueOf(after.version()),
                "changedFields", String.join(",", changedFields))));
    auditRecords.add(
        new ChannelAuditRecord(
            auditId,
            after.tenantId(),
            after.channelId(),
            actorId,
            auditAction,
            before == null ? "" : hash(before.status() + ":" + before.version()),
            hash(after.status() + ":" + after.version()),
            after.correlationId(),
            payloadHash,
            after.updatedAt()));
  }

  private List<String> changedFields(ChannelClient before, ChannelClient after) {
    List<String> changed = new ArrayList<>();
    if (before.status() != after.status()) {
      changed.add("status");
    }
    if (!before.allowedProducts().equals(after.allowedProducts())) {
      changed.add("allowedProducts");
    }
    if (!before.rateLimitPolicy().equals(after.rateLimitPolicy())) {
      changed.add("rateLimitPolicy");
    }
    if (!before.metadata().equals(after.metadata())) {
      changed.add("metadata");
    }
    return changed.isEmpty() ? List.of("version") : changed;
  }

  private ChannelResponse response(ChannelClient channel, String summary) {
    return new ChannelResponse(
        channel.channelId(),
        channel.status(),
        channel.version(),
        summary,
        List.of(),
        "audit:" + channel.tenantId() + ":" + channel.channelId() + ":" + channel.version(),
        "event:" + channel.tenantId() + ":" + channel.channelId() + ":" + channel.version(),
        channel.correlationId());
  }

  private String key(String tenantId, String channelId) {
    return tenantId + ":" + channelId;
  }

  private String deterministicId(String... parts) {
    return UUID.nameUUIDFromBytes(String.join(":", parts).getBytes(StandardCharsets.UTF_8)).toString();
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

  private String canonicalRegister(RegisterChannelClient command) {
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.externalRef(),
        command.name(),
        command.channelType().name(),
        canonicalList(command.allowedProducts()),
        canonicalMap(command.rateLimitPolicy()),
        canonicalMap(command.metadata()),
        command.correlationId());
  }

  private String canonicalUpdate(UpdateChannelClient command) {
    return String.join(
        "|",
        command.tenantId(),
        command.channelId(),
        command.idempotencyKey(),
        command.actorId(),
        command.status().name(),
        String.valueOf(command.expectedVersion()),
        canonicalList(command.allowedProducts()),
        canonicalMap(command.rateLimitPolicy()),
        canonicalMap(command.metadata()),
        command.correlationId());
  }

  private String canonicalList(List<String> values) {
    return values.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
  }

  private String canonicalMap(Map<String, String> map) {
    return map.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private ChannelError error(String code, String reason, String message, String correlationId, boolean retryable) {
    return new ChannelError(code, reason, message, List.of(), correlationId == null ? "" : correlationId, retryable);
  }

  private record IdempotencyEntry(String requestHash, ChannelResponse response) {}

  public enum ChannelType {
    LOS,
    BROKER_PORTAL,
    CONSUMER_DIRECT,
    INVESTOR,
    OPS
  }

  public enum ChannelStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    DECOMMISSIONED
  }

  public record RegisterChannelClient(
      String tenantId,
      String idempotencyKey,
      String actorId,
      String externalRef,
      String name,
      ChannelType channelType,
      List<String> allowedProducts,
      Map<String, String> rateLimitPolicy,
      Map<String, String> metadata,
      String correlationId) {}

  public record UpdateChannelClient(
      String tenantId,
      String channelId,
      String idempotencyKey,
      String actorId,
      ChannelStatus status,
      int expectedVersion,
      List<String> allowedProducts,
      Map<String, String> rateLimitPolicy,
      Map<String, String> metadata,
      String correlationId) {}

  public record ChannelClient(
      String tenantId,
      String channelId,
      String externalRef,
      String name,
      ChannelType channelType,
      ChannelStatus status,
      List<String> allowedProducts,
      Map<String, String> rateLimitPolicy,
      Map<String, String> metadata,
      int version,
      String actorId,
      Instant createdAt,
      Instant updatedAt,
      String correlationId) {}

  public record ChannelResponse(
      String id,
      ChannelStatus status,
      int version,
      String resultSummary,
      List<String> validationMessages,
      String auditRef,
      String replayRef,
      String correlationId) {}

  public record ChannelError(
      String code,
      String reason,
      String message,
      List<String> fieldErrors,
      String correlationId,
      boolean retryable) {}

  public record ChannelResult<T>(boolean valid, Optional<T> value, Optional<ChannelError> error) {
    public static <T> ChannelResult<T> success(T value) {
      return new ChannelResult<>(true, Optional.of(value), Optional.empty());
    }

    public static <T> ChannelResult<T> failure(ChannelError error) {
      return new ChannelResult<>(false, Optional.empty(), Optional.of(error));
    }
  }

  public record ChannelReadiness(boolean ready, List<ChannelError> validationMessages, String correlationId) {}

  public record ChannelOutboxEvent(
      String eventId,
      String eventType,
      int schemaVersion,
      String tenantId,
      String channelId,
      String actor,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String payloadHash,
      Instant occurredAt,
      Map<String, String> payload) {}

  public record ChannelAuditRecord(
      String auditId,
      String tenantId,
      String channelId,
      String actor,
      String action,
      String beforeHash,
      String afterHash,
      String correlationId,
      String replayHash,
      Instant occurredAt) {}
}
