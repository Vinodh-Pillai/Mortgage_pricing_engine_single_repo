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
import java.util.stream.Collectors;

public final class MlAdvisoryControlService {
  public static final String GET_CONTROLS_ENDPOINT =
      "GET /api/v1/tenants/{tenantId}/ml-advisory/controls?channel=&productFamily=&advisoryType=";
  public static final String CREATE_CONTROL_ENDPOINT = "POST /api/v1/tenants/{tenantId}/ml-advisory/controls";
  public static final String ACTIVATE_KILL_SWITCH_ENDPOINT =
      "POST /api/v1/tenants/{tenantId}/ml-advisory/kill-switch:activate";
  public static final String DEACTIVATE_KILL_SWITCH_ENDPOINT =
      "POST /api/v1/tenants/{tenantId}/ml-advisory/kill-switch:deactivate";
  public static final String CAPTURE_FEATURE_SNAPSHOT_ENDPOINT =
      "POST /api/v1/tenants/{tenantId}/ml-advisory/feature-snapshots";
  public static final String GET_FEATURE_SNAPSHOT_ENDPOINT =
      "GET /api/v1/tenants/{tenantId}/ml-advisory/feature-snapshots/{snapshotId}";
  public static final String SEARCH_FEATURE_SNAPSHOTS_ENDPOINT =
      "GET /api/v1/tenants/{tenantId}/ml-advisory/feature-snapshots?scenarioId=&from=&to=";
  public static final String LIST_ADVISORIES_ENDPOINT =
      "GET /api/v1/tenants/{tenantId}/ml-advisory/advisories?scenarioId=&pricingResultId=";
  public static final String GET_ADVISORY_ENDPOINT =
      "GET /api/v1/tenants/{tenantId}/ml-advisory/advisories/{advisoryId}";
  public static final String EVALUATE_PRICING_ADVISORY_ENDPOINT =
      "POST /api/v1/tenants/{tenantId}/ml-advisory/pricing-advisories:evaluate";
  public static final String GET_MODEL_RUNTIME_HEALTH_ENDPOINT =
      "GET /api/v1/tenants/{tenantId}/ml-advisory/model-runtime/health";
  public static final String NON_AUTHORITATIVE_DISCLAIMER =
      "Advisory only — does not change final pricing or eligibility.";
  public static final String ADMIN_ROLE = "ML_ADVISORY_ADMIN";
  public static final String CAPTURE_ROLE = "ML_ADVISORY_CAPTURE";
  public static final String SNAPSHOT_READ_ROLE = "ML_ADVISORY_SNAPSHOT_READ";
  public static final String ADVISORY_READ_ROLE = "ML_ADVISORY_READ";
  public static final String CONTROL_CHANGED_EVENT = "MlAdvisoryControlChanged.v1";
  public static final String KILL_SWITCH_CHANGED_EVENT = "MlAdvisoryKillSwitchChanged.v1";
  public static final String FEATURE_SNAPSHOT_CAPTURED_EVENT = "MlFeatureSnapshotCaptured.v1";
  public static final String ML_ADVISORY_GENERATED_EVENT = "MlAdvisoryGenerated.v1";
  public static final String ML_PRICING_ADVISORY_GENERATED_EVENT = "MlPricingAdvisoryGenerated.v1";
  public static final String ML_PRICING_ADVISORY_SUPPRESSED_EVENT = "MlPricingAdvisorySuppressed.v1";
  public static final String ML_ADVISORY_VIEWED_EVENT = "MlAdvisoryViewed.v1";
  public static final String ML_MODEL_INFERENCE_COMPLETED_EVENT = "MlModelInferenceCompleted.v1";
  public static final String ML_MODEL_RUNTIME_HEALTH_CHANGED_EVENT = "MlModelRuntimeHealthChanged.v1";
  public static final String CACHE_KEY_PATTERN = "tenant:%s:ml-advisory:control:%s:v%d";
  public static final String GLOBAL_KILL_SWITCH_TENANT_ID = "GLOBAL";

