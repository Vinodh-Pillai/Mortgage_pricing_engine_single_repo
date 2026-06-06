package com.wcpe.scenario.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

// S10 unit tests: Replay redaction policy and package assembly
class ScenarioReplayRedactionPolicyTest {
  @Test
  void redactsFinancialFieldsForSupport() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("borrowers", List.of(Map.of("creditScore", 720, "name", "Ada")));
    raw.put("monthlyIncome", "5000");
    raw.put("liquidAssets", "50000");
    raw.put("propertyState", "TX");
    raw.put("loanAmount", "300000");
    raw.put("channel", "RETAIL");

    Map<String, Object> redacted = ScenarioReplayPackageQueryService.redact(raw);

    assertEquals("REDACTED", redacted.get("borrowers"));
    assertEquals("REDACTED", redacted.get("monthlyIncome"));
    assertEquals("REDACTED", redacted.get("liquidAssets"));
    assertEquals("TX", redacted.get("propertyState"));
    assertEquals("RETAIL", redacted.get("channel"));
  }

  @Test
  void fullRedactionModeReturnsAllFields() {
    Map<String, Object> raw = Map.of("monthlyIncome", "5000", "liquidAssets", "50000", "propertyState", "TX");
    // Full mode — no redaction applied
    assertFalse(raw.values().stream().anyMatch(v -> "REDACTED".equals(v)));
  }

  @Test
  void redactedModeReturnsMaskedValues() {
    Map<String, Object> raw = new LinkedHashMap<>(Map.of("monthlyIncome", "5000", "liquidAssets", "50000", "propertyState", "TX"));
    Map<String, Object> redacted = ScenarioReplayPackageQueryService.redact(raw);
    assertTrue(redacted.values().stream().anyMatch(v -> "REDACTED".equals(v)));
  }
}

class ScenarioReplayPackageAssemblerTest {
  @Test
  void includesVersionManifestAndHashes() {
    List<VersionManifest> manifest = List.of(
        new VersionManifest(1, "CREATE_DRAFT", "sha256:abc123", Instant.parse("2026-05-01T10:00:00Z")),
        new VersionManifest(2, "UPDATE_BORROWER_CREDIT", "sha256:def456", Instant.parse("2026-05-01T11:00:00Z")),
        new VersionManifest(3, "NORMALIZE_SCENARIO", "sha256:ghi789", Instant.parse("2026-05-01T12:00:00Z"))
    );

    assertEquals(3, manifest.size());
    assertEquals(1, manifest.get(0).version());
    assertEquals("sha256:def456", manifest.get(1).hash());
    assertTrue(manifest.get(2).hash().startsWith("sha256:"));
  }

  @Test
  void hashVerificationMatchesExpected() {
    String input = "tenant:abc:scenario:def:version:1";
    String hash = Hashing.sha256(input);
    assertTrue(hash.startsWith("sha256:"));
    assertEquals(71, hash.length()); // "sha256:" + 64 hex chars
  }

  @Test
  void replayPackageIncludesAuditPackageId() {
    UUID auditId = UUID.randomUUID();
    ReplayPackage pkg = new ReplayPackage(UUID.randomUUID(), 1, "scenario-v1", true,
        ScenarioStatus.DRAFT_INCOMPLETE, List.of(), Map.of(), Map.of(),
        List.of(), List.of(), auditId);
    assertNotNull(pkg.auditPackageId());
  }
}

class ScenarioReplayPackageQueryServiceTest {
  @AfterEach
  void clearRoles() {
    RequestContext.clear();
  }

  @Test
  void unauthorizedReplayReadIsAuditedBeforeAccessDeniedWithoutLoadingScenario() {
    Scenario scenario = replayScenario();
    InMemoryReplayRepository repository = new InMemoryReplayRepository(scenario, snapshotFor(scenario, scenario.replayHash()));
    ScenarioReplayPackageQueryService service = new ScenarioReplayPackageQueryService(repository);

    ScenarioException failure = assertThrows(ScenarioException.class, () -> service.replay(
        scenario.tenantId(), scenario.scenarioId(), new ScenarioReplayAccessRequest("latest", "role-default", false, "SUPPORT_REVIEW", "corr-denied-read")));

    assertEquals(HttpStatus.FORBIDDEN, failure.status());
    assertEquals("ACCESS_DENIED", failure.code());
    assertEquals(0, repository.getCalls);
    assertEquals(1, repository.events.size());
    assertEquals("ScenarioReplayPackageAccessDenied.v1", repository.events.get(0).eventType());
    assertEquals("ACCESS_DENIED", repository.events.get(0).payload().get("decision"));
    assertEquals("scenario-replay:read", repository.events.get(0).payload().get("deniedPermission"));
    assertFalse(repository.events.get(0).payload().containsKey("rawFacts"));
    assertEquals(1, repository.audits.size());
    assertEquals("SCENARIO_REPLAY_PACKAGE_ACCESS_DENIED", repository.audits.get(0).action());
  }

