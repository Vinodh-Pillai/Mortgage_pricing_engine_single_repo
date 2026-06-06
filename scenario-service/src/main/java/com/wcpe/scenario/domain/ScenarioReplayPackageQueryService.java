package com.wcpe.scenario.domain;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class ScenarioReplayPackageQueryService {
  private static final Set<String> REPLAY_ROLES = Set.of("SCENARIO_REPLAY", "SCENARIO_REPLAY_FULL", "SCENARIO_ADMIN");
  private static final Set<String> FULL_REPLAY_ROLES = Set.of("SCENARIO_REPLAY_FULL", "SCENARIO_ADMIN");
  private static final Set<String> SENSITIVE_KEY_TOKENS = Set.of("income", "asset", "credit", "debt", "ssn", "tin", "borrower");

  private final ScenarioRepository repository;

  ScenarioReplayPackageQueryService(ScenarioRepository repository) {
    this.repository = repository;
  }

  ReplayPackage replay(UUID tenantId, UUID scenarioId, ScenarioReplayAccessRequest request) {
    RedactionMode mode = redactionMode(request.redaction());
    if (!hasAnyRole(REPLAY_ROLES)) {
      emitAccessDenied(tenantId, scenarioId, null, null, mode, request, "scenario-replay:read");
      throw new ScenarioException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "scenario-replay:read permission is required.", List.of());
    }
    Scenario scenario = repository.get(tenantId, scenarioId);
    int replayVersion = resolveVersion(scenario, request.version());
    if (mode == RedactionMode.FULL && !hasAnyRole(FULL_REPLAY_ROLES)) {
      emitAccessDenied(tenantId, scenario.scenarioId(), scenario, replayVersion, mode, request, "scenario-replay:export-full");
      throw new ScenarioException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Full replay export requires scenario-replay:export-full permission.", List.of());
    }
    Map<String, Object> exactSnapshot = repository.versionSnapshot(tenantId, scenarioId, replayVersion)
        .orElseThrow(() -> new ScenarioException(HttpStatus.NOT_FOUND, "SCENARIO_VERSION_NOT_FOUND", "Requested scenario version was not found.", List.of()));

    Map<String, Object> raw = mapSnapshotValue(exactSnapshot, "rawFacts", scenario.rawFacts());
    boolean redacted = mode != RedactionMode.FULL;
    Map<String, Object> visibleRaw = redacted ? redact(raw) : deepCopy(raw);
    Map<String, Object> normalized = mapSnapshotValue(exactSnapshot, "normalizedFacts", scenario.normalizedFacts());
    normalized.put("derivedFields", mapSnapshotValue(exactSnapshot, "derivedFields", scenario.derivedFields()));
    normalized.put("hashVerification", verifyHash(scenario, exactSnapshot, replayVersion));
    if (Boolean.TRUE.equals(((Map<?, ?>) normalized.get("hashVerification")).get("verified"))) {
      normalized.put("scenarioHash", expectedHash(scenario, replayVersion));
    }

    List<ValidationIssue> issues = new ArrayList<>(scenario.validationIssues());
    @SuppressWarnings("unchecked")
    Map<String, Object> verification = (Map<String, Object>) normalized.get("hashVerification");
    if (!Boolean.TRUE.equals(verification.get("verified"))) {
      issues.add(new ValidationIssue("HASH_MISMATCH", "replayHash", Severity.WARNING, "Stored replay hash did not match the version snapshot."));
    }

    UUID auditPackageId = emitViewed(tenantId, scenario, replayVersion, mode, request);
    List<EventRecord> events = repository.events(tenantId, scenarioId);
    return new ReplayPackage(scenario.scenarioId(), replayVersion, "scenario-v1", redacted, scenario.status(), scenario.versions(),
        visibleRaw, normalized, issues, events, auditPackageId);
  }

  static Map<String, Object> redact(Map<String, Object> raw) {
    Map<String, Object> redacted = new LinkedHashMap<>();
    raw.forEach((key, value) -> redacted.put(key, redactValue(key, value)));
    return redacted;
  }

  private static Object redactValue(String key, Object value) {
    if (isSensitiveKey(key)) return "REDACTED";
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> nested = new LinkedHashMap<>();
      map.forEach((k, v) -> nested.put(String.valueOf(k), redactValue(String.valueOf(k), v)));
      return nested;
    }
    if (value instanceof List<?> list) return list.stream().map(item -> redactValue(key, item)).toList();
    return value;
  }

  private static boolean isSensitiveKey(String key) {
    String lower = Optional.ofNullable(key).orElse("").toLowerCase(Locale.ROOT);
    return SENSITIVE_KEY_TOKENS.stream().anyMatch(lower::contains);
  }

  private static RedactionMode redactionMode(String requested) {
    String mode = Optional.ofNullable(requested).orElse("role-default").trim().toLowerCase(Locale.ROOT);
    return switch (mode) {
      case "full" -> RedactionMode.FULL;
      case "redacted" -> RedactionMode.REDACTED;
      default -> RedactionMode.ROLE_DEFAULT;
    };
  }

  private static int resolveVersion(Scenario scenario, String version) {
    String requested = Optional.ofNullable(version).orElse("latest");
    return "latest".equalsIgnoreCase(requested) ? scenario.version() : Integer.parseInt(requested);
  }

  private static Map<String, Object> mapSnapshotValue(Map<String, Object> snapshot, String key, Map<String, Object> fallback) {
    Object value = snapshot.get(key);
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((k, v) -> copy.put(String.valueOf(k), v));
      return copy;
    }
    return deepCopy(fallback);
  }

  private static Map<String, Object> deepCopy(Map<String, Object> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    Optional.ofNullable(source).orElse(Map.of()).forEach((key, value) -> copy.put(key, value));
    return copy;
  }

  private static Map<String, Object> verifyHash(Scenario scenario, Map<String, Object> snapshot, int replayVersion) {
    String expected = expectedHash(scenario, replayVersion);
    String actual = Optional.ofNullable(snapshot.get("replayHash")).map(Object::toString).orElse(expected);
    boolean verified = Objects.equals(expected, actual);
    Map<String, Object> verification = new LinkedHashMap<>();
    verification.put("verified", verified);
    verification.put("expectedHash", expected);
    verification.put("actualHash", actual);
    verification.put("warnings", verified ? List.of() : List.of("HASH_MISMATCH"));
    return verification;
  }

  private static String expectedHash(Scenario scenario, int replayVersion) {
    return scenario.versions().stream()
        .filter(version -> version.version() == replayVersion)
        .map(VersionManifest::hash)
        .findFirst()
        .orElse(scenario.replayHash());
  }

  private UUID emitViewed(UUID tenantId, Scenario scenario, int replayVersion, RedactionMode redactionMode, ScenarioReplayAccessRequest request) {
    String corr = request.correlationId() == null || request.correlationId().isBlank() ? UUID.randomUUID().toString() : request.correlationId();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("scenarioId", scenario.scenarioId().toString());
    payload.put("scenarioVersion", replayVersion);
    payload.put("actor", "request-context");
    payload.put("redactionMode", redactionMode.name());
    payload.put("exportFlag", request.export());
    payload.put("accessReasonCode", Optional.ofNullable(request.accessReasonCode()).orElse("VIEW_REPLAY_PACKAGE"));
    payload.put("correlationId", corr);
    EventRecord event = new EventRecord(UUID.randomUUID(), tenantId, scenario.scenarioId(), "ScenarioReplayPackageViewed.v1", 1, corr, Instant.now(), payload);
    repository.event(event);
    UUID auditPackageId = UUID.randomUUID();
    String action = request.export() ? "SCENARIO_REPLAY_PACKAGE_EXPORTED" : "SCENARIO_REPLAY_PACKAGE_VIEWED";
    repository.audit(new AuditRecord(auditPackageId, tenantId, scenario.scenarioId(), action, corr, Instant.now(), scenario.replayHash()));
    return auditPackageId;
  }

  private void emitAccessDenied(UUID tenantId, UUID scenarioId, Scenario scenario, Integer replayVersion, RedactionMode redactionMode,
      ScenarioReplayAccessRequest request, String deniedPermission) {
    String corr = request.correlationId() == null || request.correlationId().isBlank() ? UUID.randomUUID().toString() : request.correlationId();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("scenarioId", scenarioId.toString());
    if (replayVersion != null) payload.put("scenarioVersion", replayVersion);
    payload.put("actor", "request-context");
    payload.put("redactionMode", redactionMode.name());
    payload.put("exportFlag", request.export());
    payload.put("accessReasonCode", Optional.ofNullable(request.accessReasonCode()).orElse("VIEW_REPLAY_PACKAGE"));
    payload.put("correlationId", corr);
    payload.put("decision", "ACCESS_DENIED");
    payload.put("deniedPermission", deniedPermission);
    repository.event(new EventRecord(UUID.randomUUID(), tenantId, scenarioId, "ScenarioReplayPackageAccessDenied.v1", 1, corr, Instant.now(), payload));
    repository.audit(new AuditRecord(UUID.randomUUID(), tenantId, scenarioId, "SCENARIO_REPLAY_PACKAGE_ACCESS_DENIED", corr, Instant.now(), scenario == null ? null : scenario.replayHash()));
  }

  private static boolean hasAnyRole(Set<String> allowed) {
    String roles = Optional.ofNullable(RequestContext.roles()).orElse("");
    return Arrays.stream(roles.split(",")).map(String::trim).anyMatch(allowed::contains);
  }
}
