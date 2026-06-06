package com.wcpe.scenarioanalysis;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class WhatIfGuardrailService {
  private static final String BLOCK = "BLOCK";
  private static final String WARN = "WARN";
  private static final String ALLOW = "ALLOW";

  private final WhatIfGuardrailRepository repository;
  private final Clock clock;

  public WhatIfGuardrailService() {
    this(new InMemoryWhatIfGuardrailRepository(), Clock.systemUTC());
  }

  public WhatIfGuardrailService(WhatIfGuardrailRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public EffectivePolicyResponse effectivePolicy(String tenantId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    Optional<GuardrailPolicy> policy = repository.findPublished(normalizedTenantId);
    if (policy.isEmpty()) {
      return new EffectivePolicyResponse(
          normalizedTenantId,
          null,
          0,
          true,
          "NO_EFFECTIVE_POLICY",
          List.of(new UiMessage("WHAT_IF_GUARDRAILS_MISSING", BLOCK,
              "What-if guardrail policy is not published; write actions fail closed.")),
          List.of(),
          "DB_FALLBACK");
    }
    GuardrailPolicy effective = policy.get();
    return new EffectivePolicyResponse(
        normalizedTenantId,
        effective.policyId(),
        effective.version(),
        false,
        "PUBLISHED",
        List.of(new UiMessage("WHAT_IF_NON_BINDING", "INFO",
            "What-if analysis is non-binding and must not be used as an approved quote.")),
        effective.rules(),
        "DB_FALLBACK");
  }

  public GuardrailPolicyResponse createPolicy(String tenantId, CreatePolicyCommand command) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (command == null) {
      throw new ValidationException("guardrail policy request is required");
    }
    String actorId = requireText(command.actorId(), "actorId is required");
    List<GuardrailRule> rules = validateRules(command.rules());
    Instant now = Instant.now(clock);
    GuardrailPolicy policy = new GuardrailPolicy(
        normalizedTenantId,
        UUID.randomUUID(),
        "DRAFT",
        1,
        rules,
        actorId,
        null,
        null,
        now,
        now);
    repository.save(policy);
    return GuardrailPolicyResponse.from(policy, List.of());
  }

  public GuardrailPolicyResponse validatePolicy(String tenantId, UUID policyId) {
    GuardrailPolicy policy = requirePolicy(tenantId, policyId);
    List<String> messages = validationMessages(policy);
    if (!messages.isEmpty()) {
      throw new PolicyNotSatisfiedException("guardrail policy validation failed: " + String.join(", ", messages));
    }
    GuardrailPolicy validated = policy.withStatus("VALIDATED", policy.approvedBy(), policy.publishedBy(), Instant.now(clock));
    repository.save(validated);
    return GuardrailPolicyResponse.from(validated, List.of("policy validated"));
  }

  public GuardrailPolicyResponse publishPolicy(String tenantId, UUID policyId, PublishPolicyCommand command) {
    GuardrailPolicy policy = requirePolicy(tenantId, policyId);
    if (command == null) {
      throw new ValidationException("publish request is required");
    }
    String actorId = requireText(command.actorId(), "actorId is required");
    if (actorId.equals(policy.createdBy())) {
      throw new PolicyNotSatisfiedException("creator cannot publish their own guardrail policy");
    }
    if (!"VALIDATED".equals(policy.status()) && !"APPROVED".equals(policy.status())) {
      throw new PolicyNotSatisfiedException("guardrail policy must be validated before publish");
    }
    repository.findPublished(policy.tenantId())
        .ifPresent(existing -> repository.save(existing.withStatus("ROLLED_BACK", existing.approvedBy(), existing.publishedBy(), Instant.now(clock))));
    GuardrailPolicy published = policy.withStatus("PUBLISHED", actorId, actorId, Instant.now(clock));
    repository.save(published);
    repository.appendEvent(new GuardrailEvent(
        UUID.randomUUID(),
        "whatif.guardrail_policy.published.v1",
        published.tenantId(),
        published.policyId(),
        actorId,
        published.version(),
        Instant.now(clock)));
    return GuardrailPolicyResponse.from(published, List.of("policy published"));
  }

  public GuardrailPolicyResponse rollbackPolicy(String tenantId, UUID policyId, PublishPolicyCommand command) {
    GuardrailPolicy policy = requirePolicy(tenantId, policyId);
    if (command == null) {
      throw new ValidationException("rollback request is required");
    }
    String actorId = requireText(command.actorId(), "actorId is required");
    if (actorId.equals(policy.createdBy())) {
      throw new PolicyNotSatisfiedException("creator cannot rollback their own guardrail policy");
    }
    GuardrailPolicy rolledBack = policy.withStatus("ROLLED_BACK", policy.approvedBy(), policy.publishedBy(), Instant.now(clock));
    repository.save(rolledBack);
    return GuardrailPolicyResponse.from(rolledBack, List.of("policy rolled back"));
  }

  public EvaluationResponse evaluate(String tenantId, EvaluateCommand command) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (command == null) {
      throw new ValidationException("guardrail evaluation request is required");
    }
    String action = requireText(command.action(), "action is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    Map<String, String> context = command.context() == null ? Map.of() : Map.copyOf(command.context());
    Optional<GuardrailPolicy> policy = repository.findPublished(normalizedTenantId);
    List<GuardrailDecision> decisions = new ArrayList<>(staticDecisions(normalizedTenantId, action, context));
    if (policy.isEmpty() && !"VIEW_ONLY".equals(action)) {
      decisions.add(new GuardrailDecision("NO_EFFECTIVE_POLICY", action, BLOCK,
          "No effective what-if guardrail policy is published for this tenant."));
    }
    policy.ifPresent(effective -> decisions.addAll(policyDecisions(action, effective.rules())));
    String severity = highestSeverity(decisions);
    UUID decisionId = UUID.randomUUID();
    EvaluationResponse response = new EvaluationResponse(
        decisionId,
        normalizedTenantId,
        action,
        severity,
        policy.map(GuardrailPolicy::policyId).orElse(null),
        policy.map(GuardrailPolicy::version).orElse(0),
        decisions,
        "DB_FALLBACK",
        Instant.now(clock));
    repository.appendDecision(response.withActor(actorId));
    if (BLOCK.equals(severity)) {
      repository.appendEvent(new GuardrailEvent(
          UUID.randomUUID(),
          "whatif.guardrail_violation.blocked.v1",
          normalizedTenantId,
          policy.map(GuardrailPolicy::policyId).orElse(null),
          actorId,
          policy.map(GuardrailPolicy::version).orElse(0),
          Instant.now(clock)));
    }
    return response;
  }

  private static List<GuardrailDecision> staticDecisions(String tenantId, String action, Map<String, String> context) {
    List<GuardrailDecision> decisions = new ArrayList<>();
    if (!tenantId.equals(context.getOrDefault("sourceTenantId", tenantId))) {
      decisions.add(new GuardrailDecision("CROSS_TENANT_REF", action, BLOCK,
          "What-if requests cannot reference another tenant's data."));
    }
    if ("true".equalsIgnoreCase(context.getOrDefault("mutatesSourceQuote", "false"))) {
      decisions.add(new GuardrailDecision("SOURCE_QUOTE_MUTATION", action, BLOCK,
          "What-if analysis cannot mutate the source quote."));
    }
    if ("true".equalsIgnoreCase(context.getOrDefault("createsRealLock", "false"))) {
      decisions.add(new GuardrailDecision("REAL_LOCK_CREATION", action, BLOCK,
          "What-if analysis cannot create a real lock."));
    }
    if ("true".equalsIgnoreCase(context.getOrDefault("usesExternalMl", "false"))) {
      decisions.add(new GuardrailDecision("EXTERNAL_ML_CALL", action, BLOCK,
          "What-if guardrails do not allow external ML calls."));
    }
    if ("true".equalsIgnoreCase(context.getOrDefault("containsRawPii", "false"))) {
      decisions.add(new GuardrailDecision("RAW_PII_IN_EVENT", action, BLOCK,
          "What-if events and logs cannot contain raw PII."));
    }
    if ("true".equalsIgnoreCase(context.getOrDefault("containsAdverseActionWording", "false"))) {
      decisions.add(new GuardrailDecision("ADVERSE_ACTION_WORDING", action, BLOCK,
          "What-if output cannot be worded as an adverse-action notice."));
    }
    if ("EXPORT".equals(action)
        && "BORROWER".equals(context.getOrDefault("recipientType", ""))
        && !"true".equalsIgnoreCase(context.getOrDefault("activeDisclaimerTemplate", "false"))) {
      decisions.add(new GuardrailDecision("BORROWER_EXPORT_TEMPLATE_REQUIRED", action, BLOCK,
          "Borrower-facing export requires an active disclaimer template."));
    }
    if ("RUN_SENSITIVITY".equals(action) && !context.containsKey("maxCellsConfigured")) {
      decisions.add(new GuardrailDecision("MAX_CELLS_POLICY_REQUIRED", action, BLOCK,
          "Max cells policy is required before running sensitivity analysis."));
    }
    return decisions;
  }

  private static List<GuardrailDecision> policyDecisions(String action, List<GuardrailRule> rules) {
    List<GuardrailDecision> decisions = new ArrayList<>();
    for (GuardrailRule rule : rules) {
      if (action.equals(rule.action()) || "ALL".equals(rule.action())) {
        decisions.add(new GuardrailDecision(rule.ruleCode(), action, rule.severity(), rule.message()));
      }
    }
    return decisions;
  }

  private static String highestSeverity(List<GuardrailDecision> decisions) {
    if (decisions.stream().anyMatch(decision -> BLOCK.equals(decision.severity()))) {
      return BLOCK;
    }
    if (decisions.stream().anyMatch(decision -> WARN.equals(decision.severity()))) {
      return WARN;
    }
    return ALLOW;
  }

  private GuardrailPolicy requirePolicy(String tenantId, UUID policyId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (policyId == null) {
      throw new ValidationException("policyId is required");
    }
    return repository.findById(normalizedTenantId, policyId)
        .orElseThrow(() -> new NotFoundException("guardrail policy was not found"));
  }

  private static List<GuardrailRule> validateRules(List<GuardrailRule> rules) {
    if (rules == null || rules.isEmpty()) {
      throw new PolicyNotSatisfiedException("at least one guardrail rule is required");
    }
    List<GuardrailRule> validRules = new ArrayList<>();
    for (GuardrailRule rule : rules) {
      if (rule == null) {
        throw new ValidationException("guardrail rule is required");
      }
      String ruleCode = requireText(rule.ruleCode(), "ruleCode is required");
      String action = requireText(rule.action(), "action is required");
      String severity = requireText(rule.severity(), "severity is required");
      if (!List.of(ALLOW, WARN, BLOCK).contains(severity)) {
        throw new ValidationException("severity must be ALLOW, WARN, or BLOCK");
      }
      String message = requireText(rule.message(), "message is required");
      validRules.add(new GuardrailRule(ruleCode, action, severity, message));
    }
    return List.copyOf(validRules);
  }

  private static List<String> validationMessages(GuardrailPolicy policy) {
    List<String> messages = new ArrayList<>();
    if (!"DRAFT".equals(policy.status()) && !"VALIDATED".equals(policy.status())) {
      messages.add("only draft or validated policies can be validated");
    }
    if (policy.rules().stream().noneMatch(rule -> BLOCK.equals(rule.severity()))) {
      messages.add("policy must include at least one blocking rule");
    }
    return messages;
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(message);
    }
    return value.trim();
  }

  public record CreatePolicyCommand(String actorId, List<GuardrailRule> rules) {}

  public record PublishPolicyCommand(String actorId) {}

  public record EvaluateCommand(String action, String actorId, Map<String, String> context) {}

  public record GuardrailRule(String ruleCode, String action, String severity, String message) {}

  public record UiMessage(String messageKey, String severity, String message) {}

  public record GuardrailDecision(String ruleCode, String action, String severity, String message) {}

  public record EffectivePolicyResponse(
      String tenantId,
      UUID policyId,
      int version,
      boolean failClosed,
      String status,
      List<UiMessage> uiMessages,
      List<GuardrailRule> rules,
      String cacheStatus) {}

  public record GuardrailPolicyResponse(
      String tenantId,
      UUID policyId,
      String status,
      int version,
      List<GuardrailRule> rules,
      String createdBy,
      String approvedBy,
      String publishedBy,
      List<String> validationMessages) {
    static GuardrailPolicyResponse from(GuardrailPolicy policy, List<String> messages) {
      return new GuardrailPolicyResponse(
          policy.tenantId(),
          policy.policyId(),
          policy.status(),
          policy.version(),
          policy.rules(),
          policy.createdBy(),
          policy.approvedBy(),
          policy.publishedBy(),
          messages);
    }
  }

  public record EvaluationResponse(
      UUID decisionId,
      String tenantId,
      String action,
      String severity,
      UUID policyId,
      int policyVersion,
      List<GuardrailDecision> decisions,
      String cacheStatus,
      Instant decidedAt) {
    StoredDecision withActor(String actorId) {
      return new StoredDecision(decisionId, tenantId, action, severity, policyId, policyVersion, decisions, actorId, decidedAt);
    }
  }

  public record GuardrailEvent(
      UUID eventId,
      String eventType,
      String tenantId,
      UUID policyId,
      String actorId,
      int policyVersion,
      Instant occurredAt) {}

  public record StoredDecision(
      UUID decisionId,
      String tenantId,
      String action,
      String severity,
      UUID policyId,
      int policyVersion,
      List<GuardrailDecision> decisions,
      String actorId,
      Instant decidedAt) {}

  public record GuardrailPolicy(
      String tenantId,
      UUID policyId,
      String status,
      int version,
      List<GuardrailRule> rules,
      String createdBy,
      String approvedBy,
      String publishedBy,
      Instant createdAt,
      Instant updatedAt) {
    GuardrailPolicy withStatus(String status, String approvedBy, String publishedBy, Instant updatedAt) {
      return new GuardrailPolicy(tenantId, policyId, status, version, rules, createdBy, approvedBy, publishedBy, createdAt, updatedAt);
    }
  }

  public interface WhatIfGuardrailRepository {
    Optional<GuardrailPolicy> findById(String tenantId, UUID policyId);

    Optional<GuardrailPolicy> findPublished(String tenantId);

    void save(GuardrailPolicy policy);

    void appendDecision(StoredDecision decision);

    void appendEvent(GuardrailEvent event);
  }

  public static class InMemoryWhatIfGuardrailRepository implements WhatIfGuardrailRepository {
    private final Map<String, GuardrailPolicy> policies = new ConcurrentHashMap<>();
    private final List<StoredDecision> decisions = new ArrayList<>();
    private final List<GuardrailEvent> events = new ArrayList<>();

    @Override
    public Optional<GuardrailPolicy> findById(String tenantId, UUID policyId) {
      return Optional.ofNullable(policies.get(key(tenantId, policyId)));
    }

    @Override
    public Optional<GuardrailPolicy> findPublished(String tenantId) {
      return policies.values().stream()
          .filter(policy -> policy.tenantId().equals(tenantId))
          .filter(policy -> "PUBLISHED".equals(policy.status()))
          .findFirst();
    }

    @Override
    public void save(GuardrailPolicy policy) {
      policies.put(key(policy.tenantId(), policy.policyId()), policy);
    }

    @Override
    public void appendDecision(StoredDecision decision) {
      decisions.add(decision);
    }

    @Override
    public void appendEvent(GuardrailEvent event) {
      events.add(event);
    }

    public List<StoredDecision> decisions() {
      return List.copyOf(decisions);
    }

    public List<GuardrailEvent> events() {
      return List.copyOf(events);
    }

    private static String key(String tenantId, UUID policyId) {
      return tenantId + ':' + policyId;
    }
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class PolicyNotSatisfiedException extends RuntimeException {
    public PolicyNotSatisfiedException(String message) {
      super(message);
    }
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }
}
