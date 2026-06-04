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

public final class ConfigValidationService {
  public static final String VALIDATE_ENDPOINT =
      "/api/v1/tenants/{tenantId}/admin/config-artifacts/{artifactId}/versions/{versionId}/validation-runs";
  public static final String READ_ENDPOINT = "/api/v1/tenants/{tenantId}/admin/config-validation-runs/{runId}";
  public static final String VALIDATE_PERMISSION = "admin.config.validate";
  public static final String READ_PERMISSION = "admin.config.read";
  public static final String COMPLETED_EVENT_TYPE = "ConfigValidationCompleted.v1";
  public static final String AUDIT_ACTION = "CONFIG_VALIDATION_SERVICE_COMPLETED";

  private final Clock clock;
  private final Map<String, ConfigValidationRun> runsByTenantAndRun = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public ConfigValidationService() {
    this(Clock.systemUTC());
  }

  public ConfigValidationService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<ConfigValidationRun> validate(ConfigValidationCommand command) {
    GovernanceValidationResult<ConfigValidationCommand> validation = validateCommand(command);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    String requestHash = hash(canonicalCommand(command));
    String idempotencyLookupKey = command.tenantId() + ":" + command.idempotencyKey();
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyLookupKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        return GovernanceValidationResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return GovernanceValidationResult.success(existing.run());
    }

    Instant now = clock.instant();
    String inputHash = command.payloadHash() == null || command.payloadHash().isBlank() ? hash(canonicalMap(command.payload())) : command.payloadHash();
    String policyVersionSetHash = hash(canonicalPolicies(command.policies()));
    String runId = deterministicId(command.tenantId(), command.artifactId(), command.versionId(), inputHash, policyVersionSetHash);
    List<ConfigValidationFinding> findings = sortedFindings(command, runId);
    boolean blocking = findings.stream().anyMatch(ConfigValidationFinding::blocking);
    String resultHash = hash(inputHash + "|" + policyVersionSetHash + "|" + canonicalFindings(findings));
    String status = blocking ? "BLOCKED" : findings.isEmpty() ? "PASSED" : "WARNING";
    String auditRef = deterministicId(runId, command.actorId(), "audit");
    String eventId = deterministicId(runId, command.correlationId(), "event");
    ConfigValidationRun run =
        new ConfigValidationRun(
            command.tenantId(),
            runId,
            command.artifactId(),
            command.versionId(),
            command.validationScope() == null || command.validationScope().isBlank() ? "DRAFT" : command.validationScope(),
            status,
            inputHash,
            policyVersionSetHash,
            resultHash,
            now,
            now,
            command.actorId(),
            auditRef,
            runId,
            command.correlationId(),
            !blocking,
            findings);

