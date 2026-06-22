package com.wcpe.margin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProfitabilityFloorService {
  public static final String POLICY_TYPE = "PROFITABILITY_FLOOR";
  public static final String STEP_TYPE = "PROFITABILITY_FLOOR_EVALUATION";
  public static final String BREACH_CODE = "PROFITABILITY_FLOOR_BREACH";
  public static final String POLICY_MISSING_CODE = "PROFITABILITY_POLICY_MISSING";
  public static final String EXCEPTION_REQUIRED_CODE = "PROFITABILITY_EXCEPTION_REQUIRED";
  public static final String SENSITIVE_PROFITABILITY_PERMISSION = "pricing.margin.profitability.view_sensitive";

  public final AtomicInteger profitabilityEvaluationTotal = new AtomicInteger();
  public final AtomicInteger profitabilityFloorBreachTotal = new AtomicInteger();
  public final AtomicInteger profitabilityPolicyMissingTotal = new AtomicInteger();
  public final AtomicInteger profitabilityVisibilityRedactionTotal = new AtomicInteger();

  private final Clock clock;
  private final Store store;

  public ProfitabilityFloorService(Clock clock) {
    this(clock, Store.failClosed("ProfitabilityFloorService"));
  }

  ProfitabilityFloorService(Clock clock, Store store) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.store = Objects.requireNonNull(store, "store is required");
  }

  public CommandReceipt createDraftPolicy(String tenantId, String requestId, String actorId, String idempotencyKey,
      String correlationId, String name, ProfitabilityPolicyVersion version) {
    requireDurableStoreOrExplicitTestHarness();
    requireCommand(tenantId, actorId, idempotencyKey, correlationId);
    requireText(requestId, "requestId");
    requireText(name, "name");
    Objects.requireNonNull(version, "version is required");
    validateVersion(version);
    String requestHash = requestHash(requestId, actorId, name, version);
    String idemKey = idempotencyKey(tenantId, idempotencyKey);
    IdempotencyRecord existing = store.idempotencyReceipts().get(idemKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        throw new ProfitabilityFloorException("IDEMPOTENCY_CONFLICT");
      }
      return existing.receipt();
    }
    String policyId = UUID.randomUUID().toString();
    Instant now = Instant.now(clock);
    ProfitabilityPolicy policy = new ProfitabilityPolicy(tenantId, policyId, name, PolicyStatus.DRAFT,
        List.of(version), actorId, actorId, now, now);
    store.policies().put(new PolicyKey(tenantId, policyId), policy);
    CommandReceipt receipt = new CommandReceipt(policyId, PolicyStatus.DRAFT, version.versionNumber(), correlationId,
        requestHash, List.of(), "audit:" + policyId);
    store.idempotencyReceipts().put(idemKey, new IdempotencyRecord(requestHash, receipt));
    store.auditRecords().add(AuditRecord.completed(tenantId, policyId, actorId, correlationId,
        "PROFITABILITY_FLOOR_DRAFT_CREATED", clock));
    return receipt;
  }

  public CommandReceipt submit(String tenantId, String policyId, String actorId, String correlationId) {
    return transition(tenantId, policyId, actorId, correlationId, PolicyStatus.DRAFT, PolicyStatus.PENDING_APPROVAL,
        "PROFITABILITY_FLOOR_SUBMITTED");
  }

  public CommandReceipt approve(String tenantId, String policyId, String actorId, String correlationId) {
    ProfitabilityPolicy policy = findById(tenantId, policyId);
    if (policy.createdBy().equals(actorId)) {
      throw new ProfitabilityFloorException("PROFITABILITY_APPROVAL_SOD_VIOLATION");
    }
    return transition(tenantId, policyId, actorId, correlationId, PolicyStatus.PENDING_APPROVAL,
        PolicyStatus.APPROVED, "PROFITABILITY_FLOOR_APPROVED");
  }

  public CommandReceipt publish(String tenantId, String policyId, String actorId, String correlationId) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    ProfitabilityPolicy policy = findById(tenantId, policyId);
    if (policy.status() != PolicyStatus.APPROVED) {
      throw new ProfitabilityFloorException("VERSION_CONFLICT");
    }
    rejectPublishedOverlap(policy);
    Instant now = Instant.now(clock);
    ProfitabilityPolicy published = policy.withStatus(PolicyStatus.PUBLISHED, actorId, now);
    store.policies().put(new PolicyKey(tenantId, policyId), published);
    ProfitabilityPolicyVersion version = published.currentVersion();
    ProfitabilityPolicyPublishedEvent event = new ProfitabilityPolicyPublishedEvent(tenantId, policyId,
        version.versionId(), version.scope().stableHash(), version.effectiveWindow().effectiveFromUtc(),
        version.configHash(), actorId, correlationId, now);
    store.outbox().add(event);
    store.auditRecords().add(AuditRecord.completed(tenantId, policyId, actorId, correlationId,
        "PROFITABILITY_POLICY_PUBLISHED", clock));
    return new CommandReceipt(policyId, PolicyStatus.PUBLISHED, version.versionNumber(), correlationId,
        "publish:" + policyId, List.of(event), "audit:" + policyId);
  }

  public ProfitabilityDecision simulate(String tenantId, String policyId, ConfigResolver configResolver,
      ProfitabilityEvaluationInput input) {
    ProfitabilityPolicy policy = findById(tenantId, policyId);
    return evaluatePolicy(policy, configResolver, input, false);
  }

  public ProfitabilityDecision evaluateQuote(String tenantId, ProfitabilityScope quoteScope, ConfigResolver configResolver,
      ProfitabilityEvaluationInput input) {
    requireText(tenantId, "tenantId");
    Objects.requireNonNull(quoteScope, "quoteScope is required");
    profitabilityEvaluationTotal.incrementAndGet();
    ProfitabilityPolicy policy = resolvePublished(tenantId, quoteScope, input.effectiveAtUtc())
        .orElseThrow(() -> failClosedPolicyMissing(tenantId, input.quoteOptionId(), input.correlationId()));
    return evaluatePolicy(policy, configResolver, input, true);
  }

  public ProfitabilityDecision applyVisibility(String viewerPermission, ProfitabilityDecision decision) {
    Objects.requireNonNull(decision, "decision is required");
    if (SENSITIVE_PROFITABILITY_PERMISSION.equals(viewerPermission)) {
      return decision;
    }
    profitabilityVisibilityRedactionTotal.incrementAndGet();
    return new ProfitabilityDecision(decision.quoteId(), decision.quoteOptionId(), decision.policyId(),
        decision.policyVersionId(), decision.basis(), null, null, null, null, null, decision.decision(),
        decision.decisionCode(), null, decision.replayHash(), decision.waterfallSteps().stream()
            .map(step -> new WaterfallStep(step.stepType(), step.quoteOptionId(), step.policyId(),
                step.policyVersionId(), null, null, null, step.decision(), step.replayHash()))
            .toList());
  }

  public Optional<ProfitabilityPolicy> resolvePublished(String tenantId, ProfitabilityScope scope, Instant effectiveAtUtc) {
    requireDurableStoreOrExplicitTestHarness();
    List<ProfitabilityPolicy> matches = store.policies().values().stream()
        .filter(policy -> policy.tenantId().equals(tenantId))
        .filter(policy -> policy.status() == PolicyStatus.PUBLISHED)
        .filter(policy -> policy.currentVersion().effectiveWindow().contains(effectiveAtUtc))
        .filter(policy -> policy.currentVersion().scope().matches(scope))
        .toList();
    if (matches.size() > 1) {
      throw new ProfitabilityFloorException("PROFITABILITY_POLICY_OVERLAP");
    }
    return matches.stream().findFirst();
  }

  public List<Object> outboxEvents() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.outbox());
  }

  public List<AuditRecord> auditRecords() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.auditRecords());
  }

  public List<ProfitabilityEvaluation> evaluations() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.evaluations());
  }

  private void requireDurableStoreOrExplicitTestHarness() {
    store.requireAvailable();
  }

  private ProfitabilityDecision evaluatePolicy(ProfitabilityPolicy policy, ConfigResolver configResolver,
      ProfitabilityEvaluationInput input, boolean emitBreachEvent) {
    Objects.requireNonNull(configResolver, "configResolver is required");
    Objects.requireNonNull(input, "input is required");
    ProfitabilityPolicyVersion version = policy.currentVersion();
    ProfitabilityRule rule = selectRule(version, input.scope());
    BigDecimal loadedPrice = input.priceAfterMarginsAndComp().add(input.approvedConcessionsImpact());
    BigDecimal threshold = resolveRequired(configResolver, rule.thresholdRef());
    BigDecimal metric = metric(rule.floorBasis(), loadedPrice, input, configResolver);
    boolean breached = metric.compareTo(threshold) < 0;
    FloorDecision decision = breached ? decisionFor(rule.action()) : FloorDecision.PASS;
    String decisionCode = breached ? codeFor(rule.action()) : "PROFITABILITY_FLOOR_PASS";
    String replayHash = replayHash(policy.policyId(), version.versionId(), rule, input, loadedPrice, metric, threshold);
    ProfitabilityEvaluation evaluation = new ProfitabilityEvaluation(input.quoteId(), input.quoteOptionId(),
        policy.policyId(), version.versionId(), rule.floorBasis(), metric, rule.thresholdRef(), rule.action(), decision,
        replayHash);
    store.evaluations().add(evaluation);
    ProfitabilityDecision result = new ProfitabilityDecision(input.quoteId(), input.quoteOptionId(), policy.policyId(),
        version.versionId(), rule.floorBasis(), loadedPrice, metric, threshold, rule.thresholdRef(), rule.action(),
        decision, decisionCode, rule.exceptionRouteRef(), replayHash,
        List.of(new WaterfallStep(STEP_TYPE, input.quoteOptionId(), policy.policyId(), version.versionId(), loadedPrice,
            metric, threshold, decision, replayHash)));
    if (breached) {
      profitabilityFloorBreachTotal.incrementAndGet();
      if (emitBreachEvent) {
        store.outbox().add(new ProfitabilityFloorBreachedEvent(input.quoteId(), input.quoteOptionId(), policy.policyId(),
            version.versionId(), rule.floorBasis(), rule.thresholdRef(), rule.action(), decision, input.correlationId(),
            Instant.now(clock)));
      }
      store.auditRecords().add(AuditRecord.completed(policy.tenantId(), policy.policyId(), "system", input.correlationId(),
          "PROFITABILITY_FLOOR_BREACHED", clock));
    } else {
      store.auditRecords().add(AuditRecord.completed(policy.tenantId(), policy.policyId(), "system", input.correlationId(),
          "PROFITABILITY_FLOOR_EVALUATED", clock));
    }
    return result;
  }

  private ProfitabilityFloorException failClosedPolicyMissing(String tenantId, String quoteOptionId, String correlationId) {
    profitabilityPolicyMissingTotal.incrementAndGet();
    store.auditRecords().add(AuditRecord.completed(tenantId, quoteOptionId, "system", correlationId,
        "PROFITABILITY_POLICY_MISSING", clock));
    return new ProfitabilityFloorException(POLICY_MISSING_CODE);
  }

  private static BigDecimal metric(FloorBasis basis, BigDecimal loadedPrice, ProfitabilityEvaluationInput input,
      ConfigResolver configResolver) {
    return switch (basis) {
      case NET_PRICE -> loadedPrice;
      case NET_MARGIN_BPS -> {
        BigDecimal investorPrice = input.investorPrice()
            .orElseThrow(() -> new ProfitabilityFloorException("POLICY_NOT_SATISFIED"));
        yield loadedPrice.subtract(investorPrice).movePointRight(2).setScale(3, RoundingMode.HALF_UP);
      }
      case DOLLAR_PROFIT -> {
        String costRef = input.configuredCostRef()
            .orElseThrow(() -> new ProfitabilityFloorException("POLICY_NOT_SATISFIED"));
        BigDecimal configuredCost = resolveRequired(configResolver, costRef);
        BigDecimal loanAmount = input.loanAmount()
            .orElseThrow(() -> new ProfitabilityFloorException("POLICY_NOT_SATISFIED"));
        yield loadedPrice.subtract(configuredCost).multiply(loanAmount).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
      }
    };
  }

  private static BigDecimal resolveRequired(ConfigResolver configResolver, String ref) {
    requireText(ref, "configRef");
    return configResolver.resolve(ref).orElseThrow(() -> new ProfitabilityFloorException("POLICY_NOT_SATISFIED"));
  }

  private ProfitabilityRule selectRule(ProfitabilityPolicyVersion version, ProfitabilityScope quoteScope) {
    List<ProfitabilityRule> matches = version.rules().stream()
        .filter(rule -> rule.scope().matches(quoteScope))
        .sorted(Comparator.comparingInt((ProfitabilityRule rule) -> specificity(rule.scope())).reversed()
            .thenComparingInt(ProfitabilityRule::priority))
        .toList();
    if (matches.isEmpty()) {
      throw new ProfitabilityFloorException(POLICY_MISSING_CODE);
    }
    ProfitabilityRule best = matches.get(0);
    long ambiguous = matches.stream()
        .filter(rule -> specificity(rule.scope()) == specificity(best.scope()))
        .filter(rule -> rule.priority() == best.priority())
        .count();
    if (ambiguous > 1) {
      throw new ProfitabilityFloorException("PROFITABILITY_POLICY_OVERLAP");
    }
    return best;
  }

  private CommandReceipt transition(String tenantId, String policyId, String actorId, String correlationId,
      PolicyStatus from, PolicyStatus to, String action) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    ProfitabilityPolicy policy = findById(tenantId, policyId);
    if (policy.status() != from) {
      throw new ProfitabilityFloorException("VERSION_CONFLICT");
    }
    ProfitabilityPolicy changed = policy.withStatus(to, actorId, Instant.now(clock));
    store.policies().put(new PolicyKey(tenantId, policyId), changed);
    store.auditRecords().add(AuditRecord.completed(tenantId, policyId, actorId, correlationId, action, clock));
    return new CommandReceipt(policyId, to, changed.currentVersion().versionNumber(), correlationId,
        action + ":" + policyId, List.of(), "audit:" + policyId);
  }

  private void rejectPublishedOverlap(ProfitabilityPolicy candidate) {
    boolean overlap = store.policies().values().stream()
        .filter(policy -> !policy.policyId().equals(candidate.policyId()))
        .filter(policy -> policy.tenantId().equals(candidate.tenantId()))
        .filter(policy -> policy.status() == PolicyStatus.PUBLISHED)
        .anyMatch(policy -> policy.currentVersion().scope().matches(candidate.currentVersion().scope())
            && policy.currentVersion().effectiveWindow().overlaps(candidate.currentVersion().effectiveWindow()));
    if (overlap) {
      throw new ProfitabilityFloorException("PROFITABILITY_POLICY_OVERLAP");
    }
  }

  private ProfitabilityPolicy findById(String tenantId, String policyId) {
    requireDurableStoreOrExplicitTestHarness();
    return store.policies().values().stream()
        .filter(policy -> policy.tenantId().equals(tenantId))
        .filter(policy -> policy.policyId().equals(policyId))
        .findFirst()
        .orElseThrow(() -> new ProfitabilityFloorException("NOT_FOUND"));
  }

  private static void validateVersion(ProfitabilityPolicyVersion version) {
    if (version.rules().isEmpty()) {
      throw new ProfitabilityFloorException("VALIDATION_FAILED");
    }
    for (ProfitabilityRule rule : version.rules()) {
      Objects.requireNonNull(rule.floorBasis(), "floorBasis is required");
      Objects.requireNonNull(rule.action(), "action is required");
      requireText(rule.thresholdRef(), "thresholdRef");
      requireText(rule.reasonCode(), "reasonCode");
      if (rule.action() == FloorAction.REQUIRE_EXCEPTION) {
        requireText(rule.exceptionRouteRef(), "exceptionRouteRef");
      }
    }
  }

  private static FloorDecision decisionFor(FloorAction action) {
    return switch (action) {
      case BLOCK -> FloorDecision.EXCLUDED;
      case WARN -> FloorDecision.INCLUDED_WITH_WARNING;
      case REQUIRE_EXCEPTION -> FloorDecision.NON_BINDABLE_EXCEPTION_REQUIRED;
    };
  }

  private static String codeFor(FloorAction action) {
    return action == FloorAction.REQUIRE_EXCEPTION ? EXCEPTION_REQUIRED_CODE : BREACH_CODE;
  }

  private static int specificity(ProfitabilityScope scope) {
    return specific(scope.productFamily()) + specific(scope.channel()) + specific(scope.investorGroup())
        + specific(scope.branchId()) + specific(scope.lockPeriodBucket());
  }

  private static int specific(String value) {
    return "*".equals(value) ? 0 : 1;
  }

  private static void requireCommand(String tenantId, String actorId, String idempotencyKey, String correlationId) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(idempotencyKey, "idempotencyKey");
    requireText(correlationId, "correlationId");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ProfitabilityFloorException(field + " is required");
    }
  }

  private static String idempotencyKey(String tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String requestHash(Object... values) {
    return Integer.toHexString(Objects.hash(values));
  }

  private static String replayHash(Object... values) {
    return Integer.toHexString(Objects.hash(values));
  }

  interface Store {
    Map<PolicyKey, ProfitabilityPolicy> policies();
    Map<String, IdempotencyRecord> idempotencyReceipts();
    List<Object> outbox();
    List<AuditRecord> auditRecords();
    List<ProfitabilityEvaluation> evaluations();

    default void requireAvailable() {}

    static Store failClosed(String component) {
      return new Store() {
        @Override public void requireAvailable() {
          ProcessLocalStatePolicy.requireDurableStoreOrExplicitTestHarness(false, component);
        }
        @Override public Map<PolicyKey, ProfitabilityPolicy> policies() { return unavailable(); }
        @Override public Map<String, IdempotencyRecord> idempotencyReceipts() { return unavailable(); }
        @Override public List<Object> outbox() { return unavailable(); }
        @Override public List<AuditRecord> auditRecords() { return unavailable(); }
        @Override public List<ProfitabilityEvaluation> evaluations() { return unavailable(); }
        private <T> T unavailable() {
          requireAvailable();
          throw new AssertionError("unreachable");
        }
      };
    }
  }

  record PolicyKey(String tenantId, String policyId) {}

  record IdempotencyRecord(String requestHash, CommandReceipt receipt) {}

  public enum PolicyStatus { DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, SUSPENDED }

  public enum FloorBasis { NET_PRICE, NET_MARGIN_BPS, DOLLAR_PROFIT }

  public enum FloorAction { BLOCK, WARN, REQUIRE_EXCEPTION }

  public enum FloorDecision { PASS, EXCLUDED, INCLUDED_WITH_WARNING, NON_BINDABLE_EXCEPTION_REQUIRED }

  public interface ConfigResolver {
    Optional<BigDecimal> resolve(String ref);
  }

  public record CommandReceipt(String policyId, PolicyStatus status, int version, String correlationId,
      String requestHash, List<Object> events, String auditRef) {}

  public record ProfitabilityPolicy(String tenantId, String policyId, String name, PolicyStatus status,
      List<ProfitabilityPolicyVersion> versions, String createdBy, String updatedBy, Instant createdAt,
      Instant updatedAt) {
    public ProfitabilityPolicyVersion currentVersion() {
      return versions.stream().max(Comparator.comparingInt(ProfitabilityPolicyVersion::versionNumber)).orElseThrow();
    }

    ProfitabilityPolicy withStatus(PolicyStatus status, String actorId, Instant now) {
      return new ProfitabilityPolicy(tenantId, policyId, name, status, versions, createdBy, actorId, createdAt, now);
    }
  }

  public record ProfitabilityPolicyVersion(String versionId, int versionNumber, ProfitabilityScope scope,
      EffectiveWindow effectiveWindow, List<ProfitabilityRule> rules, String configHash) {
    public ProfitabilityPolicyVersion {
      scope = Objects.requireNonNull(scope, "scope is required");
      effectiveWindow = Objects.requireNonNull(effectiveWindow, "effectiveWindow is required");
      rules = List.copyOf(Objects.requireNonNull(rules, "rules is required"));
      requireText(configHash, "configHash");
    }
  }

  public record ProfitabilityRule(int priority, FloorBasis floorBasis, String thresholdRef, FloorAction action,
      String exceptionRouteRef, String reasonCode, ProfitabilityScope scope) {
    public ProfitabilityRule {
      scope = Objects.requireNonNull(scope, "scope is required");
    }
  }

  public record ProfitabilityScope(String productFamily, String channel, String investorGroup, String branchId,
      String lockPeriodBucket) {
    boolean matches(ProfitabilityScope other) {
      return match(productFamily, other.productFamily) && match(channel, other.channel)
          && match(investorGroup, other.investorGroup) && match(branchId, other.branchId)
          && match(lockPeriodBucket, other.lockPeriodBucket);
    }

    String stableHash() {
      return Integer.toHexString(Objects.hash(productFamily, channel, investorGroup, branchId, lockPeriodBucket));
    }

    private static boolean match(String left, String right) {
      return "*".equals(left) || "*".equals(right) || Objects.equals(left, right);
    }
  }

  public record EffectiveWindow(Instant effectiveFromUtc, Instant effectiveToUtc) {
    boolean contains(Instant instant) {
      return !instant.isBefore(effectiveFromUtc) && (effectiveToUtc == null || instant.isBefore(effectiveToUtc));
    }

    boolean overlaps(EffectiveWindow other) {
      Instant thisEnd = effectiveToUtc == null ? Instant.MAX : effectiveToUtc;
      Instant otherEnd = other.effectiveToUtc == null ? Instant.MAX : other.effectiveToUtc;
      return effectiveFromUtc.isBefore(otherEnd) && other.effectiveFromUtc.isBefore(thisEnd);
    }
  }

  public record ProfitabilityEvaluationInput(String quoteId, String quoteOptionId, ProfitabilityScope scope,
      BigDecimal priceAfterMarginsAndComp, BigDecimal approvedConcessionsImpact, Optional<BigDecimal> investorPrice,
      Optional<String> configuredCostRef, Optional<BigDecimal> loanAmount, Instant effectiveAtUtc, String correlationId) {
    public ProfitabilityEvaluationInput {
      requireText(quoteId, "quoteId");
      requireText(quoteOptionId, "quoteOptionId");
      scope = Objects.requireNonNull(scope, "scope is required");
      priceAfterMarginsAndComp = Objects.requireNonNull(priceAfterMarginsAndComp, "priceAfterMarginsAndComp is required");
      approvedConcessionsImpact = Objects.requireNonNull(approvedConcessionsImpact, "approvedConcessionsImpact is required");
      investorPrice = investorPrice == null ? Optional.empty() : investorPrice;
      configuredCostRef = configuredCostRef == null ? Optional.empty() : configuredCostRef;
      loanAmount = loanAmount == null ? Optional.empty() : loanAmount;
      effectiveAtUtc = Objects.requireNonNull(effectiveAtUtc, "effectiveAtUtc is required");
      requireText(correlationId, "correlationId");
    }
  }

  public record ProfitabilityDecision(String quoteId, String quoteOptionId, String policyId, String policyVersionId,
      FloorBasis basis, BigDecimal loadedPrice, BigDecimal profitMetric, BigDecimal threshold, String thresholdRef,
      FloorAction action, FloorDecision decision, String decisionCode, String exceptionRouteRef, String replayHash,
      List<WaterfallStep> waterfallSteps) {}

  public record WaterfallStep(String stepType, String quoteOptionId, String policyId, String policyVersionId,
      BigDecimal loadedPrice, BigDecimal profitMetric, BigDecimal threshold, FloorDecision decision, String replayHash) {}

  public record ProfitabilityEvaluation(String quoteId, String quoteOptionId, String policyId, String policyVersionId,
      FloorBasis basis, BigDecimal computedValue, String thresholdRef, FloorAction action, FloorDecision decision,
      String replayHash) {}

  public record ProfitabilityPolicyPublishedEvent(String tenantId, String policyId, String versionId, String scopeHash,
      Instant effectiveFromUtc, String configHash, String actorId, String correlationId, Instant occurredAt) {}

  public record ProfitabilityFloorBreachedEvent(String quoteId, String quoteOptionId, String policyId,
      String policyVersionId, FloorBasis basis, String thresholdRef, FloorAction action, FloorDecision decision,
      String correlationId, Instant occurredAt) {}

  public record AuditRecord(String tenantId, String subjectId, String actorId, String correlationId, String action,
      Instant recordedAt) {
    static AuditRecord completed(String tenantId, String subjectId, String actorId, String correlationId, String action,
        Clock clock) {
      return new AuditRecord(tenantId, subjectId, actorId, correlationId, action, Instant.now(clock));
    }
  }

  public static final class ProfitabilityFloorException extends RuntimeException {
    public ProfitabilityFloorException(String message) {
      super(message);
    }
  }
}