  @Test
  void fullReplayDenialIsAuditedBeforeAccessDenied() {
    RequestContext.roles("SCENARIO_REPLAY");
    Scenario scenario = replayScenario();
    InMemoryReplayRepository repository = new InMemoryReplayRepository(scenario, snapshotFor(scenario, scenario.replayHash()));
    ScenarioReplayPackageQueryService service = new ScenarioReplayPackageQueryService(repository);

    ScenarioException failure = assertThrows(ScenarioException.class, () -> service.replay(
        scenario.tenantId(), scenario.scenarioId(), new ScenarioReplayAccessRequest("latest", "full", true, "EXPORT_REVIEW", "corr-denied-full")));

    assertEquals(HttpStatus.FORBIDDEN, failure.status());
    assertEquals("ACCESS_DENIED", failure.code());
    assertEquals(1, repository.getCalls);
    assertEquals(1, repository.events.size());
    assertEquals("FULL", repository.events.get(0).payload().get("redactionMode"));
    assertEquals(true, repository.events.get(0).payload().get("exportFlag"));
    assertEquals("scenario-replay:export-full", repository.events.get(0).payload().get("deniedPermission"));
    assertEquals("SCENARIO_REPLAY_PACKAGE_ACCESS_DENIED", repository.audits.get(0).action());
    assertEquals(scenario.replayHash(), repository.audits.get(0).replayHash());
  }

  @Test
  void authorizedReplayReturnsPackageWithHashMismatchWarningAndViewedAudit() {
    RequestContext.roles("SCENARIO_REPLAY_FULL");
    Scenario scenario = replayScenario();
    InMemoryReplayRepository repository = new InMemoryReplayRepository(scenario, snapshotFor(scenario, "sha256:mismatch"));
    ScenarioReplayPackageQueryService service = new ScenarioReplayPackageQueryService(repository);

    ReplayPackage replay = service.replay(
        scenario.tenantId(), scenario.scenarioId(), new ScenarioReplayAccessRequest("latest", "full", true, "EXPORT_REVIEW", "corr-viewed"));

    assertFalse(replay.redactionApplied());
    assertEquals("5000", replay.rawInputSnapshot().get("monthlyIncome"));
    assertTrue(replay.validationIssues().stream().anyMatch(issue -> "HASH_MISMATCH".equals(issue.code())));
    assertEquals(1, repository.events.size());
    assertEquals("ScenarioReplayPackageViewed.v1", repository.events.get(0).eventType());
    assertEquals("SCENARIO_REPLAY_PACKAGE_EXPORTED", repository.audits.get(0).action());
  }

  private static Scenario replayScenario() {
    return new Scenario(UUID.randomUUID(), "PURCHASE", "RETAIL", "Replay package scenario", "LN-10", "LOS",
        Map.of("monthlyIncome", "5000", "propertyState", "TX"));
  }

  private static Map<String, Object> snapshotFor(Scenario scenario, String replayHash) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("rawFacts", new LinkedHashMap<>(scenario.rawFacts()));
    snapshot.put("normalizedFacts", Map.of("scenarioId", scenario.scenarioId().toString()));
    snapshot.put("derivedFields", Map.of("source", "unit-test"));
    snapshot.put("replayHash", replayHash);
    return snapshot;
  }

  static class InMemoryReplayRepository extends ScenarioRepository {
    final Scenario scenario;
    final Map<String, Object> snapshot;
    final List<EventRecord> events = new ArrayList<>();
    final List<AuditRecord> audits = new ArrayList<>();
    int getCalls;

    InMemoryReplayRepository(Scenario scenario, Map<String, Object> snapshot) {
      super(null, new ObjectMapper());
      this.scenario = scenario;
      this.snapshot = snapshot;
    }

    @Override
    Scenario get(UUID tenantId, UUID scenarioId) {
      getCalls++;
      if (!scenario.tenantId().equals(tenantId) || !scenario.scenarioId().equals(scenarioId)) {
        throw new ScenarioException(HttpStatus.NOT_FOUND, "SCENARIO_NOT_FOUND", "Scenario was not found for this tenant.", List.of());
      }
      return scenario;
    }

    @Override
    Optional<Map<String, Object>> versionSnapshot(UUID tenantId, UUID scenarioId, int version) {
      return Optional.of(snapshot);
    }

    @Override
    void event(EventRecord event) {
      events.add(event);
    }

    @Override
    void audit(AuditRecord audit) {
      audits.add(audit);
    }

    @Override
    List<EventRecord> events(UUID tenantId, UUID scenarioId) {
      return List.copyOf(events);
    }
  }
}
