package com.wcpe.margin;

import com.wcpe.margin.srp.SrpCalculationService;
import com.wcpe.margin.srp.SrpInputs;
import com.wcpe.margin.srp.SrpResult;
import com.wcpe.margin.srp.SrpRule;
import com.wcpe.margin.overlay.OverlayInputs;
import com.wcpe.margin.overlay.OverlayPolicyType;
import com.wcpe.margin.overlay.OverlayRule;
import com.wcpe.margin.overlay.OverlayRuleRepository;
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

public final class CompanyMarginPolicyService {
  public static final String COMPANY_POLICY_TYPE = "COMPANY";
  public static final String CHANNEL_POLICY_TYPE = "CHANNEL";
  public static final String BRANCH_OVERLAY_POLICY_TYPE = "BRANCH_OVERLAY";
  public static final String SRP_POLICY_TYPE = "SRP";
  public static final String OVERLAY_POLICY_TYPE = "SECONDARY_MARKET_OVERLAY";
  public static final String SENSITIVE_MARGIN_PERMISSION = "pricing.margin.view_sensitive";
  public final AtomicInteger marginVisibilityRedactionTotal = new AtomicInteger();
  private final Clock clock;
  private final SrpCalculationService srpCalculationService;
  private final OverlayRuleRepository overlayRuleRepository;
  private final Store store;

  public CompanyMarginPolicyService(Clock clock) {
    this(clock, new SrpCalculationService());
  }

  public CompanyMarginPolicyService(Clock clock, SrpCalculationService srpCalculationService) {
    this(clock, srpCalculationService, OverlayRuleRepository.empty());
  }

  public CompanyMarginPolicyService(Clock clock, SrpCalculationService srpCalculationService,
      OverlayRuleRepository overlayRuleRepository) {
    this(clock, srpCalculationService, overlayRuleRepository, Store.failClosed("CompanyMarginPolicyService"));
  }

