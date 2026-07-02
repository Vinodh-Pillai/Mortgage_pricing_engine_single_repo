package com.wcpe.margin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class MarginReplayService {
  public static final String EXACT_MARGIN_COMP_MANIFEST = "EXACT_MARGIN_COMP_MANIFEST";
  public static final String DOMAIN = "MARGIN_COMPENSATION";
  public static final String REPLAY_PERMISSION = "pricing.replay.margin_comp.run";
  public static final String SENSITIVE_REPLAY_PERMISSION = "pricing.replay.margin_comp.view_sensitive";
  public static final String QUOTE_REPLAY_API = "POST /api/v1/tenants/{tenantId}/quotes/{quoteId}/replay";
  public static final String FIXTURE_REPLAY_API = "POST /api/v1/tenants/{tenantId}/test-fixtures/margin-comp/replay";

  private static final List<String> REQUIRED_WATERFALL_ORDER = List.of(
      "BASE_PRICING",
      "ADJUSTMENTS_OVERLAYS",
      "COMPANY_MARGIN",
      "CHANNEL_MARGIN",
      "SRP",
      "BRANCH_OVERLAY",
      "LO_COMPENSATION",
      "BROKER_COMPENSATION",
      "PROFITABILITY_FLOOR",
      "CONCESSIONS_COMPLIANCE_BEST_EXECUTION");

  public final AtomicInteger marginReplayRunTotal = new AtomicInteger();
  public final AtomicInteger marginReplayHashMismatchTotal = new AtomicInteger();
  public final AtomicInteger marginReplayUnauthorizedTotal = new AtomicInteger();

  private final Clock clock;
  private final Store store;

  public MarginReplayService(Clock clock) {
    this(clock, MarginDurableStores.replayStore());
  }

  MarginReplayService(Clock clock, Store store) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.store = Objects.requireNonNull(store, "store is required");
  }

  public ReplayFixture registerFixture(String tenantId, ReplayFixture fixture) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    Objects.requireNonNull(fixture, "fixture is required");
    if (!tenantId.equals(fixture.tenantId())) {
      throw new MarginReplayException("TENANT_ACCESS_DENIED");
    }
    store.fixtureCatalog().put(new FixtureKey(tenantId, fixture.fixtureId()), fixture);
    return fixture;
  }

  public ReplayResult runFixtureReplay(ReplayCommand command) {
    requireDurableStoreOrExplicitTestHarness();
    Objects.requireNonNull(command, "command is required");
    requireText(command.fixtureId(), "fixtureId");
    ReplayFixture fixture = store.fixtureCatalog().get(new FixtureKey(command.tenantId(), command.fixtureId()));
    if (fixture == null || !fixture.active()) {
      throw new MarginReplayException("REPLAY_FIXTURE_MISSING");
    }
    return runReplay(command, fixture);
  }

  public ReplayResult runQuoteReplay(ReplayCommand command) {
    requireDurableStoreOrExplicitTestHarness();
    Objects.requireNonNull(command, "command is required");
    requireText(command.quoteId(), "quoteId");
    ReplayFixture fixture = Optional.ofNullable(command.fixtureId())
        .map(fixtureId -> store.fixtureCatalog().get(new FixtureKey(command.tenantId(), fixtureId)))
        .orElseThrow(() -> new MarginReplayException("REPLAY_FIXTURE_MISSING"));
    return runReplay(command, fixture);
  }

  public ReplayResult applyVisibility(String viewerPermission, ReplayResult result) {
    Objects.requireNonNull(result, "result is required");
    boolean sensitiveAllowed = SENSITIVE_REPLAY_PERMISSION.equals(viewerPermission);
    if (sensitiveAllowed) {
      return result;
    }
    List<WaterfallStep> filteredSteps = result.versionManifest().waterfallSteps().stream()
        .map(step -> step.sensitive()
            ? new WaterfallStep(step.stepType(), step.sourceVersionId(), null, null, null,
                step.visibilityClassification(), step.reasonCode(), null, true)
            : step)
        .toList();
    VersionManifest filteredManifest = new VersionManifest(result.versionManifest().manifestId(),
        result.versionManifest().policyVersionRefs(), filteredSteps, result.versionManifest().eventsObserved(),
        result.versionManifest().runEnvironment());
    return new ReplayResult(result.replayId(), result.matchStatus(), result.expectedHash(), result.actualHash(),
        result.mismatches(), filteredManifest, result.auditEvidenceId(),
        RoleFilteredQuoteResponse.from(filteredSteps), result.correlationId());
  }

  public Optional<ReplayRun> replayRun(String tenantId, String replayId) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    requireText(replayId, "replayId");
    return Optional.ofNullable(store.replayRuns().get(replayId)).filter(run -> tenantId.equals(run.tenantId()));
  }

  public List<DecisionReplayedEvent> outboxEvents() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.outbox());
  }

  public List<AuditEvidence> auditPackages() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.auditPackages());
  }

  private void requireDurableStoreOrExplicitTestHarness() {
    store.requireAvailable();
  }

  public static void assertRequiredWaterfallOrder(List<WaterfallStep> steps) {
    Objects.requireNonNull(steps, "steps are required");
    List<String> actual = steps.stream().map(WaterfallStep::stepType).toList();
    int cursor = 0;
    for (String required : REQUIRED_WATERFALL_ORDER) {
      int found = actual.subList(cursor, actual.size()).indexOf(required);
      if (found < 0) {
        throw new MarginReplayException("REPLAY_VERSION_MANIFEST_INCOMPLETE");
      }
      cursor += found + 1;
    }
  }

  public static String manifestHash(VersionManifest manifest) {
    Objects.requireNonNull(manifest, "manifest is required");
    return stableHash(
        manifest.manifestId(),
        canonicalMap(manifest.policyVersionRefs()),
        manifest.waterfallSteps().stream()
            .map(step -> step.stepType() + ':' + step.sourceVersionId() + ':' + step.amountRef() + ':'
                + step.conversionRef() + ':' + step.roundingMode())
            .toList(),
        List.copyOf(manifest.eventsObserved()),
        canonicalMap(manifest.runEnvironment()));
  }

  public static String resultHash(List<WaterfallStep> steps) {
    Objects.requireNonNull(steps, "steps are required");
    return stableHash(steps.stream()
        .map(step -> step.stepType() + ':' + step.hashContribution())
        .toList());
  }

  private ReplayResult runReplay(ReplayCommand command, ReplayFixture fixture) {
    validateCommand(command);
    Instant started = Instant.now(clock);
    assertRequiredWaterfallOrder(command.versionManifest().waterfallSteps());
    String actualManifestHash = manifestHash(command.versionManifest());
    if (!fixture.expectedManifestHash().equals(actualManifestHash)) {
      throw new MarginReplayException("REPLAY_VERSION_MANIFEST_INCOMPLETE");
    }
    String actualResultHash = resultHash(command.versionManifest().waterfallSteps());
    List<ReplayMismatch> mismatches = classifyMismatches(fixture.expectedStepHashes(),
        command.versionManifest().waterfallSteps());
    String matchStatus = mismatches.isEmpty() && fixture.expectedResultHash().equals(actualResultHash)
        ? "MATCH"
        : "MISMATCH";
    if (!mismatches.isEmpty() || !fixture.expectedResultHash().equals(actualResultHash)) {
      marginReplayHashMismatchTotal.incrementAndGet();
    }
    String replayId = UUID.randomUUID().toString();
    String evidenceId = "audit:" + replayId;
    ReplayRun run = new ReplayRun(command.tenantId(), replayId, command.quoteId(), command.fixtureId(), command.mode(),
        matchStatus, fixture.expectedResultHash(), actualResultHash, mismatches, evidenceId, started,
        Instant.now(clock));
    store.replayRuns().put(replayId, run);
    marginReplayRunTotal.incrementAndGet();
    DecisionReplayedEvent event = new DecisionReplayedEvent(command.tenantId(), replayId, DOMAIN, matchStatus,
        fixture.expectedResultHash(), actualResultHash, mismatchClass(mismatches), command.correlationId(),
        Instant.now(clock));
    store.outbox().add(event);
    AuditEvidence evidence = new AuditEvidence(evidenceId, command.tenantId(), replayId, fixture.fixturePath(),
        command.versionManifest(), command.versionManifest().waterfallSteps(),
        RoleFilteredQuoteResponse.from(command.versionManifest().waterfallSteps()), command.versionManifest().eventsObserved(),
        command.versionManifest().runEnvironment(), actualResultHash, true,
        Duration.between(started, Instant.now(clock)).toMillis());
    store.auditPackages().add(evidence);
    return new ReplayResult(replayId, matchStatus, fixture.expectedResultHash(), actualResultHash, mismatches,
        command.versionManifest(), evidenceId, evidence.roleFilteredOutputs(), command.correlationId());
  }

  private void validateCommand(ReplayCommand command) {
    requireText(command.tenantId(), "tenantId");
    requireText(command.actorId(), "actorId");
    requireText(command.mode(), "mode");
    requireText(command.correlationId(), "correlationId");
    if (!EXACT_MARGIN_COMP_MANIFEST.equals(command.mode())) {
      throw new MarginReplayException("REPLAY_VERSION_MANIFEST_INCOMPLETE");
    }
    if (!command.viewerRoles().contains(REPLAY_PERMISSION)) {
      marginReplayUnauthorizedTotal.incrementAndGet();
      throw new MarginReplayException("REPLAY_UNAUTHORIZED");
    }
    Objects.requireNonNull(command.versionManifest(), "versionManifest is required");
  }

  private static List<ReplayMismatch> classifyMismatches(Map<String, String> expectedStepHashes,
      List<WaterfallStep> actualSteps) {
    Map<String, String> expected = new TreeMap<>(Objects.requireNonNull(expectedStepHashes,
        "expectedStepHashes are required"));
    Map<String, WaterfallStep> actualByStep = new HashMap<>();
    actualSteps.forEach(step -> actualByStep.put(step.stepType(), step));
    List<ReplayMismatch> mismatches = new ArrayList<>();
    expected.forEach((stepType, expectedHash) -> {
      WaterfallStep actual = actualByStep.get(stepType);
      if (actual == null) {
        mismatches.add(new ReplayMismatch(stepType, expectedHash, null, "MISSING_STEP"));
      } else if (!expectedHash.equals(actual.hashContribution())) {
        mismatches.add(new ReplayMismatch(stepType, expectedHash, actual.hashContribution(), "STEP_HASH_MISMATCH"));
      }
    });
    actualByStep.keySet().stream()
        .filter(stepType -> !expected.containsKey(stepType))
        .sorted(Comparator.naturalOrder())
        .forEach(stepType -> mismatches.add(new ReplayMismatch(stepType, null,
            actualByStep.get(stepType).hashContribution(), "UNEXPECTED_STEP")));
    return List.copyOf(mismatches);
  }

  private static String mismatchClass(List<ReplayMismatch> mismatches) {
    return mismatches.isEmpty() ? "NONE" : mismatches.get(0).classification();
  }

  private static String canonicalMap(Map<String, String> values) {
    return new TreeMap<>(values).entrySet().stream()
        .map(entry -> entry.getKey() + '=' + entry.getValue())
        .toList()
        .toString();
  }

  private static String stableHash(Object... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (Object value : values) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '|');
      }
      byte[] hash = digest.digest();
      StringBuilder builder = new StringBuilder();
      for (byte b : hash) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new MarginReplayException(field + " is required");
    }
  }

  interface Store {
    Map<FixtureKey, ReplayFixture> fixtureCatalog();
    Map<String, ReplayRun> replayRuns();
    List<DecisionReplayedEvent> outbox();
    List<AuditEvidence> auditPackages();

    default void requireAvailable() {}

    static Store failClosed(String component) {
      return new Store() {
        @Override public void requireAvailable() {
          ProcessLocalStatePolicy.requireDurableStoreOrExplicitTestHarness(false, component);
        }
        @Override public Map<FixtureKey, ReplayFixture> fixtureCatalog() { return unavailable(); }
        @Override public Map<String, ReplayRun> replayRuns() { return unavailable(); }
        @Override public List<DecisionReplayedEvent> outbox() { return unavailable(); }
        @Override public List<AuditEvidence> auditPackages() { return unavailable(); }
        private <T> T unavailable() {
          requireAvailable();
          throw new AssertionError("unreachable");
        }
      };
    }
  }

  record FixtureKey(String tenantId, String fixtureId) {}

  public record ReplayFixture(String tenantId, String fixtureId, String name, String fixturePath,
      String expectedManifestHash, String expectedResultHash, Map<String, String> expectedStepHashes,
      List<String> riskTags, boolean active) {
    public ReplayFixture {
      requireText(tenantId, "tenantId");
      requireText(fixtureId, "fixtureId");
      requireText(name, "name");
      requireText(fixturePath, "fixturePath");
      requireText(expectedManifestHash, "expectedManifestHash");
      requireText(expectedResultHash, "expectedResultHash");
      expectedStepHashes = Map.copyOf(Objects.requireNonNull(expectedStepHashes, "expectedStepHashes are required"));
      riskTags = List.copyOf(Objects.requireNonNull(riskTags, "riskTags are required"));
    }
  }

  public record ReplayCommand(String tenantId, String actorId, String quoteId, String fixtureId, String mode,
      List<String> viewerRoles, VersionManifest versionManifest, String correlationId) {
    public ReplayCommand {
      viewerRoles = List.copyOf(Objects.requireNonNull(viewerRoles, "viewerRoles are required"));
    }
  }

  public record VersionManifest(String manifestId, Map<String, String> policyVersionRefs,
      List<WaterfallStep> waterfallSteps, List<String> eventsObserved, Map<String, String> runEnvironment) {
    public VersionManifest {
      requireText(manifestId, "manifestId");
      policyVersionRefs = Map.copyOf(Objects.requireNonNull(policyVersionRefs, "policyVersionRefs are required"));
      waterfallSteps = List.copyOf(Objects.requireNonNull(waterfallSteps, "waterfallSteps are required"));
      eventsObserved = List.copyOf(Objects.requireNonNull(eventsObserved, "eventsObserved are required"));
      runEnvironment = Map.copyOf(Objects.requireNonNull(runEnvironment, "runEnvironment is required"));
    }
  }

  public record WaterfallStep(String stepType, String sourceVersionId, String amountRef, String conversionRef,
      String roundingMode, String visibilityClassification, String reasonCode, String hashContribution,
      boolean sensitive) {
    public WaterfallStep {
      requireText(stepType, "stepType");
      requireText(sourceVersionId, "sourceVersionId");
      requireText(visibilityClassification, "visibilityClassification");
      requireText(reasonCode, "reasonCode");
    }
  }

  public record ReplayMismatch(String stepType, String expectedHash, String actualHash, String classification) {}

  public record ReplayResult(String replayId, String matchStatus, String expectedHash, String actualHash,
      List<ReplayMismatch> mismatches, VersionManifest versionManifest, String auditEvidenceId,
      RoleFilteredQuoteResponse roleFilteredQuoteResponse, String correlationId) {
    public ReplayResult {
      mismatches = List.copyOf(Objects.requireNonNull(mismatches, "mismatches are required"));
    }
  }

  public record RoleFilteredQuoteResponse(List<String> visibleStepTypes, List<String> redactedStepTypes) {
    static RoleFilteredQuoteResponse from(List<WaterfallStep> steps) {
      return new RoleFilteredQuoteResponse(
          steps.stream().filter(step -> !step.sensitive()).map(WaterfallStep::stepType).toList(),
          steps.stream().filter(WaterfallStep::sensitive).map(WaterfallStep::stepType).toList());
    }
  }

  public record ReplayRun(String tenantId, String replayId, String quoteId, String fixtureId, String mode,
      String status, String expectedHash, String actualHash, List<ReplayMismatch> mismatches, String evidenceId,
      Instant startedAt, Instant completedAt) {}

  public record DecisionReplayedEvent(String tenantId, String replayId, String domain, String matchStatus,
      String expectedHash, String actualHash, String mismatchClass, String correlationId, Instant occurredAt) {}

  public record AuditEvidence(String evidenceId, String tenantId, String replayId, String normalizedScenario,
      VersionManifest versionManifest, List<WaterfallStep> calculationLedger, RoleFilteredQuoteResponse roleFilteredOutputs,
      List<String> eventsObserved, Map<String, String> runEnvironment, String replayHash, boolean excludesBorrowerPii,
      long durationMs) {}

  public static final class MarginReplayException extends RuntimeException {
    public MarginReplayException(String message) {
      super(message);
    }
  }
}
