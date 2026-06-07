package com.wcpe.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ComplianceContractTestCatalog {
  public static final String REPORT_SCHEMA = "compliance-contract-results.v1";
  public static final String FIXTURE_INDEX_SCHEMA = "compliance-fixture-index.v1";

  private static final Set<String> REQUIRED_EVENT_HEADERS =
      Set.of(
          "tenantId",
          "eventId",
          "eventType",
          "eventVersion",
          "sourceService",
          "actorId",
          "correlationId",
          "causationId",
          "idempotencyKey",
          "occurredAt");

  private ComplianceContractTestCatalog() {}

  public static List<FixtureDefinition> baselineFixtures() {
    return List.of(
        fixture("federal-rule-pack-lifecycle", "PII-15-S01", "schema:compliance:federal-rule-pack:v1", "internal"),
        fixture("state-rule-pack-precedence", "PII-15-S02", "schema:compliance:state-rule-pack:v1", "internal"),
        fixture("high-cost-evaluation-fail-closed", "PII-15-S03", "schema:compliance:high-cost:v1", "internal"),
        fixture("apr-advisory-ledger", "PII-15-S04", "schema:compliance:apr-advisory:v1", "internal"),
        fixture("fair-lending-monitoring-redacted", "PII-15-S05", "schema:compliance:fair-lending:v1", "protected"),
        fixture("reason-code-catalog-replay", "PII-15-S06", "schema:compliance:reason-code:v1", "internal"),
        fixture("audit-snapshot-verify-legal-hold", "PII-15-S07", "schema:compliance:audit-snapshot:v1", "protected"),
        fixture("regulatory-approval-sod", "PII-15-S08", "schema:compliance:regulatory-approval:v1", "internal"),
        fixture("compliance-export-redacted-manifest", "PII-15-S09", "schema:compliance:export:v1", "protected"));
  }

  public static RestContract restContract() {
    return new RestContract(
        "POST",
        "/api/v1/tenants/{tenantId}/compliance-contract-tests",
        "GET",
        "/api/v1/tenants/{tenantId}/compliance-contract-tests/{id}",
        List.of("Authorization", "Idempotency-Key", "X-Correlation-Id"),
        List.of("tenantId", "requestId", "actorId", "effectiveDate", "sourceSystem", "payload", "clientContext"),
        List.of("id", "status", "version", "resultSummary", "validationMessages", "auditRef", "replayRef", "correlationId"),
        List.of(
            "VALIDATION_FAILED",
            "UNAUTHENTICATED",
            "TENANT_ACCESS_DENIED",
            "NOT_FOUND",
            "VERSION_CONFLICT",
            "IDEMPOTENCY_CONFLICT",
            "POLICY_NOT_SATISFIED",
            "DEPENDENCY_UNAVAILABLE"));
  }

  public static List<EventContract> eventContracts() {
    return List.of(
        event("FederalComplianceRulePackResolved.v1"),
        event("StateComplianceRulePackResolved.v1"),
        event("HighCostEvaluationCompleted.v1"),
        event("AprAdvisoryCompleted.v1"),
        event("FairLendingMonitoringCompleted.v1"),
        event(ComplianceReasonCodeCatalog.PUBLISHED_EVENT_TYPE),
        event(ComplianceAuditSnapshotService.CREATED_EVENT_TYPE),
        event(RegulatoryConfigApprovalService.PUBLISHED_EVENT_TYPE),
        event(ComplianceExportService.COMPLETED_EVENT_TYPE));
  }

  public static ContractReport contractReport(List<ContractTestResult> results, String environment) {
    List<ContractTestResult> normalized = results == null ? List.of() : List.copyOf(results);
    String env = requireNonBlank(environment, "environment");
    List<String> failures = normalized.stream().flatMap(result -> result.failures().stream()).sorted().toList();
    String resultHash = replayHash(env, normalized.stream().map(ContractTestResult::hashMaterial).sorted().toList());
    return new ContractReport(REPORT_SCHEMA, env, normalized, resultHash, failures);
  }

  public static List<String> validateFixtureIndex(List<FixtureDefinition> fixtures) {
    List<String> defects = new ArrayList<>();
    if (fixtures == null || fixtures.isEmpty()) {
      return List.of("fixture index must include S01-S09 fixtures");
    }
    Set<String> storyIds = fixtures.stream().map(FixtureDefinition::storyId).collect(java.util.stream.Collectors.toSet());
    for (int story = 1; story <= 9; story++) {
      String storyId = String.format(Locale.ROOT, "PII-15-S%02d", story);
      if (!storyIds.contains(storyId)) {
        defects.add("missing fixture for " + storyId);
      }
    }
    for (FixtureDefinition fixture : fixtures) {
      if (isBlank(fixture.fixtureId())) {
        defects.add("fixtureId must be present");
      }
      if (isBlank(fixture.schemaRef())) {
        defects.add(fixture.fixtureId() + ": schemaRef must be present");
      }
      if (fixture.configVersionRefs().isEmpty()) {
        defects.add(fixture.fixtureId() + ": configVersionRefs must be present");
      }
      if (isBlank(fixture.expectedHash()) || !fixture.expectedHash().startsWith("sha256:")) {
        defects.add(fixture.fixtureId() + ": expectedHash must be sha256");
      }
      if (isBlank(fixture.sensitivity())) {
        defects.add(fixture.fixtureId() + ": sensitivity must be present");
      }
      if (!"synthetic".equals(fixture.owner())) {
        defects.add(fixture.fixtureId() + ": owner must be synthetic");
      }
    }
    return List.copyOf(defects);
  }

  public static List<String> validateEventContracts(List<EventContract> contracts) {
    List<String> defects = new ArrayList<>();
    if (contracts == null || contracts.isEmpty()) {
      return List.of("event contracts must be present");
    }
    for (EventContract contract : contracts) {
      if (isBlank(contract.eventType()) || !contract.eventType().endsWith(".v1")) {
        defects.add(contract.eventType() + ": eventType must be versioned");
      }
      if (!contract.headers().containsAll(REQUIRED_EVENT_HEADERS)) {
        defects.add(contract.eventType() + ": required envelope headers missing");
      }
      if (isBlank(contract.keyStrategy()) || isBlank(contract.partitionStrategy())) {
        defects.add(contract.eventType() + ": key and partition strategy required");
      }
      if (!contract.payloadFields().containsAll(List.of("tenantId", "status", "version"))) {
        defects.add(contract.eventType() + ": payload fields missing");
      }
    }
    return List.copyOf(defects);
  }

  public static String fixtureIndexJson(List<FixtureDefinition> fixtures) {
    List<FixtureDefinition> sorted =
        (fixtures == null ? List.<FixtureDefinition>of() : fixtures).stream()
            .sorted(Comparator.comparing(FixtureDefinition::storyId).thenComparing(FixtureDefinition::fixtureId))
            .toList();
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"schema\": \"").append(FIXTURE_INDEX_SCHEMA).append("\",\n");
    json.append("  \"fixtures\": [\n");
    for (int i = 0; i < sorted.size(); i++) {
      FixtureDefinition fixture = sorted.get(i);
      json.append("    {");
      json.append("\"fixtureId\": \"").append(fixture.fixtureId()).append("\", ");
      json.append("\"storyId\": \"").append(fixture.storyId()).append("\", ");
      json.append("\"schemaRef\": \"").append(fixture.schemaRef()).append("\", ");
      json.append("\"configVersionRefs\": ").append(jsonArray(fixture.configVersionRefs())).append(", ");
      json.append("\"expectedHash\": \"").append(fixture.expectedHash()).append("\", ");
      json.append("\"sensitivity\": \"").append(fixture.sensitivity()).append("\", ");
      json.append("\"owner\": \"").append(fixture.owner()).append("\"");
      json.append("}");
      if (i + 1 < sorted.size()) {
        json.append(",");
      }
      json.append("\n");
    }
    json.append("  ]\n");
    json.append("}\n");
    return json.toString();
  }

  public static String reportJson(ContractReport report) {
    Objects.requireNonNull(report, "report must be provided");
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"schema\": \"").append(report.schema()).append("\",\n");
    json.append("  \"environment\": \"").append(report.environment()).append("\",\n");
    json.append("  \"resultHash\": \"").append(report.resultHash()).append("\",\n");
    json.append("  \"failures\": ").append(jsonArray(report.failures())).append(",\n");
    json.append("  \"results\": [\n");
    for (int i = 0; i < report.results().size(); i++) {
      ContractTestResult result = report.results().get(i);
      json.append("    {");
      json.append("\"testName\": \"").append(result.testName()).append("\", ");
      json.append("\"fixtureIds\": ").append(jsonArray(result.fixtureIds())).append(", ");
      json.append("\"schemaVersions\": ").append(jsonArray(result.schemaVersions())).append(", ");
      json.append("\"resultHash\": \"").append(result.resultHash()).append("\", ");
      json.append("\"failures\": ").append(jsonArray(result.failures()));
      json.append("}");
      if (i + 1 < report.results().size()) {
        json.append(",");
      }
      json.append("\n");
    }
    json.append("  ]\n");
    json.append("}\n");
    return json.toString();
  }

  public static String replayHash(String tenantId, List<String> fixtureIds) {
    String tenant = requireNonBlank(tenantId, "tenantId");
    List<String> fixtures = fixtureIds == null ? List.of() : fixtureIds.stream().map(ComplianceContractTestCatalog::requireValue).sorted().toList();
    return "sha256:" + sha256("compliance-contract-replay|" + tenant + "|" + String.join(",", fixtures));
  }

  private static FixtureDefinition fixture(String fixtureId, String storyId, String schemaRef, String sensitivity) {
    List<String> configVersionRefs = List.of("config:" + storyId + ":synthetic-contract:v1");
    String expectedHash = replayHash("synthetic-tenant", List.of(fixtureId, schemaRef, storyId));
    return new FixtureDefinition(
        fixtureId, storyId, schemaRef, configVersionRefs, expectedHash, sensitivity, "synthetic");
  }

  private static EventContract event(String eventType) {
    return new EventContract(
        eventType,
        List.copyOf(REQUIRED_EVENT_HEADERS),
        List.of("id", "tenantId", "status", "version", "summary", "sourceRefs"),
        "tenantId:id",
        "tenant-scoped-key",
        "retry-with-idempotency-key-and-dlq-on-exhaustion");
  }

  private static String jsonArray(List<String> values) {
    return "[" + String.join(", ", values.stream().map(value -> "\"" + value + "\"").toList()) + "]";
  }

  private static String requireValue(String value) {
    return requireNonBlank(value, "value");
  }

  private static String requireNonBlank(String value, String field) {
    if (isBlank(value)) {
      throw new ComplianceShellValidationError("Compliance contract test validation failed.", List.of(field + " must be provided"));
    }
    return value.trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
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
      throw new IllegalStateException("SHA-256 digest is required for compliance contract tests", exception);
    }
  }

  public record FixtureDefinition(
      String fixtureId,
      String storyId,
      String schemaRef,
      List<String> configVersionRefs,
      String expectedHash,
      String sensitivity,
      String owner) {
    public FixtureDefinition {
      configVersionRefs = configVersionRefs == null ? List.of() : List.copyOf(configVersionRefs);
    }
  }

  public record RestContract(
      String commandMethod,
      String commandPath,
      String readMethod,
      String readPath,
      List<String> requiredHeaders,
      List<String> requestFields,
      List<String> responseFields,
      List<String> errorCodes) {
    public RestContract {
      requiredHeaders = requiredHeaders == null ? List.of() : List.copyOf(requiredHeaders);
      requestFields = requestFields == null ? List.of() : List.copyOf(requestFields);
      responseFields = responseFields == null ? List.of() : List.copyOf(responseFields);
      errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
    }
  }

  public record EventContract(
      String eventType,
      List<String> headers,
      List<String> payloadFields,
      String keyStrategy,
      String partitionStrategy,
      String retryDlqSemantics) {
    public EventContract {
      headers = headers == null ? List.of() : List.copyOf(headers);
      payloadFields = payloadFields == null ? List.of() : List.copyOf(payloadFields);
    }
  }

  public record ContractTestResult(
      String testName,
      List<String> fixtureIds,
      List<String> schemaVersions,
      String resultHash,
      List<String> failures) {
    public ContractTestResult {
      fixtureIds = fixtureIds == null ? List.of() : List.copyOf(fixtureIds);
      schemaVersions = schemaVersions == null ? List.of() : List.copyOf(schemaVersions);
      failures = failures == null ? List.of() : List.copyOf(failures);
    }

    String hashMaterial() {
      return String.join("|", testName, String.join(",", fixtureIds), String.join(",", schemaVersions), resultHash);
    }
  }

  public record ContractReport(
      String schema,
      String environment,
      List<ContractTestResult> results,
      String resultHash,
      List<String> failures) {
    public ContractReport {
      results = results == null ? List.of() : List.copyOf(results);
      failures = failures == null ? List.of() : List.copyOf(failures);
    }
  }
}
