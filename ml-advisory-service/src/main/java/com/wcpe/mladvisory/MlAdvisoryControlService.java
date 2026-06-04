package com.wcpe.mladvisory;

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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MlAdvisoryControlService {
  public static final String GET_CONTROLS_ENDPOINT =
      "GET /api/v1/tenants/{tenantId}/ml-advisory/controls?channel=&productFamily=&advisoryType=";
  public static final String CREATE_CONTROL_ENDPOINT = "POST /api/v1/tenants/{tenantId}/ml-advisory/controls";
  public static final String ACTIVATE_KILL_SWITCH_ENDPOINT =
      "POST /api/v1/tenants/{tenantId}/ml-advisory/kill-switch:activate";
  public static final String DEACTIVATE_KILL_SWITCH_ENDPOINT =
      "POST /api/v1/tenants/{tenantId}/ml-advisory/kill-switch:deactivate";
  public static final String ADMIN_ROLE = "ML_ADVISORY_ADMIN";
  public static final String CONTROL_CHANGED_EVENT = "MlAdvisoryControlChanged.v1";
  public static final String KILL_SWITCH_CHANGED_EVENT = "MlAdvisoryKillSwitchChanged.v1";
  public static final String CACHE_KEY_PATTERN = "tenant:%s:ml-advisory:control:%s:v%d";

  private final Clock clock;
  private final Map<String, MlAdvisoryControl> activeControls = new HashMap<>();
  private final Map<String, MlAdvisoryControlResponse> idempotencyResponses = new HashMap<>();
  private final Map<String, String> idempotencyHashes = new HashMap<>();
  private final Map<String, KillSwitchState> killSwitches = new HashMap<>();
  private final List<MlAdvisoryOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<MlAdvisoryAuditRecord> auditRecords = new ArrayList<>();

  public MlAdvisoryControlService() {
    this(Clock.systemUTC());
  }

  public MlAdvisoryControlService(Clock clock) {
    this.clock = clock;
  }

  public MlAdvisoryResult<MlAdvisoryControlResponse> createControl(CreateControlCommand command) {
    MlAdvisoryResult<CreateControlCommand> validation = validateCreate(command);
    if (!validation.valid()) {
      return MlAdvisoryResult.failure(validation.errorCode().orElseThrow());
    }

    String idempotencyKey = command.tenantId() + ":" + command.idempotencyKey();
    String requestHash = hash(canonicalCreate(command));
    String existingHash = idempotencyHashes.get(idempotencyKey);
    if (existingHash != null) {
      if (!existingHash.equals(requestHash)) {
        return MlAdvisoryResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return MlAdvisoryResult.success(idempotencyResponses.get(idempotencyKey));
    }

    String scopeKey = scopeKey(command.tenantId(), command.channel(), command.productFamily(), command.advisoryType());
    MlAdvisoryControl previous = activeControls.get(scopeKey);
    if (previous != null && overlaps(previous, command.effectiveFrom(), command.effectiveTo())) {
      return MlAdvisoryResult.failure("ML_SCOPE_CONFLICT");
    }

    Instant now = clock.instant();
    int version = previous == null ? 1 : previous.version() + 1;
    String controlId = deterministicId(scopeKey, String.valueOf(version), command.effectiveFrom().toString());
    MlAdvisoryControl control =
        new MlAdvisoryControl(
            controlId,
            command.tenantId(),
            command.channel(),
            command.productFamily(),
            command.advisoryType(),
            command.mode(),
            command.effectiveFrom(),
            command.effectiveTo(),
            version,
            "ACTIVE",
            command.actorId(),
            now,
            command.approvedBy(),
            command.approvalRef(),
            command.changeReason(),
            command.modelRiskTicket());
    activeControls.put(scopeKey, control);

    String eventId = deterministicId(controlId, command.correlationId(), CONTROL_CHANGED_EVENT);
    MlAdvisoryControlResponse response =
        new MlAdvisoryControlResponse(
            controlId,
            command.tenantId(),
            command.advisoryType(),
            command.mode(),
            command.mode(),
            version,
            false,
            "",
            cacheKey(command.tenantId(), command.channel(), command.productFamily(), command.advisoryType(), version),
            eventId,
            deterministicId(controlId, command.actorId(), "audit"),
            command.correlationId());
    outboxEvents.add(
        new MlAdvisoryOutboxEvent(
            eventId,
            CONTROL_CHANGED_EVENT,
            command.tenantId(),
            controlId,
            command.actorId(),
            command.correlationId(),
            command.idempotencyKey(),
            now,
            Map.of(
                "scope", scopeKey,
                "advisoryType", command.advisoryType().name(),
                "oldMode", previous == null ? AdvisoryMode.DISABLED.name() : previous.mode().name(),
                "newMode", command.mode().name(),
                "version", String.valueOf(version),
                "effectiveFrom", command.effectiveFrom().toString())));
    auditRecords.add(
        new MlAdvisoryAuditRecord(
            response.auditRef(),
            command.tenantId(),
            command.actorId(),
            "ML_ADVISORY_CONTROL_CHANGED",
            previous == null ? AdvisoryMode.DISABLED.name() : previous.mode().name(),
            command.mode().name(),
            command.correlationId(),
            now));
    idempotencyHashes.put(idempotencyKey, requestHash);
    idempotencyResponses.put(idempotencyKey, response);
    return MlAdvisoryResult.success(response);
  }

  public MlAdvisoryResult<MlAdvisoryControlResponse> resolveEffectiveMode(ResolveControlQuery query) {
    if (query == null || !isUuid(query.tenantId())) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    KillSwitchState killSwitch = killSwitchFor(query.tenantId()).orElse(null);
    if (killSwitch != null && killSwitch.enabled()) {
      return MlAdvisoryResult.success(
          new MlAdvisoryControlResponse(
              "",
              query.tenantId(),
              query.advisoryType(),
              AdvisoryMode.DISABLED,
              AdvisoryMode.DISABLED,
              0,
              true,
              killSwitch.reason(),
              "",
              "",
              "",
              query.correlationId()));
    }
    MlAdvisoryControl control =
        activeControls.get(scopeKey(query.tenantId(), query.channel(), query.productFamily(), query.advisoryType()));
    AdvisoryMode configured = control == null ? AdvisoryMode.DISABLED : control.mode();
    int version = control == null ? 0 : control.version();
    return MlAdvisoryResult.success(
        new MlAdvisoryControlResponse(
            control == null ? "" : control.id(),
            query.tenantId(),
            query.advisoryType(),
            configured,
            configured,
            version,
            false,
            "",
            version == 0 ? "" : cacheKey(query.tenantId(), query.channel(), query.productFamily(), query.advisoryType(), version),
            "",
            "",
            query.correlationId()));
  }

  public MlAdvisoryResult<KillSwitchState> activateKillSwitch(KillSwitchCommand command) {
    MlAdvisoryResult<KillSwitchCommand> validation = validateKillSwitch(command, true);
    if (!validation.valid()) {
      return MlAdvisoryResult.failure(validation.errorCode().orElseThrow());
    }
    KillSwitchState state = newKillSwitch(command, true);
    killSwitches.put(command.tenantId(), state);
    recordKillSwitchEvent(command, state);
    return MlAdvisoryResult.success(state);
  }

  public MlAdvisoryResult<KillSwitchState> deactivateKillSwitch(KillSwitchCommand command) {
    MlAdvisoryResult<KillSwitchCommand> validation = validateKillSwitch(command, false);
    if (!validation.valid()) {
      return MlAdvisoryResult.failure(validation.errorCode().orElseThrow());
    }
    KillSwitchState state = newKillSwitch(command, false);
    killSwitches.put(command.tenantId(), state);
    recordKillSwitchEvent(command, state);
    return MlAdvisoryResult.success(state);
  }

  public List<MlAdvisoryOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<MlAdvisoryAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  public List<MlAdvisoryControl> controlsForTenant(String tenantId) {
    return activeControls.values().stream()
        .filter(control -> control.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(MlAdvisoryControl::id))
        .toList();
  }

  private MlAdvisoryResult<CreateControlCommand> validateCreate(CreateControlCommand command) {
    if (command == null) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (!isUuid(command.tenantId()) || isBlank(command.idempotencyKey()) || isBlank(command.actorId())) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (!command.actorRoles().contains(ADMIN_ROLE)) {
      return MlAdvisoryResult.failure("ML_CONTROL_UNAUTHORIZED");
    }
    if (isBlank(command.channel()) || isBlank(command.productFamily()) || command.advisoryType() == null || command.mode() == null) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (isBlank(command.changeReason()) || isBlank(command.modelRiskTicket()) || command.effectiveFrom() == null) {
      return MlAdvisoryResult.failure("POLICY_NOT_SATISFIED");
    }
    if (command.effectiveTo() != null && !command.effectiveTo().isAfter(command.effectiveFrom())) {
      return MlAdvisoryResult.failure("ML_FLAG_INVALID_TRANSITION");
    }
    if (killSwitchFor(command.tenantId()).filter(KillSwitchState::enabled).isPresent()) {
      return MlAdvisoryResult.failure("ML_KILL_SWITCH_ACTIVE");
    }
    if (command.mode() == AdvisoryMode.ADVISORY_VISIBLE && !"APPROVED_FOR_ADVISORY".equals(command.modelVersionStatus())) {
      return MlAdvisoryResult.failure("ML_MODEL_NOT_APPROVED");
    }
    if (command.mode() == AdvisoryMode.ADVISORY_VISIBLE && (isBlank(command.approvedBy()) || isBlank(command.approvalRef()))) {
      return MlAdvisoryResult.failure("POLICY_NOT_SATISFIED");
    }
    return MlAdvisoryResult.success(command);
  }

  private MlAdvisoryResult<KillSwitchCommand> validateKillSwitch(KillSwitchCommand command, boolean activation) {
    if (command == null || !isUuid(command.tenantId()) || isBlank(command.actorId()) || isBlank(command.reason())) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (!command.actorRoles().contains(ADMIN_ROLE)) {
      return MlAdvisoryResult.failure("ML_CONTROL_UNAUTHORIZED");
    }
    if (!activation && (isBlank(command.approvedBy()) || Objects.equals(command.actorId(), command.approvedBy()))) {
      return MlAdvisoryResult.failure("POLICY_NOT_SATISFIED");
    }
    return MlAdvisoryResult.success(command);
  }

  private KillSwitchState newKillSwitch(KillSwitchCommand command, boolean enabled) {
    return new KillSwitchState(
        deterministicId(command.tenantId(), command.correlationId(), String.valueOf(enabled)),
        command.tenantId(),
        enabled,
        command.reason(),
        command.actorId(),
        clock.instant(),
        command.correlationId(),
        command.approvedBy());
  }

  private void recordKillSwitchEvent(KillSwitchCommand command, KillSwitchState state) {
    String eventId = deterministicId(state.id(), command.correlationId(), KILL_SWITCH_CHANGED_EVENT);
    outboxEvents.add(
        new MlAdvisoryOutboxEvent(
            eventId,
            KILL_SWITCH_CHANGED_EVENT,
            command.tenantId(),
            state.id(),
            command.actorId(),
            command.correlationId(),
            command.idempotencyKey(),
            clock.instant(),
            Map.of("enabled", String.valueOf(state.enabled()), "reason", state.reason())));
    auditRecords.add(
        new MlAdvisoryAuditRecord(
            deterministicId(state.id(), command.actorId(), "audit"),
            command.tenantId(),
            command.actorId(),
            "ML_ADVISORY_KILL_SWITCH_CHANGED",
            String.valueOf(!state.enabled()),
            String.valueOf(state.enabled()),
            command.correlationId(),
            clock.instant()));
  }

  private Optional<KillSwitchState> killSwitchFor(String tenantId) {
    KillSwitchState tenantSwitch = killSwitches.get(tenantId);
    if (tenantSwitch != null) {
      return Optional.of(tenantSwitch);
    }
    return Optional.ofNullable(killSwitches.get("GLOBAL"));
  }

  private boolean overlaps(MlAdvisoryControl previous, Instant start, Instant end) {
    Instant previousEnd = previous.effectiveTo() == null ? Instant.MAX : previous.effectiveTo();
    Instant newEnd = end == null ? Instant.MAX : end;
    return start.isBefore(previousEnd) && previous.effectiveFrom().isBefore(newEnd);
  }

  private String scopeKey(String tenantId, String channel, String productFamily, AdvisoryType advisoryType) {
    return tenantId + ":" + channel + ":" + productFamily + ":" + advisoryType;
  }

  private String cacheKey(String tenantId, String channel, String productFamily, AdvisoryType advisoryType, int version) {
    return CACHE_KEY_PATTERN.formatted(tenantId, hash(channel + ":" + productFamily + ":" + advisoryType).substring(0, 16), version);
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

  private String canonicalCreate(CreateControlCommand command) {
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.channel(),
        command.productFamily(),
        command.advisoryType().name(),
        command.mode().name(),
        command.effectiveFrom().toString(),
        command.effectiveTo() == null ? "" : command.effectiveTo().toString(),
        command.changeReason(),
        command.modelRiskTicket(),
        command.modelVersionStatus() == null ? "" : command.modelVersionStatus(),
        command.approvedBy() == null ? "" : command.approvedBy(),
        command.approvalRef() == null ? "" : command.approvalRef(),
        command.correlationId());
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }
}
