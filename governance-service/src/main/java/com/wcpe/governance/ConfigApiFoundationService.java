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
import java.util.Map;
import java.util.UUID;

public final class ConfigApiFoundationService {
  public static final String CREATE_ENDPOINT = "/api/v1/tenants/{tenantId}/governance/configurations";
  public static final String WRITE_PERMISSION = "admin.config.write";
  public static final String DRAFT_STATUS = "DRAFT";
  public static final String CREATED_EVENT_TYPE = "ConfigVersionDrafted.v1";
  public static final String CREATED_AUDIT_ACTION = "CONFIG_API_FOUNDATION_COMPLETED";

  private final Clock clock;
  private final Map<String, ConfigApiDraft> draftsByTenantAndArtifact = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public ConfigApiFoundationService() {
    this(Clock.systemUTC());
  }

  public ConfigApiFoundationService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<ConfigApiDraftResponse> createDraft(ConfigApiCreateCommand command) {
    GovernanceValidationResult<ConfigApiCreateCommand> validation = validate(command);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    String requestHash = hash(canonicalCommand(command));
    String idempotencyLookupKey = command.tenantId() + ":" + command.idempotencyKey();
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyLookupKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        return GovernanceValidationResult.failure("IDEMPOTENCY_REPLAY_MISMATCH");
      }
      return GovernanceValidationResult.success(existing.response());
    }

    Instant now = clock.instant();
    String artifactId = deterministicId(command.tenantId(), command.artifactType(), command.displayName());
    String versionId = deterministicId(artifactId, requestHash, "v1");
    String payloadHash = hash(canonicalMap(command.payload()));
    String etag = "v1-" + payloadHash.substring(0, 16);
    ConfigApiDraft draft =
        new ConfigApiDraft(
            command.tenantId(),
            artifactId,
            command.artifactType(),
            command.displayName(),
            versionId,
            1,
            DRAFT_STATUS,
            command.schemaVersion(),
            command.payload(),
            command.context(),
            command.effectiveStart(),
            command.effectiveEnd(),
            payloadHash,
            etag,
            command.actorId(),
            now,
            command.correlationId());

    draftsByTenantAndArtifact.put(command.tenantId() + ":" + artifactId, draft);
    String eventId = deterministicId(versionId, command.correlationId(), "event");
    String auditId = deterministicId(versionId, command.actorId(), "audit");
    ConfigApiDraftResponse response =
        new ConfigApiDraftResponse(
            artifactId,
            versionId,
            1,
            DRAFT_STATUS,
            etag,
            payloadHash,
            auditId,
            eventId,
            command.correlationId());

    outboxEvents.add(
        new ConfigApiOutboxEvent(
            eventId,
            CREATED_EVENT_TYPE,
            1,
            command.tenantId(),
            artifactId,
            versionId,
            command.actorId(),
            command.correlationId(),
            command.correlationId(),
            command.idempotencyKey(),
            now,
            Map.of(
                "artifactId", artifactId,
                "versionId", versionId,
                "status", DRAFT_STATUS,
                "payloadHash", payloadHash)));
    auditRecords.add(
        new ConfigApiAuditRecord(
            auditId,
            command.tenantId(),
            artifactId,
            versionId,
            command.actorId(),
            CREATED_AUDIT_ACTION,
            "",
            payloadHash,
            command.correlationId(),
            now));
    idempotencyEntries.put(idempotencyLookupKey, new IdempotencyEntry(requestHash, response));
    return GovernanceValidationResult.success(response);
  }

  public List<ConfigApiDraft> draftsForTenant(String tenantId) {
    return draftsByTenantAndArtifact.values().stream()
        .filter(draft -> draft.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(ConfigApiDraft::artifactId))
        .toList();
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private GovernanceValidationResult<ConfigApiCreateCommand> validate(ConfigApiCreateCommand command) {
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
    if (isBlank(command.artifactType())) {
      return GovernanceValidationResult.failure("CONFIG_CONTEXT_INVALID: artifactType is required");
    }
    if (isBlank(command.displayName())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: displayName is required");
    }
    if (isBlank(command.schemaVersion())) {
      return GovernanceValidationResult.failure("CONFIG_SCHEMA_INVALID: schemaVersion is required");
    }
    if (command.payload().isEmpty()) {
      return GovernanceValidationResult.failure("CONFIG_SCHEMA_INVALID: payload is required");
    }
    if (command.effectiveStart() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: effectiveStart is required");
    }
    if (command.effectiveEnd() != null && !command.effectiveEnd().isAfter(command.effectiveStart())) {
      return GovernanceValidationResult.failure("CONFIG_CONTEXT_INVALID: effectiveEnd must be after effectiveStart");
    }
    if (isBlank(command.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: correlationId is required");
    }
    return GovernanceValidationResult.success(command);
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

  private String canonicalCommand(ConfigApiCreateCommand command) {
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.artifactType(),
        command.displayName(),
        command.schemaVersion(),
        canonicalMap(command.payload()),
        canonicalMap(command.context()),
        command.effectiveStart().toString(),
        command.effectiveEnd() == null ? "" : command.effectiveEnd().toString(),
        command.changeSummary() == null ? "" : command.changeSummary(),
        command.correlationId());
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

  private record IdempotencyEntry(String requestHash, ConfigApiDraftResponse response) {}
}