    runsByTenantAndRun.put(command.tenantId() + ":" + runId, run);
    idempotencyEntries.put(idempotencyLookupKey, new IdempotencyEntry(requestHash, run));
    outboxEvents.add(
        new ConfigApiOutboxEvent(
            eventId,
            COMPLETED_EVENT_TYPE,
            1,
            command.tenantId(),
            command.artifactId(),
            command.versionId(),
            command.actorId(),
            command.correlationId(),
            command.correlationId(),
            command.idempotencyKey(),
            now,
            Map.of(
                "runId", runId,
                "status", status,
                "resultHash", resultHash,
                "policyVersionSetHash", policyVersionSetHash,
                "publishEligible", Boolean.toString(!blocking))));
    auditRecords.add(
        new ConfigApiAuditRecord(
            auditRef,
            command.tenantId(),
            command.artifactId(),
            command.versionId(),
            command.actorId(),
            AUDIT_ACTION,
            inputHash,
            resultHash,
            command.correlationId(),
            now));
    return GovernanceValidationResult.success(run);
  }

  public GovernanceValidationResult<ConfigValidationRun> validationRun(String tenantId, String runId) {
    ConfigValidationRun run = runsByTenantAndRun.get(tenantId + ":" + runId);
    return run == null ? GovernanceValidationResult.failure("NOT_FOUND") : GovernanceValidationResult.success(run);
  }

  public List<ConfigValidationRun> validationRunsForTenant(String tenantId) {
    return runsByTenantAndRun.values().stream()
        .filter(run -> run.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(ConfigValidationRun::completedAt).thenComparing(ConfigValidationRun::runId))
        .toList();
  }

  public boolean approvalOrPublishAllowed(String tenantId, String runId) {
    return validationRun(tenantId, runId).value().map(ConfigValidationRun::publishEligible).orElse(false);
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private GovernanceValidationResult<ConfigValidationCommand> validateCommand(ConfigValidationCommand command) {
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
    if (isBlank(command.artifactId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: artifactId is required");
    }
    if (isBlank(command.versionId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: versionId is required");
    }
    if (isBlank(command.artifactType())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: artifactType is required");
    }
    if (isBlank(command.schemaVersion())) {
      return GovernanceValidationResult.failure("SCHEMA_VERSION_UNSUPPORTED: schemaVersion is required");
    }
    if (command.payload().isEmpty()) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: payload is required");
    }
    if (command.policies().isEmpty()) {
      return GovernanceValidationResult.failure("VALIDATION_POLICY_MISSING");
    }
    if (isBlank(command.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: correlationId is required");
    }
    return GovernanceValidationResult.success(command);
  }

  private List<ConfigValidationFinding> sortedFindings(ConfigValidationCommand command, String runId) {
    List<ConfigValidationFinding> findings = new ArrayList<>();
    boolean schemaSupported =
        command.policies().stream().anyMatch(policy -> policy.supportedSchemaVersions().contains(command.schemaVersion()));
    if (!schemaSupported) {
      findings.add(
          finding(
              command,
              runId,
              ConfigValidationSeverity.BLOCKER,
              "SCHEMA_VERSION_UNSUPPORTED",
              "$.schemaVersion",
              "config.validation.schema.unsupported",
              Map.of("schemaVersion", command.schemaVersion()),
              "Select a validation policy version that supports this schema.",
              true));
    }

    for (ConfigValidationPolicy policy : command.policies()) {
      for (String field : policy.requiredPayloadFields()) {
        if (!command.payload().containsKey(field) || command.payload().get(field).isBlank()) {
          findings.add(
              finding(
                  command,
                  runId,
                  ConfigValidationSeverity.ERROR,
                  "REQUIRED_FIELD_MISSING",
                  "$.payload." + field,
                  policy.messageKeys().getOrDefault("required", "config.validation.required-field.missing"),
                  Map.of("field", field, "policyId", policy.policyId(), "policyVersion", policy.version()),
                  "Supply the tenant-configured required field before approval or publication.",
                  true));
        }
      }
    }

    List<ConfigValidationFinding> sorted =
        findings.stream()
            .sorted(
                Comparator.comparing(ConfigValidationFinding::blocking)
                    .reversed()
                    .thenComparing(Comparator.comparingInt((ConfigValidationFinding finding) -> severityRank(finding.severity())).reversed())
                    .thenComparing(ConfigValidationFinding::code)
                    .thenComparing(ConfigValidationFinding::jsonPath))
            .toList();
    List<ConfigValidationFinding> ordered = new ArrayList<>();
    for (int index = 0; index < sorted.size(); index++) {
      ordered.add(withSortOrder(sorted.get(index), index + 1));
    }
    return ordered;
  }

  private ConfigValidationFinding finding(
      ConfigValidationCommand command,
      String runId,
      ConfigValidationSeverity severity,
      String code,
      String jsonPath,
      String messageKey,
      Map<String, String> messageParams,
      String remediation,
      boolean blocking) {
    String findingId = deterministicId(runId, code, jsonPath, canonicalMap(messageParams));
    return new ConfigValidationFinding(
        findingId,
        runId,
        severity,
        code,
        jsonPath,
        command.artifactId() + ":" + command.versionId(),
        messageKey,
        Map.copyOf(messageParams),
        remediation,
        blocking,
        0);
  }

  private ConfigValidationFinding withSortOrder(ConfigValidationFinding finding, int sortOrder) {
    return new ConfigValidationFinding(
        finding.findingId(),
        finding.runId(),
        finding.severity(),
        finding.code(),
        finding.jsonPath(),
        finding.artifactRef(),
        finding.messageKey(),
        finding.messageParams(),
        finding.remediation(),
        finding.blocking(),
        sortOrder);
  }

  private int severityRank(ConfigValidationSeverity severity) {
    return switch (severity) {
      case INFO -> 1;
      case WARNING -> 2;
      case ERROR -> 3;
      case BLOCKER -> 4;
    };
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

  private String canonicalCommand(ConfigValidationCommand command) {
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.artifactId(),
        command.versionId(),
        command.artifactType(),
        command.schemaVersion(),
        canonicalMap(command.payload()),
        command.payloadHash() == null ? "" : command.payloadHash(),
        canonicalPolicies(command.policies()),
        command.validationScope() == null ? "" : command.validationScope(),
        command.correlationId());
  }

  private String canonicalPolicies(List<ConfigValidationPolicy> policies) {
    return policies.stream()
        .sorted(Comparator.comparing(ConfigValidationPolicy::policyId).thenComparing(ConfigValidationPolicy::version))
        .map(
            policy ->
                policy.policyId()
                    + "@"
                    + policy.version()
                    + "{required="
                    + String.join(",", policy.requiredPayloadFields().stream().sorted().toList())
                    + ";schemas="
                    + String.join(",", policy.supportedSchemaVersions().stream().sorted().toList())
                    + ";messages="
                    + canonicalMap(policy.messageKeys())
                    + "}")
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String canonicalFindings(List<ConfigValidationFinding> findings) {
    return findings.stream()
        .map(
            finding ->
                finding.sortOrder()
                    + "|"
                    + finding.severity()
                    + "|"
                    + finding.code()
                    + "|"
                    + finding.jsonPath()
                    + "|"
                    + finding.messageKey()
                    + "|"
                    + canonicalMap(finding.messageParams())
                    + "|"
                    + finding.blocking())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("no-findings");
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

  private record IdempotencyEntry(String requestHash, ConfigValidationRun run) {}
}