  CompanyMarginPolicyService(Clock clock, SrpCalculationService srpCalculationService,
      OverlayRuleRepository overlayRuleRepository, Store store) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.srpCalculationService = Objects.requireNonNull(srpCalculationService, "srpCalculationService is required");
    this.overlayRuleRepository = Objects.requireNonNull(overlayRuleRepository, "overlayRuleRepository is required");
    this.store = Objects.requireNonNull(store, "store is required");
  }

  public CommandReceipt createDraft(CreatePolicyCommand command) {
    requireDurableStoreOrExplicitTestHarness();
    requireCommand(command.tenantId(), command.actorId(), command.idempotencyKey(), command.correlationId());
    String idemKey = idempotencyKey(command.tenantId(), command.idempotencyKey());
    CommandReceipt existing = store.idempotencyReceipts().get(idemKey);
    if (existing != null) {
      if (!existing.requestHash().equals(command.requestHash())) {
        throw new MarginPolicyException("IDEMPOTENCY_CONFLICT");
      }
      return existing;
    }
    validateDraft(command.policyType(), command.name(), command.version());
    if (activePolicyNameExists(command.tenantId(), command.name(), command.policyType())) {
      throw new MarginPolicyException("MARGIN_POLICY_ALREADY_EXISTS");
    }
    String policyId = UUID.randomUUID().toString();
    MarginPolicy policy = new MarginPolicy(
        command.tenantId(),
        policyId,
        command.name(),
        command.policyType(),
        PolicyStatus.DRAFT,
        List.of(command.version()),
        command.actorId(),
        command.actorId(),
        Instant.now(clock),
        Instant.now(clock));
    store.policies().put(new PolicyKey(command.tenantId(), policyId), policy);
    CommandReceipt receipt = new CommandReceipt(policy.policyId(), policy.status(), policy.currentVersion().versionNumber(),
        command.correlationId(), command.requestHash(), List.of(), "audit:" + policy.policyId());
    store.idempotencyReceipts().put(idemKey, receipt);
    store.auditRecords().add(AuditRecord.completed(command.tenantId(), policy.policyId(), command.actorId(), command.correlationId(), "DRAFT_CREATED"));
    return receipt;
  }

  public CommandReceipt createChannelDraft(CreatePolicyCommand command) {
    return createDraft(command.withPolicyType(CHANNEL_POLICY_TYPE));
  }

  public CommandReceipt createBranchOverlayDraft(CreatePolicyCommand command) {
    return createDraft(command.withPolicyType(BRANCH_OVERLAY_POLICY_TYPE));
  }

  public CommandReceipt createSrpDraft(CreatePolicyCommand command) {
    return createDraft(command.withPolicyType(SRP_POLICY_TYPE));
  }

  public CommandReceipt createOverlayDraft(CreatePolicyCommand command) {
    return createDraft(command.withPolicyType(OVERLAY_POLICY_TYPE));
  }

  public SimulationResult simulate(String tenantId, String policyId, ConfigResolver configResolver, BigDecimal priceBeforeMargin) {
    MarginPolicy policy = findById(tenantId, policyId);
    MarginPolicyVersion version = policy.currentVersion();
    List<MarginCalculationStep> steps = new ArrayList<>();
    BigDecimal runningPrice = priceBeforeMargin;
    for (MarginRule rule : version.rules()) {
      MarginCalculationStep step = calculate(rule, configResolver, runningPrice, version.versionId());
      steps.add(step);
      runningPrice = step.priceAfterMargin();
    }
    return new SimulationResult(policyId, version.versionId(), runningPrice, steps);
  }

  public SimulationResult simulateChannel(String tenantId, String policyId, ConfigResolver configResolver,
      BigDecimal priceAfterCompanyMargin, MarginScope quoteScope) {
    MarginPolicy policy = findById(tenantId, policyId);
    if (!CHANNEL_POLICY_TYPE.equals(policy.policyType())) {
      throw new MarginPolicyException("CHANNEL_NOT_CONFIGURED");
    }
    MarginPolicyVersion version = policy.currentVersion();
    MarginRule rule = selectChannelRule(version, quoteScope);
    MarginCalculationStep step = calculate(rule, configResolver, priceAfterCompanyMargin, version.versionId(), "CHANNEL_MARGIN");
    return new SimulationResult(policyId, version.versionId(), step.priceAfterMargin(), List.of(step));
  }

  public SimulationResult simulateBranchOverlay(String tenantId, String policyId, ConfigResolver configResolver,
      BigDecimal priceAfterChannelMargin, BranchOverlayContext context) {
    Objects.requireNonNull(context, "context is required");
    MarginPolicy policy = findById(tenantId, policyId);
    if (!BRANCH_OVERLAY_POLICY_TYPE.equals(policy.policyType())) {
      throw new MarginPolicyException("BRANCH_OVERLAY_NOT_CONFIGURED");
    }
    if (!tenantId.equals(context.tenantId())) {
      throw new MarginPolicyException("TENANT_ACCESS_DENIED");
    }
    if (!context.authorizedBranchIds().contains(context.branchId())) {
      throw new MarginPolicyException("BRANCH_SCOPE_UNAUTHORIZED");
    }
    if (context.hierarchyStale()) {
      throw new MarginPolicyException("BRANCH_HIERARCHY_STALE");
    }
    MarginPolicyVersion version = policy.currentVersion();
    MarginRule rule = selectBranchOverlayRule(version, context);
    MarginCalculationStep step = calculate(rule, configResolver, priceAfterChannelMargin, version.versionId(), "BRANCH_OVERLAY");
    BigDecimal enterpriseLimit = resolveRequired(configResolver, context.enterpriseLimitRef());
    BigDecimal enterpriseLimitPoints = rule.unit() == MarginUnit.BPS ? enterpriseLimit.movePointLeft(2) : enterpriseLimit;
    if (step.marginPoints().abs().compareTo(enterpriseLimitPoints.abs()) > 0) {
      throw new MarginPolicyException("BRANCH_OVERLAY_LIMIT_EXCEEDED");
    }
    return new SimulationResult(policyId, version.versionId(), step.priceAfterMargin(), List.of(step));
  }

  public SimulationResult simulateSrp(String tenantId, String policyId, ConfigResolver configResolver,
      BigDecimal priceAfterChannelMargin, SrpInputs inputs) {
    Objects.requireNonNull(inputs, "inputs are required");
    MarginPolicy policy = findById(tenantId, policyId);
    if (!SRP_POLICY_TYPE.equals(policy.policyType())) {
      throw new MarginPolicyException("SRP_NOT_CONFIGURED");
    }
    MarginPolicyVersion version = policy.currentVersion();
    SrpRule rule = selectSrpRule(version, inputs.scope(), configResolver);
    SrpResult result = srpCalculationService.calculate(rule, inputs);
    BigDecimal priceAfterSrp = priceAfterChannelMargin.subtract(result.srpPoints()).setScale(rule.roundingScale(), RoundingMode.HALF_UP);
    MarginCalculationStep step = new MarginCalculationStep("SRP", version.versionId(), priceAfterChannelMargin,
        priceAfterSrp, result.srpPoints(), result.reasonCode(), replayHash(rule, result, priceAfterChannelMargin, priceAfterSrp, inputs.loanAmount()));
    return new SimulationResult(policyId, version.versionId(), priceAfterSrp, List.of(step));
  }

  public SimulationResult simulateOverlay(String tenantId, String policyId, ConfigResolver configResolver,
      BigDecimal priceBeforeOverlay, OverlayInputs inputs) {
    Objects.requireNonNull(configResolver, "configResolver is required");
    return evaluateProfitabilityOverlays(tenantId, policyId, priceBeforeOverlay, inputs, List.of()).simulationResult();
  }

  public ProfitabilityOverlayEvaluationResult evaluateProfitabilityOverlays(String tenantId, String policyId,
      BigDecimal priceBeforeOverlay, OverlayInputs inputs, List<String> downstreamConsumers) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    requireText(policyId, "policyId");
    Objects.requireNonNull(priceBeforeOverlay, "priceBeforeOverlay is required");
    MarginPolicy policy = findById(tenantId, policyId);
    List<String> consumerRefs = List.copyOf(downstreamConsumers == null ? List.of() : downstreamConsumers);
    List<DependencyError> inputErrors = overlayInputErrors(inputs);
    if (!inputErrors.isEmpty()) {
      SimulationResult simulation = new SimulationResult(policyId, policy.currentVersion().versionId(), priceBeforeOverlay,
          List.of());
      return new ProfitabilityOverlayEvaluationResult("MISSING_DATA", simulation, inputErrors, List.of(), consumerRefs);
    }
    if (!tenantId.equals(inputs.tenantId().toString())) {
      throw new MarginPolicyException("TENANT_ACCESS_DENIED");
    }
    List<OverlayRule> overlays = overlayRuleRepository.findApplicable(inputs).stream()
        .filter(rule -> rule.type().waterfallPosition() == OverlayPolicyType.WaterfallPosition.MARGIN_COMPONENT)
        .toList();
    List<MarginCalculationStep> steps = new ArrayList<>();
    BigDecimal runningPrice = priceBeforeOverlay;
    for (OverlayRule overlay : overlays) {
      MarginCalculationStep step = calculateOverlay(overlay, runningPrice, policy.currentVersion().versionId());
      steps.add(step);
      runningPrice = step.priceAfterMargin();
    }
    SimulationResult simulation = new SimulationResult(policyId, policy.currentVersion().versionId(), runningPrice, steps);
    String status = overlays.isEmpty() ? "NO_APPLICABLE_OVERLAYS" : "OVERLAYS_EVALUATED";
    return new ProfitabilityOverlayEvaluationResult(status, simulation, List.of(), overlayMetadataRefs(overlays), consumerRefs);
  }

  public MarginRuleEvaluationResult evaluateActiveMarginRules(String tenantId, MarginScope quoteScope,
      Instant pricedAtUtc, RuleEvaluationContext context, BigDecimal priceBeforeMargin) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    Objects.requireNonNull(quoteScope, "quoteScope is required");
    Objects.requireNonNull(pricedAtUtc, "pricedAtUtc is required");
    Objects.requireNonNull(context, "context is required");
    Objects.requireNonNull(priceBeforeMargin, "priceBeforeMargin is required");
    if (!tenantId.equals(context.tenantId())) {
      throw new MarginPolicyException("TENANT_ACCESS_DENIED");
    }
    MarginPolicy policy = resolvePublishedRulePolicy(tenantId, quoteScope, pricedAtUtc)
        .orElseThrow(() -> new MarginPolicyException("MARGIN_RULES_NOT_CONFIGURED"));
    MarginPolicyVersion version = policy.currentVersion();
    List<MarginRule> rules = activeMarginRules(version, quoteScope);
    if (rules.isEmpty()) {
      throw new MarginPolicyException("MARGIN_RULES_NOT_CONFIGURED");
    }
    List<DependencyError> dependencyErrors = dependencyErrors(rules, context);
    if (!dependencyErrors.isEmpty()) {
      store.auditRecords().add(AuditRecord.completed(tenantId, policy.policyId(), "system", context.correlationId(),
          "MARGIN_RULE_DEPENDENCY_ERROR"));
      return new MarginRuleEvaluationResult(tenantId, context.quoteId(), policy.policyId(), version.versionId(),
          "DEPENDENCY_ERROR", priceBeforeMargin, priceBeforeMargin, List.of(), dependencyErrors,
          referencedMetadataRefs(rules), "audit:" + policy.policyId());
    }
    List<MarginCalculationStep> steps = new ArrayList<>();
    BigDecimal runningPrice = priceBeforeMargin;
    for (MarginRule rule : rules) {
      MarginCalculationStep step = calculate(rule, context::resolve, runningPrice, version.versionId(), "MARGIN_RULE");
      steps.add(step);
      runningPrice = step.priceAfterMargin();
    }
    store.auditRecords().add(AuditRecord.completed(tenantId, policy.policyId(), "system", context.correlationId(),
        "MARGIN_RULES_EVALUATED"));
    return new MarginRuleEvaluationResult(tenantId, context.quoteId(), policy.policyId(), version.versionId(),
        "MARGIN_RULES_EVALUATED", priceBeforeMargin, runningPrice, steps, List.of(), referencedMetadataRefs(rules),
        "audit:" + policy.policyId());
  }

  public List<MarginRule> activeRules(String tenantId, MarginScope quoteScope, Instant pricedAtUtc) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    Objects.requireNonNull(quoteScope, "quoteScope is required");
    Objects.requireNonNull(pricedAtUtc, "pricedAtUtc is required");
    return store.policies().values().stream()
        .filter(policy -> policy.tenantId().equals(tenantId))
        .filter(policy -> COMPANY_POLICY_TYPE.equals(policy.policyType()))
        .filter(policy -> policy.status() == PolicyStatus.PUBLISHED)
        .filter(policy -> policy.currentVersion().effectiveWindow().contains(pricedAtUtc))
        .filter(policy -> policy.currentVersion().scope().matches(quoteScope))
        .flatMap(policy -> activeMarginRules(policy.currentVersion(), quoteScope).stream())
        .toList();
  }

  public SimulationResult applyVisibility(String viewerPermission, SimulationResult result) {
    Objects.requireNonNull(result, "result is required");
    if (SENSITIVE_MARGIN_PERMISSION.equals(viewerPermission)) {
      return result;
    }
    marginVisibilityRedactionTotal.incrementAndGet();
    return new SimulationResult(result.policyId(), result.versionId(), result.priceAfterMargin(),
        result.steps().stream()
            .map(step -> new MarginCalculationStep(step.stepType(), step.sourceVersionId(), null, null, null,
                step.reasonCode(), step.replayHash()))
            .toList());
  }

  public CommandReceipt submit(String tenantId, String policyId, String actorId, String correlationId) {
    return transition(tenantId, policyId, actorId, correlationId, PolicyStatus.DRAFT, PolicyStatus.PENDING_APPROVAL, "SUBMITTED");
  }

  public CommandReceipt approve(String tenantId, String policyId, String actorId, String correlationId) {
    MarginPolicy policy = findById(tenantId, policyId);
    if (policy.createdBy().equals(actorId)) {
      throw new MarginPolicyException("MARGIN_APPROVAL_SOD_VIOLATION");
    }
    return transition(tenantId, policyId, actorId, correlationId, PolicyStatus.PENDING_APPROVAL, PolicyStatus.APPROVED, "APPROVED");
  }

  public CommandReceipt publish(String tenantId, String policyId, String actorId, String correlationId) {
    MarginPolicy policy = findById(tenantId, policyId);
    if (policy.status() != PolicyStatus.APPROVED) {
      throw new MarginPolicyException("MARGIN_VERSION_STALE");
    }
    rejectPublishedOverlap(policy);
    MarginPolicy published = policy.withStatus(PolicyStatus.PUBLISHED, actorId, Instant.now(clock));
    store.policies().put(new PolicyKey(tenantId, policy.policyId()), published);
    MarginPolicyVersion version = published.currentVersion();
    MarginPolicyPublishedEvent event = new MarginPolicyPublishedEvent(
        tenantId,
        policyId,
        version.versionId(),
        policy.policyType(),
        version.scope().stableHash(),
        version.effectiveWindow().effectiveFromUtc(),
        version.configHash(),
        actorId,
        correlationId);
    store.outbox().add(event);
    String action = BRANCH_OVERLAY_POLICY_TYPE.equals(policy.policyType())
        ? "BRANCH_OVERLAY_POLICY_PUBLISHED"
        : "MARGIN_POLICY_PUBLISHED";
    store.auditRecords().add(AuditRecord.completed(tenantId, policyId, actorId, correlationId, action));
    return new CommandReceipt(policyId, PolicyStatus.PUBLISHED, version.versionNumber(), correlationId, "publish:" + policyId, List.of(event), "audit:" + policyId);
  }

  public CommandReceipt suspend(String tenantId, String policyId, String actorId, String correlationId) {
    return transition(tenantId, policyId, actorId, correlationId, PolicyStatus.PUBLISHED, PolicyStatus.SUSPENDED, "SUSPENDED");
  }

  public Optional<MarginPolicy> resolvePublished(String tenantId, MarginScope scope, Instant pricedAtUtc) {
    requireDurableStoreOrExplicitTestHarness();
    List<MarginPolicy> matches = store.policies().values().stream()
        .filter(policy -> policy.tenantId().equals(tenantId))
        .filter(policy -> policy.status() == PolicyStatus.PUBLISHED)
        .filter(policy -> policy.currentVersion().scope().matches(scope))
        .filter(policy -> policy.currentVersion().effectiveWindow().contains(pricedAtUtc))
        .toList();
    if (matches.size() > 1) {
      throw new MarginPolicyException("MARGIN_CONFIG_UNAVAILABLE");
    }
    return matches.stream().findFirst();
  }

  public List<MarginPolicyPublishedEvent> outboxEvents() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.outbox());
  }

  public List<AuditRecord> auditRecords() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.auditRecords());
  }

  private void requireDurableStoreOrExplicitTestHarness() {
    store.requireAvailable();
  }

  private CommandReceipt transition(String tenantId, String policyId, String actorId, String correlationId,
      PolicyStatus from, PolicyStatus to, String action) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    MarginPolicy policy = findById(tenantId, policyId);
    if (policy.status() != from) {
      throw new MarginPolicyException("MARGIN_VERSION_STALE");
    }
    MarginPolicy changed = policy.withStatus(to, actorId, Instant.now(clock));
    store.policies().put(new PolicyKey(tenantId, changed.policyId()), changed);
    store.auditRecords().add(AuditRecord.completed(tenantId, policyId, actorId, correlationId, action));
    return new CommandReceipt(policyId, to, changed.currentVersion().versionNumber(), correlationId, action + ":" + policyId, List.of(), "audit:" + policyId);
  }

  private MarginCalculationStep calculate(MarginRule rule, ConfigResolver configResolver, BigDecimal priceBeforeMargin, String sourceVersionId) {
    return calculate(rule, configResolver, priceBeforeMargin, sourceVersionId, "COMPANY_MARGIN");
  }

  private MarginCalculationStep calculate(MarginRule rule, ConfigResolver configResolver, BigDecimal priceBeforeMargin,
      String sourceVersionId, String stepType) {
    BigDecimal amount = resolveRequired(configResolver, rule.amountRef());
    BigDecimal min = resolveRequired(configResolver, rule.minRef());
    BigDecimal max = resolveRequired(configResolver, rule.maxRef());
    if (min.compareTo(max) > 0) {
      throw new MarginPolicyException("MARGIN_BOUND_INVALID");
    }
    BigDecimal margin = amount.max(min).min(max);
    BigDecimal points = rule.unit() == MarginUnit.BPS ? margin.movePointLeft(2) : margin;
    BigDecimal priceAfterMargin = priceBeforeMargin.subtract(points).setScale(rule.roundingScale(), RoundingMode.HALF_UP);
    return new MarginCalculationStep(stepType, sourceVersionId, priceBeforeMargin, priceAfterMargin, points,
        rule.reasonCode(), replayHash(rule, priceBeforeMargin, priceAfterMargin));
  }

  private MarginCalculationStep calculateOverlay(OverlayRule overlay, BigDecimal priceBeforeOverlay,
      String sourceVersionId) {
    BigDecimal points = overlay.boundedAdjustmentPoints();
    BigDecimal priceAfterOverlay = switch (overlay.type().waterfallPosition()) {
      case LLPA_ADJUSTMENT -> priceBeforeOverlay.add(points);
      case MARGIN_COMPONENT -> priceBeforeOverlay.subtract(points);
    };
    priceAfterOverlay = priceAfterOverlay.setScale(3, RoundingMode.HALF_UP);
    return new MarginCalculationStep(overlay.type().waterfallPosition().name(), sourceVersionId, priceBeforeOverlay,
        priceAfterOverlay, points, overlay.reasonCode(), replayHash(overlay, priceBeforeOverlay, priceAfterOverlay));
  }

  private MarginRule selectChannelRule(MarginPolicyVersion version, MarginScope quoteScope) {
    Objects.requireNonNull(quoteScope, "quoteScope is required");
    List<MarginRule> matches = version.rules().stream()
        .filter(rule -> rule.scope().matches(quoteScope))
        .sorted(Comparator.comparingInt((MarginRule rule) -> specificity(rule.scope())).reversed()
            .thenComparingInt(MarginRule::priority))
        .toList();
    if (matches.isEmpty()) {
      throw new MarginPolicyException("CHANNEL_NOT_CONFIGURED");
    }
    MarginRule best = matches.get(0);
    long ambiguous = matches.stream()
        .filter(rule -> specificity(rule.scope()) == specificity(best.scope()))
        .filter(rule -> rule.priority() == best.priority())
        .count();
    if (ambiguous > 1) {
      throw new MarginPolicyException("CHANNEL_MARGIN_OVERLAP");
    }
    return best;
  }

  private MarginRule selectBranchOverlayRule(MarginPolicyVersion version, BranchOverlayContext context) {
    MarginScope quoteScope = context.scope().withBranch(context.branchId(), context.regionId());
    List<MarginRule> matches = version.rules().stream()
        .filter(rule -> context.hierarchyVersionId().equals(rule.scope().sourceHierarchyVersionId()))
        .filter(rule -> branchOverlayScopeMatches(rule.scope(), quoteScope, context))
        .sorted(Comparator.comparingInt((MarginRule rule) -> branchSpecificity(rule.scope(), context)).reversed()
            .thenComparing(Comparator.comparingInt((MarginRule rule) -> specificity(rule.scope())).reversed())
            .thenComparingInt(MarginRule::priority))
        .toList();
    if (matches.isEmpty()) {
      throw new MarginPolicyException("BRANCH_OVERLAY_NOT_CONFIGURED");
    }
    MarginRule best = matches.get(0);
    long ambiguous = matches.stream()
        .filter(rule -> branchSpecificity(rule.scope(), context) == branchSpecificity(best.scope(), context))
        .filter(rule -> specificity(rule.scope()) == specificity(best.scope()))
        .filter(rule -> rule.priority() == best.priority())
        .count();
    if (ambiguous > 1) {
      throw new MarginPolicyException("BRANCH_OVERLAY_AMBIGUOUS");
    }
    return best;
  }

  private SrpRule selectSrpRule(MarginPolicyVersion version, MarginScope quoteScope, ConfigResolver configResolver) {
    Objects.requireNonNull(quoteScope, "quoteScope is required");
    List<MarginRule> matches = version.rules().stream()
        .filter(rule -> rule.scope().matches(quoteScope))
        .sorted(Comparator.comparingInt((MarginRule rule) -> specificity(rule.scope())).reversed()
            .thenComparingInt(MarginRule::priority))
        .toList();
    if (matches.isEmpty()) {
      throw new MarginPolicyException("SRP_NOT_CONFIGURED");
    }
    MarginRule best = matches.get(0);
    long ambiguous = matches.stream()
        .filter(rule -> specificity(rule.scope()) == specificity(best.scope()))
        .filter(rule -> rule.priority() == best.priority())
        .count();
    if (ambiguous > 1) {
      throw new MarginPolicyException("SRP_POLICY_OVERLAP");
    }
    return new SrpRule(best.priority(), best.scope().investorGroup(), best.scope().channel(), best.scope().productFamily(),
        resolveRequired(configResolver, best.amountRef()), resolveRequired(configResolver, best.minRef()),
        resolveRequired(configResolver, best.maxRef()), best.roundingScale(), best.reasonCode(), best.scope());
  }

  private static int branchSpecificity(MarginScope scope, BranchOverlayContext context) {
    if (Objects.equals(scope.branchId(), context.branchId())) {
      return 3;
    }
    if (context.branchAncestorIds().contains(scope.branchId())) {
      return 2;
    }
    if (Objects.equals(scope.regionId(), context.regionId())) {
      return 1;
    }
    return 0;
  }

  private static boolean branchOverlayScopeMatches(MarginScope ruleScope, MarginScope quoteScope,
      BranchOverlayContext context) {
    boolean hierarchyMatch = ruleScope.matches(quoteScope) || context.branchAncestorIds().contains(ruleScope.branchId());
    MarginScope comparable = new MarginScope(ruleScope.productFamily(), ruleScope.investorGroup(), ruleScope.channel(),
        ruleScope.state(), ruleScope.occupancy(), ruleScope.purpose(), ruleScope.lockPeriodBucket(),
        quoteScope.branchId(), quoteScope.regionId(), ruleScope.sourceHierarchyVersionId(), ruleScope.inheritanceMode());
    return hierarchyMatch && comparable.matches(quoteScope);
  }

  private static List<MarginRule> activeMarginRules(MarginPolicyVersion version, MarginScope quoteScope) {
    return version.rules().stream()
        .filter(rule -> rule.scope().matches(quoteScope))
        .sorted(Comparator.comparingInt(MarginRule::priority))
        .toList();
  }

  private Optional<MarginPolicy> resolvePublishedRulePolicy(String tenantId, MarginScope scope, Instant pricedAtUtc) {
    List<MarginPolicy> matches = store.policies().values().stream()
        .filter(policy -> policy.tenantId().equals(tenantId))
        .filter(policy -> COMPANY_POLICY_TYPE.equals(policy.policyType()))
        .filter(policy -> policy.status() == PolicyStatus.PUBLISHED)
        .filter(policy -> policy.currentVersion().scope().matches(scope))
        .filter(policy -> policy.currentVersion().effectiveWindow().contains(pricedAtUtc))
        .toList();
    if (matches.size() > 1) {
      throw new MarginPolicyException("MARGIN_CONFIG_UNAVAILABLE");
    }
    return matches.stream().findFirst();
  }

  private static List<DependencyError> dependencyErrors(List<MarginRule> rules, RuleEvaluationContext context) {
    List<DependencyError> errors = new ArrayList<>();
    for (MarginRule rule : rules) {
      for (String ref : List.of(rule.amountRef(), rule.minRef(), rule.maxRef())) {
        if (!context.isApproved(ref)) {
          errors.add(new DependencyError(ref, context.sourceType(ref), "MARGIN_METADATA_NOT_APPROVED"));
        } else if (context.resolve(ref).isEmpty()) {
          errors.add(new DependencyError(ref, context.sourceType(ref), "MARGIN_DEPENDENCY_MISSING"));
        }
      }
    }
    return errors;
  }

  private static List<String> referencedMetadataRefs(List<MarginRule> rules) {
    List<String> refs = new ArrayList<>();
    for (MarginRule rule : rules) {
      for (String ref : List.of(rule.amountRef(), rule.minRef(), rule.maxRef())) {
        if (!refs.contains(ref)) {
          refs.add(ref);
        }
      }
    }
    return refs;
  }

  private static List<DependencyError> overlayInputErrors(OverlayInputs inputs) {
    List<DependencyError> errors = new ArrayList<>();
    if (inputs == null) {
      errors.add(new DependencyError("overlayInputs", "MISSING", "OVERLAY_INPUTS_MISSING"));
      return errors;
    }
    if (inputs.tenantId() == null) {
      errors.add(new DependencyError("tenantId", "MISSING", "OVERLAY_TENANT_MISSING"));
    }
    if (inputs.quoteDate() == null) {
      errors.add(new DependencyError("quoteDate", "MISSING", "OVERLAY_QUOTE_DATE_MISSING"));
    }
    return errors;
  }

  private static List<String> overlayMetadataRefs(List<OverlayRule> overlays) {
    List<String> refs = new ArrayList<>();
    for (OverlayRule overlay : overlays) {
      String ruleBookRef = "overlayRuleBook:" + overlay.ruleBookId();
      String ruleRef = "overlayRule:" + overlay.ruleId();
      if (!refs.contains(ruleBookRef)) {
        refs.add(ruleBookRef);
      }
      if (!refs.contains(ruleRef)) {
        refs.add(ruleRef);
      }
    }
    return refs;
  }

  private BigDecimal resolveRequired(ConfigResolver configResolver, String ref) {
    return configResolver.resolve(ref).orElseThrow(() -> new MarginPolicyException("MARGIN_CONFIG_UNAVAILABLE"));
  }

  private void validateDraft(String policyType, String name, MarginPolicyVersion version) {
    requireText(policyType, "policyType");
    requireText(name, "name");
    Objects.requireNonNull(version, "version is required");
    if (version.rules().isEmpty()) {
      throw new MarginPolicyException("VALIDATION_FAILED");
    }
    for (MarginRule rule : version.rules()) {
      requireText(rule.amountRef(), "amountRef");
      requireText(rule.minRef(), "minRef");
      requireText(rule.maxRef(), "maxRef");
      requireText(rule.reasonCode(), "reasonCode");
      if (CHANNEL_POLICY_TYPE.equals(policyType) && "*".equals(rule.scope().channel())) {
        throw new MarginPolicyException("CHANNEL_MARGIN_SCOPE_TOO_BROAD");
      }
      if (BRANCH_OVERLAY_POLICY_TYPE.equals(policyType)) {
        if ("*".equals(rule.scope().branchId()) && "*".equals(rule.scope().regionId())) {
          throw new MarginPolicyException("BRANCH_OVERLAY_SCOPE_REQUIRED");
        }
        requireText(rule.scope().sourceHierarchyVersionId(), "sourceHierarchyVersionId");
      }
      if (SRP_POLICY_TYPE.equals(policyType) && ("*".equals(rule.scope().investorGroup())
          || "*".equals(rule.scope().channel()) || "*".equals(rule.scope().productFamily()))) {
        throw new MarginPolicyException("SRP_SCOPE_REQUIRED");
      }
    }
  }

  private void rejectPublishedOverlap(MarginPolicy candidate) {
    boolean overlap = store.policies().values().stream()
        .filter(policy -> !policy.policyId().equals(candidate.policyId()))
        .filter(policy -> policy.tenantId().equals(candidate.tenantId()))
        .filter(policy -> policy.policyType().equals(candidate.policyType()))
        .filter(policy -> policy.status() == PolicyStatus.PUBLISHED)
        .anyMatch(policy -> policy.currentVersion().scope().matches(candidate.currentVersion().scope())
            && policy.currentVersion().effectiveWindow().overlaps(candidate.currentVersion().effectiveWindow()));
    if (overlap) {
      if (CHANNEL_POLICY_TYPE.equals(candidate.policyType())) {
        throw new MarginPolicyException("CHANNEL_MARGIN_OVERLAP");
      }
      throw new MarginPolicyException("MARGIN_SCOPE_OVERLAP");
    }
  }

  private boolean activePolicyNameExists(String tenantId, String name, String policyType) {
    return store.policies().values().stream()
        .filter(policy -> policy.tenantId().equals(tenantId))
        .filter(policy -> policy.name().equals(name))
        .filter(policy -> policy.policyType().equals(policyType))
        .anyMatch(policy -> policy.status() != PolicyStatus.PUBLISHED && policy.status() != PolicyStatus.SUSPENDED);
  }

  private static int specificity(MarginScope scope) {
    return specific(scope.productFamily()) + specific(scope.investorGroup()) + specific(scope.channel())
        + specific(scope.state()) + specific(scope.occupancy()) + specific(scope.purpose())
        + specific(scope.lockPeriodBucket()) + specific(scope.branchId()) + specific(scope.regionId());
  }

  private static int specific(String value) {
    return "*".equals(value) ? 0 : 1;
  }

  private MarginPolicy findById(String tenantId, String policyId) {
    requireDurableStoreOrExplicitTestHarness();
    return store.policies().values().stream()
        .filter(policy -> policy.tenantId().equals(tenantId))
        .filter(policy -> policy.policyId().equals(policyId))
        .findFirst()
        .orElseThrow(() -> new MarginPolicyException("NOT_FOUND"));
  }

  private static void requireCommand(String tenantId, String actorId, String idempotencyKey, String correlationId) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(idempotencyKey, "idempotencyKey");
    requireText(correlationId, "correlationId");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new MarginPolicyException(field + " is required");
    }
  }

  private static String idempotencyKey(String tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String replayHash(MarginRule rule, BigDecimal before, BigDecimal after) {
    return Integer.toHexString(Objects.hash(rule.amountRef(), rule.minRef(), rule.maxRef(), before.stripTrailingZeros(), after.stripTrailingZeros()));
  }

  private static String replayHash(SrpRule rule, SrpResult result, BigDecimal before, BigDecimal after, BigDecimal loanAmount) {
    return Integer.toHexString(Objects.hash(rule.reasonCode(), result.srpBps().stripTrailingZeros(),
        loanAmount.stripTrailingZeros(), before.stripTrailingZeros(), after.stripTrailingZeros()));
  }

  private static String replayHash(OverlayRule rule, BigDecimal before, BigDecimal after) {
    return Integer.toHexString(Objects.hash(rule.ruleId(), rule.type(), rule.boundedAdjustmentBps().stripTrailingZeros(),
        before.stripTrailingZeros(), after.stripTrailingZeros()));
  }

  interface Store {
    Map<PolicyKey, MarginPolicy> policies();
    Map<String, CommandReceipt> idempotencyReceipts();
    List<MarginPolicyPublishedEvent> outbox();
    List<AuditRecord> auditRecords();

    default void requireAvailable() {}

    static Store failClosed(String component) {
      return new Store() {
        @Override public void requireAvailable() {
          ProcessLocalStatePolicy.requireDurableStoreOrExplicitTestHarness(false, component);
        }
        @Override public Map<PolicyKey, MarginPolicy> policies() { return unavailable(); }
        @Override public Map<String, CommandReceipt> idempotencyReceipts() { return unavailable(); }
        @Override public List<MarginPolicyPublishedEvent> outbox() { return unavailable(); }
        @Override public List<AuditRecord> auditRecords() { return unavailable(); }
        private <T> T unavailable() {
          requireAvailable();
          throw new AssertionError("unreachable");
        }
      };
    }
  }

  record PolicyKey(String tenantId, String name) {}

  public enum PolicyStatus { DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, SUSPENDED }

  public enum MarginUnit { BPS, PRICE_POINTS }

  public interface ConfigResolver {
    Optional<BigDecimal> resolve(String ref);
  }

  public record CreatePolicyCommand(String tenantId, String requestId, String actorId, String idempotencyKey,
      String correlationId, String name, MarginPolicyVersion version, String requestHash, String policyType) {
    public CreatePolicyCommand(String tenantId, String requestId, String actorId, String idempotencyKey,
        String correlationId, String name, MarginPolicyVersion version, String requestHash) {
      this(tenantId, requestId, actorId, idempotencyKey, correlationId, name, version, requestHash, COMPANY_POLICY_TYPE);
    }

    CreatePolicyCommand withPolicyType(String policyType) {
      return new CreatePolicyCommand(tenantId, requestId, actorId, idempotencyKey, correlationId, name, version, requestHash, policyType);
    }
  }

  public record CommandReceipt(String policyId, PolicyStatus status, int version, String correlationId,
      String requestHash, List<MarginPolicyPublishedEvent> events, String auditRef) {}

  public record MarginPolicy(String tenantId, String policyId, String name, String policyType, PolicyStatus status,
      List<MarginPolicyVersion> versions, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {
    public MarginPolicyVersion currentVersion() {
      return versions.stream().max(Comparator.comparingInt(MarginPolicyVersion::versionNumber)).orElseThrow();
    }

    MarginPolicy withStatus(PolicyStatus status, String actorId, Instant now) {
      return new MarginPolicy(tenantId, policyId, name, policyType, status, versions, createdBy, actorId, createdAt, now);
    }
  }

  public record MarginPolicyVersion(String versionId, int versionNumber, MarginScope scope,
      EffectiveWindow effectiveWindow, List<MarginRule> rules, String configHash) {}

  public record MarginRule(int priority, MarginUnit unit, String amountRef, String minRef, String maxRef,
      int roundingScale, String reasonCode, MarginScope scope) {
    public MarginRule(int priority, MarginUnit unit, String amountRef, String minRef, String maxRef,
        int roundingScale, String reasonCode) {
      this(priority, unit, amountRef, minRef, maxRef, roundingScale, reasonCode, new MarginScope("*", "*", "*", "*", "*", "*", "*"));
    }
  }

  public record MarginScope(String productFamily, String investorGroup, String channel, String state,
      String occupancy, String purpose, String lockPeriodBucket, String branchId, String regionId,
      String sourceHierarchyVersionId, String inheritanceMode) {
    public MarginScope(String productFamily, String investorGroup, String channel, String state,
        String occupancy, String purpose, String lockPeriodBucket) {
      this(productFamily, investorGroup, channel, state, occupancy, purpose, lockPeriodBucket, "*", "*", "*", "INHERIT");
    }

    boolean matches(MarginScope other) {
      return match(productFamily, other.productFamily) && match(investorGroup, other.investorGroup)
          && match(channel, other.channel) && match(state, other.state)
          && match(occupancy, other.occupancy) && match(purpose, other.purpose)
          && match(lockPeriodBucket, other.lockPeriodBucket) && match(branchId, other.branchId)
          && match(regionId, other.regionId);
    }

    MarginScope withBranch(String branchId, String regionId) {
      return new MarginScope(productFamily, investorGroup, channel, state, occupancy, purpose, lockPeriodBucket,
          branchId, regionId, sourceHierarchyVersionId, inheritanceMode);
    }

    String stableHash() {
      return Integer.toHexString(Objects.hash(productFamily, investorGroup, channel, state, occupancy, purpose,
          lockPeriodBucket, branchId, regionId, sourceHierarchyVersionId, inheritanceMode));
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

  public record SimulationResult(String policyId, String versionId, BigDecimal priceAfterMargin,
      List<MarginCalculationStep> steps) {}

  public record RuleEvaluationContext(String tenantId, String quoteId, String correlationId,
      Map<String, BigDecimal> tenantFieldValues, Map<String, BigDecimal> calculationOutputs,
      List<String> approvedMetadataRefs) {
    public RuleEvaluationContext {
      requireText(tenantId, "tenantId");
      requireText(quoteId, "quoteId");
      requireText(correlationId, "correlationId");
      tenantFieldValues = Map.copyOf(Objects.requireNonNull(tenantFieldValues, "tenantFieldValues is required"));
      calculationOutputs = Map.copyOf(Objects.requireNonNull(calculationOutputs, "calculationOutputs is required"));
      approvedMetadataRefs = List.copyOf(Objects.requireNonNull(approvedMetadataRefs, "approvedMetadataRefs is required"));
    }

    Optional<BigDecimal> resolve(String ref) {
      BigDecimal fieldValue = tenantFieldValues.get(ref);
      if (fieldValue != null) {
        return Optional.of(fieldValue);
      }
      return Optional.ofNullable(calculationOutputs.get(ref));
    }

    boolean isApproved(String ref) {
      return approvedMetadataRefs.contains(ref);
    }

    String sourceType(String ref) {
      if (tenantFieldValues.containsKey(ref)) {
        return "TENANT_FIELD";
      }
      if (calculationOutputs.containsKey(ref)) {
        return "CALCULATION_OUTPUT";
      }
      return "MISSING";
    }
  }

  public record MarginRuleEvaluationResult(String tenantId, String quoteId, String policyId, String versionId,
      String status, BigDecimal priceBeforeMargin, BigDecimal priceAfterMargin, List<MarginCalculationStep> steps,
      List<DependencyError> dependencyErrors, List<String> metadataRefs, String auditRef) {
    public MarginRuleEvaluationResult {
      steps = List.copyOf(Objects.requireNonNull(steps, "steps is required"));
      dependencyErrors = List.copyOf(Objects.requireNonNull(dependencyErrors, "dependencyErrors is required"));
      metadataRefs = List.copyOf(Objects.requireNonNull(metadataRefs, "metadataRefs is required"));
    }
  }

  public record ProfitabilityOverlayEvaluationResult(String status, SimulationResult simulationResult,
      List<DependencyError> dependencyErrors, List<String> metadataRefs, List<String> downstreamConsumers) {
    public ProfitabilityOverlayEvaluationResult {
      requireText(status, "status");
      simulationResult = Objects.requireNonNull(simulationResult, "simulationResult is required");
      dependencyErrors = List.copyOf(Objects.requireNonNull(dependencyErrors, "dependencyErrors is required"));
      metadataRefs = List.copyOf(Objects.requireNonNull(metadataRefs, "metadataRefs is required"));
      downstreamConsumers = List.copyOf(Objects.requireNonNull(downstreamConsumers, "downstreamConsumers is required"));
    }
  }

  public record DependencyError(String ref, String sourceType, String errorCode) {}

  public record BranchOverlayContext(String tenantId, String branchId, String regionId, List<String> branchAncestorIds,
      String hierarchyVersionId, String enterpriseLimitRef, List<String> authorizedBranchIds, boolean hierarchyStale,
      MarginScope scope) {
    public BranchOverlayContext {
      requireText(tenantId, "tenantId");
      requireText(branchId, "branchId");
      requireText(hierarchyVersionId, "hierarchyVersionId");
      requireText(enterpriseLimitRef, "enterpriseLimitRef");
      branchAncestorIds = List.copyOf(Objects.requireNonNull(branchAncestorIds, "branchAncestorIds is required"));
      authorizedBranchIds = List.copyOf(Objects.requireNonNull(authorizedBranchIds, "authorizedBranchIds is required"));
      scope = Objects.requireNonNull(scope, "scope is required");
    }
  }

  public record MarginCalculationStep(String stepType, String sourceVersionId, BigDecimal priceBeforeMargin,
      BigDecimal priceAfterMargin, BigDecimal marginPoints, String reasonCode, String replayHash) {}

  public record MarginPolicyPublishedEvent(String tenantId, String policyId, String versionId, String policyType,
      String scopeHash, Instant effectiveFromUtc, String configHash, String actorId, String correlationId) {}

  public record AuditRecord(String tenantId, String policyId, String actorId, String correlationId, String action,
      Instant recordedAt) {
    static AuditRecord completed(String tenantId, String policyId, String actorId, String correlationId, String action) {
      return new AuditRecord(tenantId, policyId, actorId, correlationId, action, Instant.now());
    }
  }

  public static final class MarginPolicyException extends RuntimeException {
    public MarginPolicyException(String message) {
      super(message);
    }
  }
}