  private final Clock clock;
  private final Map<String, MlAdvisoryControl> activeControls = new HashMap<>();
  private final Map<String, MlAdvisoryControlResponse> idempotencyResponses = new HashMap<>();
  private final Map<String, String> idempotencyHashes = new HashMap<>();
  private final Map<String, KillSwitchState> killSwitches = new HashMap<>();
  private final Map<String, FeatureSnapshot> featureSnapshots = new HashMap<>();
  private final Map<String, FeatureSnapshotResponse> snapshotIdempotencyResponses = new HashMap<>();
  private final Map<String, AdvisoryCard> advisoryCards = new HashMap<>();
  private final Map<String, AdvisoryCard> advisoryIdempotencyResponses = new HashMap<>();
  private final Map<String, PricingAdvisoryEvaluation> pricingAdvisoryIdempotencyResponses = new HashMap<>();
  private final List<ModelInferenceResult> modelInvocations = new ArrayList<>();
  private final Map<String, RuntimeHealth> runtimeHealth = new HashMap<>();
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
    killSwitches.put(killSwitchTenantId(command.tenantId()), state);
    recordKillSwitchEvent(command, state);
    return MlAdvisoryResult.success(state);
  }

  public MlAdvisoryResult<KillSwitchState> deactivateKillSwitch(KillSwitchCommand command) {
    MlAdvisoryResult<KillSwitchCommand> validation = validateKillSwitch(command, false);
    if (!validation.valid()) {
      return MlAdvisoryResult.failure(validation.errorCode().orElseThrow());
    }
    KillSwitchState state = newKillSwitch(command, false);
    killSwitches.put(killSwitchTenantId(command.tenantId()), state);
    recordKillSwitchEvent(command, state);
    return MlAdvisoryResult.success(state);
  }

  public MlAdvisoryResult<FeatureSnapshotResponse> captureFeatureSnapshot(CaptureFeatureSnapshotCommand command) {
    MlAdvisoryResult<CaptureFeatureSnapshotCommand> validation = validateSnapshotCapture(command);
    if (!validation.valid()) {
      return MlAdvisoryResult.failure(validation.errorCode().orElseThrow());
    }

    String idempotencyKey = command.tenantId() + ":snapshot:" + command.idempotencyKey();
    String requestHash = hash(canonicalSnapshot(command));
    String existingHash = idempotencyHashes.get(idempotencyKey);
    if (existingHash != null) {
      if (!existingHash.equals(requestHash)) {
        return MlAdvisoryResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return MlAdvisoryResult.success(snapshotIdempotencyResponses.get(idempotencyKey));
    }

    List<FeatureSnapshotValue> values = command.features().stream().map(this::govern).toList();
    String includedCanonical =
        values.stream()
            .filter(FeatureSnapshotValue::included)
            .sorted(Comparator.comparing(FeatureSnapshotValue::featureName))
            .map(value -> value.featureName() + "=" + value.valueHash())
            .reduce("", (left, right) -> left + "|" + right);
    String featureHash = hash(command.tenantId() + "|" + command.featureSchemaVersion() + includedCanonical);
    String snapshotId = deterministicId(command.tenantId(), command.scenarioId(), featureHash);
    Instant now = clock.instant();
    String governanceStatus = values.stream().anyMatch(value -> !value.included()) ? "REDACTED_WITH_EXCLUSIONS" : "APPROVED_REDACTED";
    FeatureSnapshot snapshot =
        new FeatureSnapshot(
            snapshotId,
            command.tenantId(),
            command.scenarioId(),
            command.pricingResultId(),
            command.eligibilityResultId(),
            command.featureSchemaVersion(),
            command.captureMode(),
            featureHash,
            now,
            command.retentionClass(),
            governanceStatus,
            command.correlationId(),
            values,
            Map.copyOf(command.sourceRefs()));
    featureSnapshots.put(snapshotKey(command.tenantId(), snapshotId), snapshot);

    String eventId = deterministicId(snapshotId, command.correlationId(), FEATURE_SNAPSHOT_CAPTURED_EVENT);
    String auditId = deterministicId(snapshotId, command.actorId(), "snapshot-audit");
    outboxEvents.add(
        new MlAdvisoryOutboxEvent(
            eventId,
            FEATURE_SNAPSHOT_CAPTURED_EVENT,
            command.tenantId(),
            snapshotId,
            command.actorId(),
            command.correlationId(),
            command.idempotencyKey(),
            now,
            Map.of(
                "snapshotId", snapshotId,
                "scenarioId", command.scenarioId(),
                "schemaVersion", command.featureSchemaVersion(),
                "featureHash", featureHash,
                "captureMode", command.captureMode().name(),
                "governanceStatus", governanceStatus,
                "sourceRefs", String.join(",", command.sourceRefs().keySet().stream().sorted().toList()))));
    auditRecords.add(
        new MlAdvisoryAuditRecord(
            auditId,
            command.tenantId(),
            command.actorId(),
            "SHADOW_INPUT_CAPTURE_COMPLETED",
            "none",
            "snapshot=" + snapshotId + ";features=" + values.size() + ";governance=" + governanceStatus,
            command.correlationId(),
            now));
    FeatureSnapshotResponse response = toResponse(snapshot, eventId, auditId);
    idempotencyHashes.put(idempotencyKey, requestHash);
    snapshotIdempotencyResponses.put(idempotencyKey, response);
    return MlAdvisoryResult.success(response);
  }

  public MlAdvisoryResult<FeatureSnapshotResponse> getFeatureSnapshot(String tenantId, String snapshotId, String correlationId) {
    if (!isUuid(tenantId) || isBlank(snapshotId)) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    FeatureSnapshot snapshot = featureSnapshots.get(snapshotKey(tenantId, snapshotId));
    if (snapshot == null) {
      return MlAdvisoryResult.failure("ML_SNAPSHOT_NOT_FOUND");
    }
    return MlAdvisoryResult.success(toResponse(snapshot, "", deterministicId(snapshotId, correlationId, "read-audit")));
  }

  public List<FeatureSnapshotResponse> searchFeatureSnapshots(FeatureSnapshotSearchQuery query) {
    if (query == null || !isUuid(query.tenantId())) {
      return List.of();
    }
    return featureSnapshots.values().stream()
        .filter(snapshot -> snapshot.tenantId().equals(query.tenantId()))
        .filter(snapshot -> isBlank(query.scenarioId()) || snapshot.scenarioId().equals(query.scenarioId()))
        .filter(snapshot -> query.from() == null || !snapshot.createdAt().isBefore(query.from()))
        .filter(snapshot -> query.to() == null || snapshot.createdAt().isBefore(query.to()))
        .sorted(Comparator.comparing(FeatureSnapshot::createdAt).reversed())
        .map(snapshot -> toResponse(snapshot, "", ""))
        .toList();
  }

  public MlAdvisoryResult<AdvisoryCard> generateAdvisory(GenerateAdvisoryCommand command) {
    MlAdvisoryResult<GenerateAdvisoryCommand> validation = validateGenerateAdvisory(command);
    if (!validation.valid()) {
      return MlAdvisoryResult.failure(validation.errorCode().orElseThrow());
    }
    MlAdvisoryResult<MlAdvisoryControlResponse> mode =
        resolveEffectiveMode(
            new ResolveControlQuery(
                command.tenantId(),
                "advisory-interface",
                "pricing-result",
                command.advisoryType(),
                command.correlationId()));
    if (mode.valid() && mode.value().orElseThrow().effectiveMode() != AdvisoryMode.ADVISORY_VISIBLE) {
      return MlAdvisoryResult.failure("ML_ADVISORY_DISABLED");
    }

    String idempotencyKey = command.tenantId() + ":advisory:" + command.idempotencyKey();
    String requestHash = hash(canonicalAdvisory(command));
    String existingHash = idempotencyHashes.get(idempotencyKey);
    if (existingHash != null) {
      if (!existingHash.equals(requestHash)) {
        return MlAdvisoryResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return MlAdvisoryResult.success(advisoryIdempotencyResponses.get(idempotencyKey));
    }

    Instant now = clock.instant();
    String advisoryId =
        deterministicId(command.tenantId(), command.scenarioId(), command.pricingResultId(), command.snapshotId(), command.modelVersionId());
    String eventId = deterministicId(advisoryId, command.correlationId(), ML_ADVISORY_GENERATED_EVENT);
    String auditId = deterministicId(advisoryId, command.actorId(), "advisory-audit");
    List<AllowedAction> allowedActions = AdvisoryBoundaryEnforcer.allowedDisplayActions(command.displayPolicy());
    boolean collapsed = command.displayPolicy().collapsedByDefault(command.confidence());
    AdvisoryCard card =
        new AdvisoryCard(
            advisoryId,
            command.tenantId(),
            command.scenarioId(),
            command.pricingResultId(),
            command.snapshotId(),
            command.modelVersionId(),
            command.advisoryType(),
            command.recommendation(),
            command.confidence(),
            command.confidenceBand(),
            AdvisoryBoundaryEnforcer.authoritative(),
            expired(command.expiresAt(), now) ? "EXPIRED" : "READY",
            command.generatedAt(),
            command.expiresAt(),
            command.displayPolicy().disclaimer(),
            allowedActions,
            command.reasons().stream().sorted(Comparator.comparingInt(AdvisoryReason::rank)).toList(),
            collapsed,
            collapsed ? "LOW_CONFIDENCE_COLLAPSED" : "READY_EXPANDED",
            "ML Advisory, " + command.confidenceBand() + " confidence, advisory only",
            eventId,
            auditId,
            command.correlationId());
    advisoryCards.put(advisoryKey(command.tenantId(), advisoryId), card);
    outboxEvents.add(
        new MlAdvisoryOutboxEvent(
            eventId,
            ML_ADVISORY_GENERATED_EVENT,
            command.tenantId(),
            advisoryId,
            command.actorId(),
            command.correlationId(),
            command.idempotencyKey(),
            now,
            Map.of(
                "advisoryId", advisoryId,
                "scenarioId", command.scenarioId(),
                "type", command.advisoryType().name(),
                "confidenceBand", command.confidenceBand(),
                "modelVersionId", command.modelVersionId(),
                "snapshotId", command.snapshotId(),
                "authoritative", "false",
                "sourceRefs", String.join(",", command.sourceRefs().keySet().stream().sorted().toList()))));
    auditRecords.add(
        new MlAdvisoryAuditRecord(
            auditId,
            command.tenantId(),
            command.actorId(),
            "ML_ADVISORY_GENERATED",
            "none",
            "advisory=" + advisoryId + ";authoritative=false;actions=" + allowedActions,
            command.correlationId(),
            now));
    idempotencyHashes.put(idempotencyKey, requestHash);
    advisoryIdempotencyResponses.put(idempotencyKey, card);
    return MlAdvisoryResult.success(card);
  }

  public List<AdvisoryCard> listAdvisoryCards(AdvisorySummaryQuery query) {
    if (query == null || !isUuid(query.tenantId()) || isBlank(query.scenarioId()) || isBlank(query.pricingResultId())) {
      return List.of();
    }
    return advisoryCards.values().stream()
        .filter(card -> card.tenantId().equals(query.tenantId()))
        .filter(card -> card.scenarioId().equals(query.scenarioId()))
        .filter(card -> card.pricingResultId().equals(query.pricingResultId()))
        .sorted(Comparator.comparing(AdvisoryCard::generatedAt).reversed())
        .toList();
  }

  public MlAdvisoryResult<PricingAdvisoryEvaluation> evaluatePricingAdvisory(
      EvaluatePricingAdvisoryCommand command, LocalModelAdapter adapter) {
    MlAdvisoryResult<EvaluatePricingAdvisoryCommand> validation = validatePricingAdvisory(command, adapter);
    if (!validation.valid()) {
      return MlAdvisoryResult.failure(validation.errorCode().orElseThrow());
    }

    String idempotencyKey = command.tenantId() + ":pricing-advisory:" + command.idempotencyKey();
    String requestHash = hash(canonicalPricingAdvisory(command));
    String existingHash = idempotencyHashes.get(idempotencyKey);
    if (existingHash != null) {
      if (!existingHash.equals(requestHash)) {
        return MlAdvisoryResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return MlAdvisoryResult.success(pricingAdvisoryIdempotencyResponses.get(idempotencyKey));
    }

    FeatureSnapshot snapshot = featureSnapshots.get(snapshotKey(command.tenantId(), command.snapshotId()));
    if (snapshot == null || !snapshot.pricingResultId().equals(command.pricingResultId())) {
      return MlAdvisoryResult.failure("ML_PRICING_REFERENCE_STALE");
    }

    MlAdvisoryResult<MlAdvisoryControlResponse> mode =
        resolveEffectiveMode(
            new ResolveControlQuery(
                command.tenantId(),
                "advisory-interface",
                "pricing-result",
                AdvisoryType.PRICING,
                command.correlationId()));
    if (mode.valid() && mode.value().orElseThrow().effectiveMode() != AdvisoryMode.ADVISORY_VISIBLE) {
      PricingAdvisoryEvaluation suppressed = suppressPricingAdvisory(command, "ML_PRICING_ADVISORY_DISABLED");
      rememberPricingAdvisory(idempotencyKey, requestHash, suppressed);
      return MlAdvisoryResult.success(suppressed);
    }
    if (command.displayPolicy().collapsedByDefault(command.confidence())) {
      PricingAdvisoryEvaluation suppressed = suppressPricingAdvisory(command, "ML_PRICING_ADVISORY_SUPPRESSED");
      rememberPricingAdvisory(idempotencyKey, requestHash, suppressed);
      return MlAdvisoryResult.success(suppressed);
    }
    if (snapshot.features().stream().anyMatch(value -> !value.included() && "PROHIBITED_PROXY".equals(value.sensitivityClass()))) {
      PricingAdvisoryEvaluation suppressed = suppressPricingAdvisory(command, "FAIR_LENDING_GUARD_SUPPRESSED");
      rememberPricingAdvisory(idempotencyKey, requestHash, suppressed);
      return MlAdvisoryResult.success(suppressed);
    }

    Map<String, String> modelFeatures =
        snapshot.features().stream()
            .filter(FeatureSnapshotValue::included)
            .collect(Collectors.toMap(FeatureSnapshotValue::featureName, FeatureSnapshotValue::valueHash, (left, right) -> left));
    ModelInferenceResult inference =
        invokeLocalModel(
                adapter,
                new ModelInvocationRequest(
                    command.modelArtifactRef(),
                    new InferenceRequest(
                        command.tenantId(),
                        snapshot.snapshotId(),
                        AdvisoryType.PRICING,
                        snapshot.featureSchemaVersion(),
                        modelFeatures,
                        command.actorId(),
                        command.correlationId(),
                        command.timeoutMillis(),
                        command.simulateModelTimeout(),
                        command.simulateModelFailure(),
                        command.simulateAuthoritativeOutput())))
            .value()
            .orElseThrow();
    if (!"SUCCESS".equals(inference.status()) || inference.authoritative()) {
      PricingAdvisoryEvaluation suppressed =
          suppressPricingAdvisory(command, inference.reasonCode().isBlank() ? "ML_MODEL_UNAVAILABLE" : inference.reasonCode());
      rememberPricingAdvisory(idempotencyKey, requestHash, suppressed);
      return MlAdvisoryResult.success(suppressed);
    }

    Map<String, String> sourceRefs = new HashMap<>(snapshot.sourceRefs());
    sourceRefs.put("snapshot", snapshot.snapshotId());
    sourceRefs.put("pricingResult", snapshot.pricingResultId());
    sourceRefs.put("modelVersion", command.modelArtifactRef().modelVersionId());
    String recommendation =
        inference.output().getOrDefault("recommendation", "Review pricing advisory evidence before final pricing decision");
    String confidenceBand =
        inference.confidenceBand().isBlank() ? commandConfidenceBand(command.displayPolicy(), command.confidence()) : inference.confidenceBand();
    AdvisoryCard card =
        generateAdvisory(
                new GenerateAdvisoryCommand(
                    command.tenantId(),
                    command.idempotencyKey() + "-card",
                    command.actorId(),
                    command.scenarioId(),
                    command.pricingResultId(),
                    snapshot.snapshotId(),
                    command.modelArtifactRef().modelVersionId(),
                    AdvisoryType.PRICING,
                    recommendation,
                    command.confidence(),
                    confidenceBand,
                    command.generatedAt(),
                    command.expiresAt(),
                    command.reasons(),
                    sourceRefs,
                    command.displayPolicy(),
                    command.correlationId()))
            .value()
            .orElseThrow();
    String eventId = deterministicId(card.advisoryId(), command.correlationId(), ML_PRICING_ADVISORY_GENERATED_EVENT);
    outboxEvents.add(
        new MlAdvisoryOutboxEvent(
            eventId,
            ML_PRICING_ADVISORY_GENERATED_EVENT,
            command.tenantId(),
            card.advisoryId(),
            command.actorId(),
            command.correlationId(),
            command.idempotencyKey(),
            clock.instant(),
            Map.of(
                "advisoryId", card.advisoryId(),
                "scenarioId", command.scenarioId(),
                "pricingResultId", command.pricingResultId(),
                "subtype", "PRICING",
                "confidenceBand", confidenceBand,
                "severity", command.reasons().get(0).direction(),
                "modelVersionId", command.modelArtifactRef().modelVersionId(),
                "snapshotId", snapshot.snapshotId(),
                "authoritative", "false",
                "deterministicPricingUnchanged", "true")));
    PricingAdvisoryEvaluation evaluation =
        new PricingAdvisoryEvaluation(
            deterministicId(card.advisoryId(), command.correlationId(), "evaluation"),
            command.tenantId(),
            command.scenarioId(),
            command.pricingResultId(),
            snapshot.snapshotId(),
            command.modelArtifactRef().modelVersionId(),
            "READY",
            false,
            true,
            command.confidence(),
            confidenceBand,
            "",
            card.disclaimer(),
            card.advisoryId(),
            pricingAdvisoryLink(command.tenantId(), card.advisoryId(), "explanation"),
            pricingAdvisoryLink(command.tenantId(), card.advisoryId(), "feedback"),
            eventId,
            card.auditRef(),
            command.correlationId(),
            card.topReasons());
    rememberPricingAdvisory(idempotencyKey, requestHash, evaluation);
    return MlAdvisoryResult.success(evaluation);
  }

  public MlAdvisoryResult<AdvisoryCard> getAdvisoryDetails(
      String tenantId, String advisoryId, String actorId, String viewSurface, String correlationId) {
    if (!isUuid(tenantId) || isBlank(advisoryId) || isBlank(actorId) || isBlank(viewSurface) || isBlank(correlationId)) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    AdvisoryCard card = advisoryCards.get(advisoryKey(tenantId, advisoryId));
    if (card == null) {
      return MlAdvisoryResult.failure("ML_ADVISORY_NOT_FOUND");
    }
    if (expired(card.expiresAt(), clock.instant())) {
      return MlAdvisoryResult.failure("ML_ADVISORY_EXPIRED");
    }
    String eventId = deterministicId(advisoryId, actorId, correlationId, ML_ADVISORY_VIEWED_EVENT);
    outboxEvents.add(
        new MlAdvisoryOutboxEvent(
            eventId,
            ML_ADVISORY_VIEWED_EVENT,
            tenantId,
            advisoryId,
            actorId,
            correlationId,
            "",
            clock.instant(),
            Map.of("advisoryId", advisoryId, "viewSurface", viewSurface, "authoritative", "false")));
    auditRecords.add(
        new MlAdvisoryAuditRecord(
            deterministicId(advisoryId, actorId, "view-audit"),
            tenantId,
            actorId,
            "ML_ADVISORY_VIEWED",
            "hidden",
            "viewSurface=" + viewSurface,
            correlationId,
            clock.instant()));
    return MlAdvisoryResult.success(card);
  }

  public MlAdvisoryResult<ModelInferenceResult> invokeLocalModel(LocalModelAdapter adapter, ModelInvocationRequest request) {
    if (adapter == null || request == null || request.inferenceRequest() == null || request.artifactRef() == null) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    InferenceRequest inference = request.inferenceRequest();
    ModelArtifactRef artifact = request.artifactRef();
    if (!isUuid(inference.tenantId())
        || isBlank(inference.snapshotId())
        || inference.advisoryType() == null
        || isBlank(inference.actorId())
        || isBlank(inference.correlationId())) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }

    ModelInferenceResult result = adapter.invoke(request);
    Instant now = clock.instant();
    modelInvocations.add(result);
    String runtimeId = deterministicId(inference.tenantId(), artifact.modelVersionId(), "runtime");
    String healthStatus = result.status().equals("SUCCESS") ? "READY" : "DEGRADED";
    RuntimeHealth previousHealth = runtimeHealth.get(runtimeId);
    RuntimeHealth health =
        new RuntimeHealth(
            runtimeId,
            artifact.modelVersionId(),
            artifact.actualChecksum(),
            now,
            healthStatus,
            result.status().equals("SUCCESS") ? "" : result.reasonCode(),
            now,
            killSwitchFor(inference.tenantId()).filter(KillSwitchState::enabled).isPresent());
    runtimeHealth.put(runtimeId, health);

    outboxEvents.add(
        new MlAdvisoryOutboxEvent(
            result.eventRef(),
            ML_MODEL_INFERENCE_COMPLETED_EVENT,
            result.tenantId(),
            result.invocationId(),
            inference.actorId(),
            result.correlationId(),
            "",
            now,
            Map.of(
                "invocationId", result.invocationId(),
                "modelVersionId", result.modelVersionId(),
                "snapshotId", result.snapshotId(),
                "status", result.status(),
                "advisoryResponse", result.advisoryResponse(),
                "latencyMs", String.valueOf(result.latencyMs()),
                "confidenceBand", result.confidenceBand())));
    auditRecords.add(
        new MlAdvisoryAuditRecord(
            result.auditRef(),
            result.tenantId(),
            inference.actorId(),
            "ML_MODEL_RUNTIME_INVOCATION_COMPLETED",
            "snapshot=" + result.snapshotId(),
            "status=" + result.status() + ";response=" + result.advisoryResponse() + ";reason=" + result.reasonCode(),
            result.correlationId(),
            now));
    if (previousHealth == null || !previousHealth.status().equals(health.status())) {
      outboxEvents.add(
          new MlAdvisoryOutboxEvent(
              deterministicId(runtimeId, health.status(), ML_MODEL_RUNTIME_HEALTH_CHANGED_EVENT),
              ML_MODEL_RUNTIME_HEALTH_CHANGED_EVENT,
              inference.tenantId(),
              runtimeId,
              inference.actorId(),
              result.correlationId(),
              "",
              now,
              Map.of(
                  "runtimeId", runtimeId,
                  "modelVersionId", artifact.modelVersionId(),
                  "oldStatus", previousHealth == null ? "UNKNOWN" : previousHealth.status(),
                  "newStatus", health.status(),
                  "reason", health.lastError())));
    }
    return MlAdvisoryResult.success(result);
  }

  public List<ModelInferenceResult> modelInvocationsForTenant(String tenantId) {
    return modelInvocations.stream()
        .filter(invocation -> invocation.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(ModelInferenceResult::invocationId))
        .toList();
  }

  public List<RuntimeHealth> runtimeHealthForTenant(String tenantId) {
    if (!isUuid(tenantId)) {
      return List.of();
    }
    return runtimeHealth.values().stream()
        .sorted(Comparator.comparing(RuntimeHealth::runtimeId))
        .toList();
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

  public List<FeatureSnapshot> featureSnapshotsForTenant(String tenantId) {
    return featureSnapshots.values().stream()
        .filter(snapshot -> snapshot.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(FeatureSnapshot::snapshotId))
        .toList();
  }

  public List<AdvisoryCard> advisoryCardsForTenant(String tenantId) {
    return advisoryCards.values().stream()
        .filter(card -> card.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(AdvisoryCard::advisoryId))
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
    if (command == null || !isValidKillSwitchTenant(command.tenantId()) || isBlank(command.actorId()) || isBlank(command.reason())) {
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

  private MlAdvisoryResult<CaptureFeatureSnapshotCommand> validateSnapshotCapture(CaptureFeatureSnapshotCommand command) {
    if (command == null || !isUuid(command.tenantId()) || isBlank(command.idempotencyKey()) || isBlank(command.actorId())) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (isBlank(command.scenarioId())
        || isBlank(command.pricingResultId())
        || isBlank(command.eligibilityResultId())
        || isBlank(command.featureSchemaVersion())
        || command.captureMode() == null
        || isBlank(command.correlationId())) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (isBlank(command.legalBasis()) || isBlank(command.retentionClass())) {
      return MlAdvisoryResult.failure("POLICY_NOT_SATISFIED");
    }
    if (command.features() == null || command.features().isEmpty() || command.sourceRefs() == null || command.sourceRefs().isEmpty()) {
      return MlAdvisoryResult.failure("ML_FEATURE_SCHEMA_UNSUPPORTED");
    }
    if (command.features().stream().anyMatch(this::invalidFeature)) {
      return MlAdvisoryResult.failure("ML_FEATURE_SCHEMA_UNSUPPORTED");
    }
    return MlAdvisoryResult.success(command);
  }

  private MlAdvisoryResult<GenerateAdvisoryCommand> validateGenerateAdvisory(GenerateAdvisoryCommand command) {
    if (command == null
        || !isUuid(command.tenantId())
        || isBlank(command.idempotencyKey())
        || isBlank(command.actorId())
        || isBlank(command.scenarioId())
        || isBlank(command.pricingResultId())
        || isBlank(command.snapshotId())
        || isBlank(command.modelVersionId())
        || command.advisoryType() == null
        || isBlank(command.recommendation())
        || isBlank(command.confidenceBand())
        || command.generatedAt() == null
        || command.expiresAt() == null
        || isBlank(command.correlationId())) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (command.confidence() < 0.0 || command.confidence() > 1.0) {
      return MlAdvisoryResult.failure("ML_ADVISORY_CONFIDENCE_INVALID");
    }
    if (!command.expiresAt().isAfter(command.generatedAt())) {
      return MlAdvisoryResult.failure("ML_ADVISORY_EXPIRED");
    }
    if (command.displayPolicy() == null || !command.displayPolicy().valid()) {
      return MlAdvisoryResult.failure("POLICY_NOT_SATISFIED");
    }
    if (command.reasons().isEmpty()
        || command.sourceRefs().isEmpty()
        || command.reasons().stream().anyMatch(this::invalidReason)) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    return MlAdvisoryResult.success(command);
  }

  private MlAdvisoryResult<EvaluatePricingAdvisoryCommand> validatePricingAdvisory(
      EvaluatePricingAdvisoryCommand command, LocalModelAdapter adapter) {
    if (command == null
        || adapter == null
        || !isUuid(command.tenantId())
        || isBlank(command.idempotencyKey())
        || isBlank(command.actorId())
        || isBlank(command.scenarioId())
        || isBlank(command.pricingResultId())
        || isBlank(command.snapshotId())
        || command.modelArtifactRef() == null
        || isBlank(command.correlationId())
        || command.generatedAt() == null
        || command.expiresAt() == null) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (command.confidence() < 0.0 || command.confidence() > 1.0 || command.timeoutMillis() <= 0) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    if (command.displayPolicy() == null || !command.displayPolicy().valid() || command.reasons().isEmpty()) {
      return MlAdvisoryResult.failure("POLICY_NOT_SATISFIED");
    }
    if (!command.expiresAt().isAfter(command.generatedAt()) || command.reasons().stream().anyMatch(this::invalidReason)) {
      return MlAdvisoryResult.failure("VALIDATION_FAILED");
    }
    return MlAdvisoryResult.success(command);
  }

  private PricingAdvisoryEvaluation suppressPricingAdvisory(EvaluatePricingAdvisoryCommand command, String reason) {
    String evaluationId = deterministicId(command.tenantId(), command.pricingResultId(), command.snapshotId(), reason);
    String eventId = deterministicId(evaluationId, command.correlationId(), ML_PRICING_ADVISORY_SUPPRESSED_EVENT);
    String auditId = deterministicId(evaluationId, command.actorId(), "pricing-advisory-suppressed-audit");
    outboxEvents.add(
        new MlAdvisoryOutboxEvent(
            eventId,
            ML_PRICING_ADVISORY_SUPPRESSED_EVENT,
            command.tenantId(),
            evaluationId,
            command.actorId(),
            command.correlationId(),
            command.idempotencyKey(),
            clock.instant(),
            Map.of(
                "scenarioId", command.scenarioId(),
                "pricingResultId", command.pricingResultId(),
                "reason", reason,
                "policyVersion", command.displayPolicy().disclaimer(),
                "modelVersionId", command.modelArtifactRef() == null ? "" : command.modelArtifactRef().modelVersionId(),
                "authoritative", "false",
                "deterministicPricingUnchanged", "true")));
    auditRecords.add(
        new MlAdvisoryAuditRecord(
            auditId,
            command.tenantId(),
            command.actorId(),
            "ML_PRICING_ADVISORY_SUPPRESSED",
            "none",
            "reason=" + reason + ";pricingResult=" + command.pricingResultId(),
            command.correlationId(),
            clock.instant()));
    return new PricingAdvisoryEvaluation(
        evaluationId,
        command.tenantId(),
        command.scenarioId(),
        command.pricingResultId(),
        command.snapshotId(),
        command.modelArtifactRef() == null ? "" : command.modelArtifactRef().modelVersionId(),
        "SUPPRESSED",
        false,
        true,
        command.confidence(),
        commandConfidenceBand(command.displayPolicy(), command.confidence()),
        reason,
        command.displayPolicy() == null ? NON_AUTHORITATIVE_DISCLAIMER : command.displayPolicy().disclaimer(),
        "",
        "",
        "",
        eventId,
        auditId,
        command.correlationId(),
        command.reasons());
  }

  private void rememberPricingAdvisory(String idempotencyKey, String requestHash, PricingAdvisoryEvaluation evaluation) {
    idempotencyHashes.put(idempotencyKey, requestHash);
    pricingAdvisoryIdempotencyResponses.put(idempotencyKey, evaluation);
  }

  private String commandConfidenceBand(AdvisoryDisplayPolicy policy, double confidence) {
    return policy != null && policy.collapsedByDefault(confidence) ? "LOW" : "VISIBLE";
  }

  private String pricingAdvisoryLink(String tenantId, String advisoryId, String relation) {
    return "/api/v1/tenants/" + tenantId + "/ml-advisory/pricing-advisories/" + advisoryId + ":" + relation;
  }

  private boolean invalidReason(AdvisoryReason reason) {
    return reason == null
        || isBlank(reason.reasonCode())
        || reason.rank() <= 0
        || isBlank(reason.description())
        || isBlank(reason.direction())
        || isBlank(reason.featureRef())
        || isBlank(reason.sensitivityClass());
  }

  private boolean invalidFeature(FeatureInput feature) {
    return feature == null
        || isBlank(feature.name())
        || isBlank(feature.type())
        || isBlank(feature.sensitivityClass())
        || isBlank(feature.sourceSystem())
        || isBlank(feature.sourceField())
        || isBlank(feature.businessJustification());
  }

  private FeatureSnapshotValue govern(FeatureInput feature) {
    String normalizedName = feature.name().trim();
    String sensitivity = feature.sensitivityClass().trim().toUpperCase();
    if (sensitivity.equals("PROTECTED") || sensitivity.equals("PROHIBITED_PROXY")) {
      return new FeatureSnapshotValue(
          normalizedName,
          feature.type(),
          "[EXCLUDED]",
          sensitivity,
          feature.sourceSystem(),
          feature.sourceField(),
          false,
          "GOVERNANCE_EXCLUDED_" + sensitivity,
          hash(normalizedName + ":excluded"),
          feature.businessJustification());
    }
    String valueHash = hash(normalizedName + ":" + nullToEmpty(feature.value()));
    String redactedValue = sensitivity.equals("PUBLIC") ? nullToEmpty(feature.value()) : "[REDACTED:" + valueHash.substring(0, 12) + "]";
    return new FeatureSnapshotValue(
        normalizedName,
        feature.type(),
        redactedValue,
        sensitivity,
        feature.sourceSystem(),
        feature.sourceField(),
        true,
        "",
        valueHash,
        feature.businessJustification());
  }

  private FeatureSnapshotResponse toResponse(FeatureSnapshot snapshot, String eventRef, String auditRef) {
    return new FeatureSnapshotResponse(
        snapshot.snapshotId(),
        snapshot.tenantId(),
        snapshot.scenarioId(),
        snapshot.featureSchemaVersion(),
        snapshot.captureMode(),
        snapshot.featureHash(),
        snapshot.governanceStatus(),
        eventRef,
        auditRef,
        snapshot.correlationId(),
        snapshot.features());
  }

  private KillSwitchState newKillSwitch(KillSwitchCommand command, boolean enabled) {
    String tenantId = killSwitchTenantId(command.tenantId());
    return new KillSwitchState(
        deterministicId(tenantId, command.correlationId(), String.valueOf(enabled)),
        tenantId,
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
            state.tenantId(),
            state.id(),
            command.actorId(),
            command.correlationId(),
            command.idempotencyKey(),
            clock.instant(),
            Map.of("enabled", String.valueOf(state.enabled()), "reason", state.reason())));
    auditRecords.add(
        new MlAdvisoryAuditRecord(
            deterministicId(state.id(), command.actorId(), "audit"),
            state.tenantId(),
            command.actorId(),
            "ML_ADVISORY_KILL_SWITCH_CHANGED",
            String.valueOf(!state.enabled()),
            String.valueOf(state.enabled()),
            command.correlationId(),
            clock.instant()));
  }

  private Optional<KillSwitchState> killSwitchFor(String tenantId) {
    KillSwitchState globalSwitch = killSwitches.get(GLOBAL_KILL_SWITCH_TENANT_ID);
    if (globalSwitch != null && globalSwitch.enabled()) {
      return Optional.of(globalSwitch);
    }
    KillSwitchState tenantSwitch = killSwitches.get(tenantId);
    if (tenantSwitch != null) {
      return Optional.of(tenantSwitch);
    }
    return Optional.ofNullable(globalSwitch);
  }

  private boolean isValidKillSwitchTenant(String tenantId) {
    return tenantId == null || GLOBAL_KILL_SWITCH_TENANT_ID.equals(tenantId) || isUuid(tenantId);
  }

  private String killSwitchTenantId(String tenantId) {
    return tenantId == null ? GLOBAL_KILL_SWITCH_TENANT_ID : tenantId;
  }

  private boolean overlaps(MlAdvisoryControl previous, Instant start, Instant end) {
    Instant previousEnd = previous.effectiveTo() == null ? Instant.MAX : previous.effectiveTo();
    Instant newEnd = end == null ? Instant.MAX : end;
    return start.isBefore(previousEnd) && previous.effectiveFrom().isBefore(newEnd);
  }

  private String scopeKey(String tenantId, String channel, String productFamily, AdvisoryType advisoryType) {
    return tenantId + ":" + channel + ":" + productFamily + ":" + advisoryType;
  }

  private String snapshotKey(String tenantId, String snapshotId) {
    return tenantId + ":" + snapshotId;
  }

  private String advisoryKey(String tenantId, String advisoryId) {
    return tenantId + ":" + advisoryId;
  }

  private boolean expired(Instant expiresAt, Instant now) {
    return expiresAt != null && !expiresAt.isAfter(now);
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

  private String canonicalSnapshot(CaptureFeatureSnapshotCommand command) {
    String canonicalFeatures =
        command.features().stream()
            .sorted(Comparator.comparing(FeatureInput::name))
            .map(
                feature ->
                    String.join(
                        ":",
                        feature.name(),
                        feature.type(),
                        nullToEmpty(feature.value()),
                        feature.sensitivityClass(),
                        feature.sourceSystem(),
                        feature.sourceField(),
                        feature.businessJustification()))
            .reduce("", (left, right) -> left + "|" + right);
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.scenarioId(),
        command.pricingResultId(),
        command.eligibilityResultId(),
        command.featureSchemaVersion(),
        command.captureMode().name(),
        command.legalBasis(),
        command.retentionClass(),
        command.correlationId(),
        canonicalFeatures);
  }

  private String canonicalAdvisory(GenerateAdvisoryCommand command) {
    String canonicalReasons =
        command.reasons().stream()
            .sorted(Comparator.comparingInt(AdvisoryReason::rank))
            .map(reason -> reason.reasonCode() + ":" + reason.featureRef() + ":" + reason.direction())
            .reduce("", (left, right) -> left + "|" + right);
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.scenarioId(),
        command.pricingResultId(),
        command.snapshotId(),
        command.modelVersionId(),
        command.advisoryType().name(),
        command.recommendation(),
        String.valueOf(command.confidence()),
        command.confidenceBand(),
        command.generatedAt().toString(),
        command.expiresAt().toString(),
        command.displayPolicy().disclaimer(),
        canonicalReasons,
        command.correlationId());
  }

  private String canonicalPricingAdvisory(EvaluatePricingAdvisoryCommand command) {
    String canonicalReasons =
        command.reasons().stream()
            .sorted(Comparator.comparingInt(AdvisoryReason::rank))
            .map(reason -> reason.reasonCode() + ":" + reason.featureRef() + ":" + reason.direction())
            .reduce("", (left, right) -> left + "|" + right);
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        command.scenarioId(),
        command.pricingResultId(),
        command.snapshotId(),
        command.modelArtifactRef().modelVersionId(),
        String.valueOf(command.confidence()),
        command.generatedAt().toString(),
        command.expiresAt().toString(),
        command.displayPolicy().disclaimer(),
        String.valueOf(command.timeoutMillis()),
        String.valueOf(command.simulateModelTimeout()),
        String.valueOf(command.simulateModelFailure()),
        String.valueOf(command.simulateAuthoritativeOutput()),
        canonicalReasons,
        command.correlationId());
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }
}
