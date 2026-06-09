package com.wcpe.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RuleBuilderService {
  public static final String METADATA_ENDPOINT =
      "/api/v1/tenants/{tenantId}/admin/rule-builder/metadata?artifactType=RULE_SET&context={context}";
  public static final String SIMULATION_ENDPOINT =
      "/api/v1/tenants/{tenantId}/admin/rule-builder/simulations";
  public static final String WRITE_PERMISSION = "admin.config.write";
  public static final String SIMULATE_PERMISSION = "admin.config.simulate";
  public static final String DRAFT_EVENT_TYPE = "RuleSetDraftSaved.v1";
  public static final String SIMULATION_EVENT_TYPE = "RuleSetSimulationCompleted.v1";
  public static final String AUDIT_ACTION = "RULE_BUILDER_UI_COMPLETED";

  private final Clock clock;
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final List<ConfigApiOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<ConfigApiAuditRecord> auditRecords = new ArrayList<>();

  public RuleBuilderService() {
    this(Clock.systemUTC());
  }

  public RuleBuilderService(Clock clock) {
    this.clock = clock;
  }

  public GovernanceValidationResult<RuleBuilderDraftResult> saveDraft(RuleBuilderDraftCommand command) {
    GovernanceValidationResult<RuleBuilderDraftCommand> validation = validateCommand(command);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    List<RuleBuilderValidationMessage> messages = new ArrayList<>(validateRuleSet(command.ruleSet(), command.metadata()));
    if (messages.stream().anyMatch(RuleBuilderValidationMessage::blocking)) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: " + messages.get(0).code());
    }

    String requestHash = hash(canonicalCommand(command));
    String idempotencyLookupKey = command.tenantId() + ":" + command.idempotencyKey();
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyLookupKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        return GovernanceValidationResult.failure("IDEMPOTENCY_CONFLICT");
      }
      return GovernanceValidationResult.success(existing.draftResult());
    }

    Instant now = clock.instant();
    String payloadHash = hash(canonicalRuleSet(command.ruleSet()));
    String versionId = deterministicId(command.tenantId(), command.ruleSet().ruleSetId(), payloadHash, "draft");
    String auditRef = deterministicId(versionId, command.actorId(), "audit");
    String eventId = deterministicId(versionId, command.correlationId(), "event");
    RuleBuilderDraftResult result =
        new RuleBuilderDraftResult(
            command.ruleSet().ruleSetId(),
            versionId,
            "DRAFT",
            command.ruleSet().metadataVersionRefs(),
            payloadHash,
            messages,
            auditRef,
            versionId,
            command.correlationId(),
            now);

    outboxEvents.add(
        new ConfigApiOutboxEvent(
            eventId,
            DRAFT_EVENT_TYPE,
            1,
            command.tenantId(),
            command.ruleSet().ruleSetId(),
            versionId,
            command.actorId(),
            command.correlationId(),
            command.correlationId(),
            command.idempotencyKey(),
            now,
            Map.of(
                "status", result.status(),
                "payloadHash", payloadHash,
                "metadataVersionRefs", canonicalList(command.ruleSet().metadataVersionRefs()))));
    auditRecords.add(
        new ConfigApiAuditRecord(
            auditRef,
            command.tenantId(),
            command.ruleSet().ruleSetId(),
            versionId,
            command.actorId(),
            AUDIT_ACTION,
            "",
            payloadHash,
            command.correlationId(),
            now));
    idempotencyEntries.put(idempotencyLookupKey, new IdempotencyEntry(requestHash, result));
    return GovernanceValidationResult.success(result);
  }

  public GovernanceValidationResult<RuleBuilderSimulationResult> simulate(RuleBuilderSimulationCommand command) {
    GovernanceValidationResult<RuleBuilderSimulationCommand> validation = validateSimulationCommand(command);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    List<RuleBuilderValidationMessage> messages = new ArrayList<>(validateRuleSet(command.ruleSet(), command.metadata()));
    List<RuleBuilderLedgerEntry> ledger = new ArrayList<>();
    for (RuleBuilderRule rule : orderedEnabledRules(command.ruleSet())) {
      for (RuleBuilderCondition condition : rule.conditions()) {
        if (!command.factsByDimension().containsKey(condition.dimensionRef())) {
          messages.add(
              new RuleBuilderValidationMessage(
                  "UNKNOWN_FACT_FAIL_CLOSED",
                  "$.rules." + rule.ruleId() + ".conditions." + condition.dimensionRef(),
                  "rule-builder.fact.missing",
                  true));
        }
      }
      for (RuleBuilderAction action : rule.actions()) {
        ledger.add(
            new RuleBuilderLedgerEntry(
                rule.ruleId(),
                action.actionId(),
                action.actionTypeRef(),
                firstSourceDimension(rule.conditions()),
                action.precisionRef(),
                action.roundingRef(),
                command.versionRef(),
                action.reasonCodeRef()));
      }
    }

    boolean blocked = messages.stream().anyMatch(RuleBuilderValidationMessage::blocking);
    Instant now = clock.instant();
    String resultHash = hash(canonicalRuleSet(command.ruleSet()) + "|" + canonicalMap(command.factsByDimension()) + "|" + canonicalMessages(messages));
    String eventId = deterministicId(command.tenantId(), command.ruleSet().ruleSetId(), resultHash, command.correlationId());
    RuleBuilderSimulationResult result =
        new RuleBuilderSimulationResult(
            command.ruleSet().ruleSetId(),
            blocked ? "BLOCKED" : "PASSED",
            resultHash,
            blocked ? List.copyOf(messages) : List.of(),
            List.copyOf(ledger),
            command.correlationId(),
            now);

    outboxEvents.add(
        new ConfigApiOutboxEvent(
            eventId,
            SIMULATION_EVENT_TYPE,
            1,
            command.tenantId(),
            command.ruleSet().ruleSetId(),
            command.versionRef(),
            command.actorId(),
            command.correlationId(),
            command.correlationId(),
            command.idempotencyKey(),
            now,
            Map.of("status", result.status(), "resultHash", resultHash)));
    return GovernanceValidationResult.success(result);
  }

  public List<ConfigApiOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<ConfigApiAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  public GovernanceValidationResult<List<RuleBuilderCustomFieldDescriptor>> describeCustomFields(RuleBuilderMetadata metadata) {
    if (metadata == null || metadata.dimensions() == null || metadata.operators() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: rule metadata is required");
    }

    List<RuleBuilderCustomFieldDescriptor> descriptors =
        metadata.dimensions().values().stream()
            .sorted(Comparator.comparing(RuleBuilderDimensionMetadata::dimensionRef))
            .map(dimension -> toDescriptor(metadata, dimension))
            .toList();
    return GovernanceValidationResult.success(descriptors);
  }

  public GovernanceValidationResult<RuleBuilderDynamicEvaluationResult> evaluateDynamicRules(RuleBuilderDynamicEvaluationCommand command) {
    GovernanceValidationResult<RuleBuilderDynamicEvaluationCommand> validation = validateDynamicEvaluationCommand(command);
    if (!validation.valid()) {
      return GovernanceValidationResult.failure(validation.error().orElseThrow());
    }

    List<RuleBuilderValidationMessage> messages = new ArrayList<>(validateRuleSet(command.ruleSet(), command.metadata()));
    List<RuleBuilderDynamicActionOutput> actionOutputs = new ArrayList<>();
    List<String> matchedRuleIds = new ArrayList<>();
    List<String> skippedRuleIds = new ArrayList<>();

    for (RuleBuilderRule rule : orderedEnabledRules(command.ruleSet())) {
      List<RuleBuilderValidationMessage> ruleBlockers = requiredFactBlockers(command, rule);
      messages.addAll(ruleBlockers);
      if (!ruleBlockers.isEmpty()) {
        skippedRuleIds.add(rule.ruleId());
        continue;
      }
      RuleMatchResult match = matchesRule(command.metadata().dimensions(), command.factsByDimension(), rule);
      messages.addAll(match.messages());
      if (match.blocked() || !match.matched()) {
        skippedRuleIds.add(rule.ruleId());
        continue;
      }
      matchedRuleIds.add(rule.ruleId());
      List<String> factRefs = factRefs(command.factsByDimension(), rule.conditions());
      for (RuleBuilderAction action : rule.actions()) {
        actionOutputs.add(
            new RuleBuilderDynamicActionOutput(
                rule.ruleId(),
                action.actionId(),
                action.actionTypeRef(),
                command.versionRef(),
                factRefs,
                action.precisionRef(),
                action.roundingRef(),
                action.reasonCodeRef()));
      }
      if (rule.stopProcessing()) {
        break;
      }
    }

    boolean blocked = messages.stream().anyMatch(RuleBuilderValidationMessage::blocking);
    Instant now = clock.instant();
    String evidenceMaterial =
        canonicalRuleSet(command.ruleSet())
            + "|"
            + canonicalTypedFacts(command.factsByDimension())
            + "|"
            + canonicalDynamicOutputs(actionOutputs)
            + "|"
            + canonicalMessages(messages)
            + "|"
            + command.versionRef();
    String evidenceHash = hash(evidenceMaterial);
    return GovernanceValidationResult.success(
        new RuleBuilderDynamicEvaluationResult(
            command.ruleSet().ruleSetId(),
            blocked ? "BLOCKED" : "PASSED",
            evidenceHash,
            evidenceHash,
            List.copyOf(messages),
            List.copyOf(actionOutputs),
            List.copyOf(matchedRuleIds),
            List.copyOf(skippedRuleIds),
            command.correlationId(),
            now));
  }

  private GovernanceValidationResult<RuleBuilderDraftCommand> validateCommand(RuleBuilderDraftCommand command) {
    if (command == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: command is required");
    }
    if (!isUuid(command.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (isBlank(command.idempotencyKey()) || isBlank(command.actorId()) || isBlank(command.correlationId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: idempotencyKey, actorId, and correlationId are required");
    }
    if (command.permissions() == null || !command.permissions().contains(WRITE_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (command.metadata() == null || command.ruleSet() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: rule metadata and rule set payload are required");
    }
    return GovernanceValidationResult.success(command);
  }

  private GovernanceValidationResult<RuleBuilderSimulationCommand> validateSimulationCommand(RuleBuilderSimulationCommand command) {
    if (command == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: command is required");
    }
    if (!isUuid(command.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (isBlank(command.idempotencyKey()) || isBlank(command.actorId()) || isBlank(command.correlationId()) || isBlank(command.versionRef())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: idempotencyKey, actorId, versionRef, and correlationId are required");
    }
    if (command.permissions() == null || !command.permissions().contains(SIMULATE_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (command.metadata() == null || command.ruleSet() == null || command.factsByDimension() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: metadata, rule set, and facts are required");
    }
    return GovernanceValidationResult.success(command);
  }

  private GovernanceValidationResult<RuleBuilderDynamicEvaluationCommand> validateDynamicEvaluationCommand(RuleBuilderDynamicEvaluationCommand command) {
    if (command == null) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: command is required");
    }
    if (!isUuid(command.tenantId())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: tenantId must be a UUID");
    }
    if (isBlank(command.actorId()) || isBlank(command.correlationId()) || isBlank(command.versionRef())) {
      return GovernanceValidationResult.failure("VALIDATION_FAILED: actorId, versionRef, and correlationId are required");
    }
    if (command.permissions() == null || !command.permissions().contains(SIMULATE_PERMISSION)) {
      return GovernanceValidationResult.failure("TENANT_ACCESS_DENIED");
    }
    if (command.metadata() == null || command.ruleSet() == null || command.factsByDimension() == null) {
      return GovernanceValidationResult.failure("POLICY_NOT_SATISFIED: metadata, rule set, and typed facts are required");
    }
    return GovernanceValidationResult.success(command);
  }

  private List<RuleBuilderValidationMessage> validateRuleSet(RuleBuilderRuleSet ruleSet, RuleBuilderMetadata metadata) {
    List<RuleBuilderValidationMessage> messages = new ArrayList<>();
    if (isBlank(metadata.metadataVersion()) || metadata.dimensions().isEmpty() || metadata.operators().isEmpty() || metadata.actions().isEmpty()) {
      messages.add(new RuleBuilderValidationMessage("RULE_METADATA_MISSING", "$.metadata", "rule-builder.metadata.missing", true));
      return messages;
    }
    if (isBlank(ruleSet.ruleSetId()) || isBlank(ruleSet.schemaVersion()) || isBlank(ruleSet.name()) || ruleSet.metadataVersionRefs().isEmpty()) {
      messages.add(new RuleBuilderValidationMessage("RULE_SET_REQUIRED_FIELDS_MISSING", "$.ruleSet", "rule-builder.rule-set.required", true));
    }
    if (!metadata.precedenceStrategies().contains(ruleSet.precedenceStrategy())) {
      messages.add(new RuleBuilderValidationMessage("PRECEDENCE_STRATEGY_UNSUPPORTED", "$.precedenceStrategy", "rule-builder.precedence.unsupported", true));
    }
    Set<String> priorities = new HashSet<>();
    Set<String> ruleIds = new HashSet<>();
    for (RuleBuilderRule rule : ruleSet.rules()) {
      if (!ruleIds.add(rule.ruleId())) {
        messages.add(new RuleBuilderValidationMessage("DUPLICATE_RULE_ID", "$.rules." + rule.ruleId(), "rule-builder.rule-id.duplicate", true));
      }
      if (rule.enabled() && !metadata.duplicatePrioritiesAllowed() && !priorities.add(Integer.toString(rule.priority()))) {
        messages.add(new RuleBuilderValidationMessage("DUPLICATE_RULE_PRIORITY", "$.rules." + rule.ruleId() + ".priority", "rule-builder.priority.duplicate", true));
      }
      if (rule.actions().isEmpty()) {
        messages.add(new RuleBuilderValidationMessage("RULE_ACTION_REQUIRED", "$.rules." + rule.ruleId() + ".actions", "rule-builder.action.required", true));
      }
      for (RuleBuilderCondition condition : rule.conditions()) {
        validateCondition(metadata, rule, condition, messages);
      }
      for (RuleBuilderAction action : rule.actions()) {
        validateAction(metadata, rule, action, messages);
      }
    }
    return messages.stream().sorted(Comparator.comparing(RuleBuilderValidationMessage::code).thenComparing(RuleBuilderValidationMessage::jsonPath)).toList();
  }

  private void validateCondition(
      RuleBuilderMetadata metadata, RuleBuilderRule rule, RuleBuilderCondition condition, List<RuleBuilderValidationMessage> messages) {
    RuleBuilderDimensionMetadata dimension = metadata.dimensions().get(condition.dimensionRef());
    RuleBuilderOperatorMetadata operator = metadata.operators().get(condition.operatorRef());
    if (dimension == null) {
      messages.add(new RuleBuilderValidationMessage("DIMENSION_METADATA_MISSING", "$.rules." + rule.ruleId() + ".conditions." + condition.dimensionRef(), "rule-builder.dimension.missing", true));
      return;
    }
    if (operator == null || !dimension.operatorRefs().contains(condition.operatorRef())) {
      messages.add(new RuleBuilderValidationMessage("OPERATOR_METADATA_MISSING", "$.rules." + rule.ruleId() + ".conditions." + condition.operatorRef(), "rule-builder.operator.missing", true));
    }
    if (!dimension.valueSourceRefs().contains(condition.valueSourceRef())) {
      messages.add(new RuleBuilderValidationMessage("VALUE_SOURCE_METADATA_MISSING", "$.rules." + rule.ruleId() + ".conditions." + condition.valueSourceRef(), "rule-builder.value-source.missing", true));
    }
  }

  private RuleBuilderCustomFieldDescriptor toDescriptor(RuleBuilderMetadata metadata, RuleBuilderDimensionMetadata dimension) {
    List<String> validationMessages = new ArrayList<>();
    List<String> allowedOperators = dimension.operatorRefs().stream().sorted().toList();
    for (String operatorRef : allowedOperators) {
      if (!metadata.operators().containsKey(operatorRef)) {
        validationMessages.add("operator metadata missing: " + operatorRef);
      }
    }
    String dataType =
        allowedOperators.stream()
            .map(metadata.operators()::get)
            .filter(operator -> operator != null && !isBlank(operator.valueType()))
            .map(RuleBuilderOperatorMetadata::valueType)
            .sorted()
            .findFirst()
            .orElse("metadata-ref");
    return new RuleBuilderCustomFieldDescriptor(
        dimension.dimensionRef(),
        dimension.label(),
        dataType,
        allowedOperators,
        dimension.valueSourceRefs().stream().sorted().toList(),
        "CONFIRMED_OR_ESTIMATED",
        metadata.metadataVersionRef(),
        List.copyOf(validationMessages));
  }

  private List<RuleBuilderValidationMessage> requiredFactBlockers(RuleBuilderDynamicEvaluationCommand command, RuleBuilderRule rule) {
    List<RuleBuilderValidationMessage> blockers = new ArrayList<>();
    for (RuleBuilderCondition condition : rule.conditions()) {
      RuleBuilderTypedFact fact = command.factsByDimension().get(condition.dimensionRef());
      String path = "$.rules." + rule.ruleId() + ".conditions." + condition.dimensionRef();
      if (fact == null) {
        blockers.add(new RuleBuilderValidationMessage("UNKNOWN_FACT_FAIL_CLOSED", path, "rule-builder.fact.missing", true));
      } else if (fact.quality() == RuleBuilderFactQuality.UNKNOWN || fact.quality() == RuleBuilderFactQuality.CONFLICTING) {
        blockers.add(new RuleBuilderValidationMessage(fact.quality().name() + "_FACT_FAIL_CLOSED", path, "rule-builder.fact.quality-blocked", true));
      }
    }
    return blockers;
  }

  private RuleMatchResult matchesRule(Map<String, RuleBuilderDimensionMetadata> dimensions, Map<String, RuleBuilderTypedFact> facts, RuleBuilderRule rule) {
    List<RuleBuilderValidationMessage> messages = new ArrayList<>();
    for (RuleBuilderCondition condition : rule.conditions()) {
      RuleBuilderDimensionMetadata dimension = dimensions.get(condition.dimensionRef());
      RuleBuilderTypedFact fact = facts.get(condition.dimensionRef());
      if (dimension == null || fact == null) {
        continue;
      }
      String expectedValueRef = condition.expectedValueRef();
      if (isBlank(expectedValueRef)) {
        continue;
      }
      String actual = String.valueOf(fact.value());
      boolean matched;
      switch (condition.operatorRef()) {
        case "metadata-equals", "equals", "eq" -> matched = expectedValueRef.equals(actual);
        case "metadata-in", "in" -> matched = List.of(expectedValueRef.split(",")).stream().map(String::trim).anyMatch(actual::equals);
        default -> {
          messages.add(
              new RuleBuilderValidationMessage(
                  "OPERATOR_EVALUATION_UNSUPPORTED",
                  "$.rules." + rule.ruleId() + ".conditions." + condition.operatorRef(),
                  "rule-builder.operator.evaluation-unsupported",
                  true));
          return new RuleMatchResult(false, true, messages);
        }
      }
      if (!matched) {
        return new RuleMatchResult(false, false, messages);
      }
    }
    return new RuleMatchResult(true, false, messages);
  }

  private List<String> factRefs(Map<String, RuleBuilderTypedFact> facts, List<RuleBuilderCondition> conditions) {
    return conditions.stream()
        .map(condition -> facts.get(condition.dimensionRef()))
        .filter(fact -> fact != null && !isBlank(fact.factRef()))
        .map(RuleBuilderTypedFact::factRef)
        .sorted()
        .toList();
  }

  private void validateAction(
      RuleBuilderMetadata metadata, RuleBuilderRule rule, RuleBuilderAction action, List<RuleBuilderValidationMessage> messages) {
    RuleBuilderActionMetadata actionMetadata = metadata.actions().get(action.actionTypeRef());
    if (actionMetadata == null) {
      messages.add(new RuleBuilderValidationMessage("ACTION_METADATA_MISSING", "$.rules." + rule.ruleId() + ".actions." + action.actionTypeRef(), "rule-builder.action.missing", true));
      return;
    }
    if (isBlank(action.reasonCodeRef()) || !metadata.reasonCodeRefs().contains(action.reasonCodeRef())) {
      messages.add(new RuleBuilderValidationMessage("REASON_CODE_REQUIRED", "$.rules." + rule.ruleId() + ".actions." + action.actionId() + ".reasonCodeRef", "rule-builder.reason-code.required", true));
    }
    if (actionMetadata.formulaAction()) {
      if (isBlank(action.precisionRef()) || !metadata.precisionRefs().contains(action.precisionRef())) {
        messages.add(new RuleBuilderValidationMessage("FORMULA_PRECISION_REQUIRED", "$.rules." + rule.ruleId() + ".actions." + action.actionId() + ".precisionRef", "rule-builder.formula.precision.required", true));
      }
      if (isBlank(action.roundingRef()) || !metadata.roundingRefs().contains(action.roundingRef())) {
        messages.add(new RuleBuilderValidationMessage("FORMULA_ROUNDING_REQUIRED", "$.rules." + rule.ruleId() + ".actions." + action.actionId() + ".roundingRef", "rule-builder.formula.rounding.required", true));
      }
    }
  }

  private List<RuleBuilderRule> orderedEnabledRules(RuleBuilderRuleSet ruleSet) {
    return ruleSet.rules().stream().filter(RuleBuilderRule::enabled).sorted(Comparator.comparingInt(RuleBuilderRule::priority).thenComparing(RuleBuilderRule::ruleId)).toList();
  }

  private String firstSourceDimension(List<RuleBuilderCondition> conditions) {
    return conditions.stream().map(RuleBuilderCondition::dimensionRef).sorted().findFirst().orElse("");
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

  private String canonicalCommand(RuleBuilderDraftCommand command) {
    return String.join(
        "|",
        command.tenantId(),
        command.idempotencyKey(),
        command.actorId(),
        canonicalList(command.permissions()),
        command.metadata().metadataVersion(),
        canonicalRuleSet(command.ruleSet()),
        command.correlationId());
  }

  private String canonicalRuleSet(RuleBuilderRuleSet ruleSet) {
    return String.join(
        "|",
        ruleSet.ruleSetId(),
        ruleSet.schemaVersion(),
        ruleSet.name(),
        ruleSet.context(),
        ruleSet.precedenceStrategy(),
        canonicalList(ruleSet.metadataVersionRefs()),
        canonicalList(ruleSet.reasonCodeRefs()),
        ruleSet.rules().stream().sorted(Comparator.comparing(RuleBuilderRule::ruleId)).map(this::canonicalRule).reduce((left, right) -> left + ";" + right).orElse(""));
  }

  private String canonicalRule(RuleBuilderRule rule) {
    return rule.ruleId()
        + ":"
        + rule.displayName()
        + ":"
        + rule.enabled()
        + ":"
        + rule.priority()
        + ":"
        + rule.conditionGroups()
        + ":"
        + rule.conditions().stream().sorted(Comparator.comparing(RuleBuilderCondition::conditionId)).map(this::canonicalCondition).reduce((left, right) -> left + "," + right).orElse("")
        + ":"
        + rule.actions().stream().sorted(Comparator.comparing(RuleBuilderAction::actionId)).map(this::canonicalAction).reduce((left, right) -> left + "," + right).orElse("")
        + ":"
        + rule.stopProcessing();
  }

  private String canonicalCondition(RuleBuilderCondition condition) {
    return condition.conditionId() + ":" + condition.dimensionRef() + ":" + condition.operatorRef() + ":" + condition.valueSourceRef() + ":" + condition.expectedValueRef();
  }

  private String canonicalAction(RuleBuilderAction action) {
    return action.actionId() + ":" + action.actionTypeRef() + ":" + action.reasonCodeRef() + ":" + action.precisionRef() + ":" + action.roundingRef();
  }

  private String canonicalMessages(List<RuleBuilderValidationMessage> messages) {
    return messages.stream().sorted(Comparator.comparing(RuleBuilderValidationMessage::code).thenComparing(RuleBuilderValidationMessage::jsonPath)).map(message -> message.code() + ":" + message.jsonPath() + ":" + message.blocking()).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalList(List<String> values) {
    return values.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
  }

  private String canonicalMap(Map<String, String> values) {
    return values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue()).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String canonicalTypedFacts(Map<String, RuleBuilderTypedFact> values) {
    return values.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue().factRef() + ":" + entry.getValue().quality() + ":" + String.valueOf(entry.getValue().value()))
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private String canonicalDynamicOutputs(List<RuleBuilderDynamicActionOutput> outputs) {
    return outputs.stream()
        .sorted(Comparator.comparing(RuleBuilderDynamicActionOutput::ruleId).thenComparing(RuleBuilderDynamicActionOutput::actionId))
        .map(output -> output.ruleId() + ":" + output.actionId() + ":" + output.actionOutputRef() + ":" + canonicalList(output.factRefs()))
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

  private record IdempotencyEntry(String requestHash, RuleBuilderDraftResult draftResult) {}
}

record RuleBuilderDraftCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    List<String> permissions,
    RuleBuilderMetadata metadata,
    RuleBuilderRuleSet ruleSet,
    String correlationId) {}

record RuleBuilderSimulationCommand(
    String tenantId,
    String idempotencyKey,
    String actorId,
    List<String> permissions,
    RuleBuilderMetadata metadata,
    RuleBuilderRuleSet ruleSet,
    Map<String, String> factsByDimension,
    String versionRef,
    String correlationId) {}

record RuleBuilderDynamicEvaluationCommand(
    String tenantId,
    String actorId,
    List<String> permissions,
    RuleBuilderMetadata metadata,
    RuleBuilderRuleSet ruleSet,
    Map<String, RuleBuilderTypedFact> factsByDimension,
    String versionRef,
    String correlationId) {}

enum RuleBuilderFactQuality {
  CONFIRMED,
  ESTIMATED,
  UNKNOWN,
  CONFLICTING
}

record RuleBuilderTypedFact(Object value, String factRef, RuleBuilderFactQuality quality, String sourceRef, String precisionRef) {
  public RuleBuilderTypedFact {
    if (quality == null) {
      quality = RuleBuilderFactQuality.UNKNOWN;
    }
  }
}

record RuleBuilderMetadata(
    String metadataVersion,
    Map<String, RuleBuilderDimensionMetadata> dimensions,
    Map<String, RuleBuilderOperatorMetadata> operators,
    Map<String, RuleBuilderActionMetadata> actions,
    List<String> precedenceStrategies,
    List<String> precisionRefs,
    List<String> roundingRefs,
    List<String> reasonCodeRefs,
    boolean duplicatePrioritiesAllowed) {}

record RuleBuilderDimensionMetadata(String dimensionRef, String label, List<String> operatorRefs, List<String> valueSourceRefs) {}

record RuleBuilderOperatorMetadata(String operatorRef, String label, String valueType) {}

record RuleBuilderActionMetadata(String actionTypeRef, String label, boolean formulaAction) {}

record RuleBuilderRuleSet(
    String ruleSetId,
    String schemaVersion,
    String name,
    String context,
    String precedenceStrategy,
    List<RuleBuilderRule> rules,
    List<String> metadataVersionRefs,
    List<String> reasonCodeRefs) {}

record RuleBuilderRule(
    String ruleId,
    String displayName,
    boolean enabled,
    int priority,
    List<String> conditionGroups,
    List<RuleBuilderCondition> conditions,
    List<RuleBuilderAction> actions,
    boolean stopProcessing) {}

record RuleBuilderCondition(String conditionId, String dimensionRef, String operatorRef, String valueSourceRef, String expectedValueRef) {
  RuleBuilderCondition(String conditionId, String dimensionRef, String operatorRef, String valueSourceRef) {
    this(conditionId, dimensionRef, operatorRef, valueSourceRef, "");
  }
}

record RuleBuilderAction(String actionId, String actionTypeRef, String reasonCodeRef, String precisionRef, String roundingRef) {}

record RuleBuilderDraftResult(
    String ruleSetId,
    String versionId,
    String status,
    List<String> metadataVersionRefs,
    String payloadHash,
    List<RuleBuilderValidationMessage> validationMessages,
    String auditRef,
    String replayRef,
    String correlationId,
    Instant createdAt) {}

record RuleBuilderSimulationResult(
    String ruleSetId,
    String status,
    String resultHash,
    List<RuleBuilderValidationMessage> validationMessages,
    List<RuleBuilderLedgerEntry> ledger,
    String correlationId,
    Instant completedAt) {}

record RuleBuilderDynamicEvaluationResult(
    String ruleSetId,
    String status,
    String resultHash,
    String evidenceHash,
    List<RuleBuilderValidationMessage> validationMessages,
    List<RuleBuilderDynamicActionOutput> actionOutputs,
    List<String> matchedRuleIds,
    List<String> skippedRuleIds,
    String correlationId,
    Instant completedAt) {}

record RuleBuilderCustomFieldDescriptor(
    String stableId,
    String label,
    String dataType,
    List<String> allowedOperators,
    List<String> valueSources,
    String decisionQualityRequirement,
    String versionRef,
    List<String> validationMessages) {}

record RuleBuilderDynamicActionOutput(
    String ruleId,
    String actionId,
    String actionOutputRef,
    String ruleVersionRef,
    List<String> factRefs,
    String precisionRef,
    String roundingRef,
    String reasonCodeRef) {}

record RuleMatchResult(boolean matched, boolean blocked, List<RuleBuilderValidationMessage> messages) {}

record RuleBuilderValidationMessage(String code, String jsonPath, String messageKey, boolean blocking) {}

record RuleBuilderLedgerEntry(
    String ruleId,
    String actionId,
    String actionOutputRef,
    String sourceDimensionRef,
    String precisionRef,
    String roundingRef,
    String ruleVersionRef,
    String reasonCodeRef) {}
