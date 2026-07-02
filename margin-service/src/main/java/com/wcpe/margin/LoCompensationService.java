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

public final class LoCompensationService {
  public static final String LO_PLAN_TYPE = "LO";
  public static final String LO_PAYEE_TYPE = "LO";
  public static final String SENSITIVE_PERMISSION = "pricing.comp.lo.view_sensitive";

  public final AtomicInteger loCompResolveTotal = new AtomicInteger();
  public final AtomicInteger compAssignmentOverlapRejectedTotal = new AtomicInteger();
  public final AtomicInteger compSensitiveViewDeniedTotal = new AtomicInteger();

  private final Clock clock;
  private final Store store;

  public LoCompensationService(Clock clock) {
    this(clock, MarginDurableStores.loCompensationStore());
  }

  LoCompensationService(Clock clock, Store store) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.store = Objects.requireNonNull(store, "store is required");
  }

  public CommandReceipt createDraftPlan(String tenantId, String requestId, String actorId, String idempotencyKey,
      String correlationId, String name, int versionNumber, List<CompensationRule> rules) {
    requireDurableStoreOrExplicitTestHarness();
    requireCommand(tenantId, actorId, idempotencyKey, correlationId);
    requireText(requestId, "requestId");
    requireText(name, "name");
    List<CompensationRule> safeRules = List.copyOf(Objects.requireNonNull(rules, "rules is required"));
    String requestHash = requestHash(requestId, actorId, name, versionNumber, safeRules);
    String idemKey = idempotencyKey(tenantId, idempotencyKey);
    IdempotencyRecord existing = store.idempotencyReceipts().get(idemKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        throw new CompException("IDEMPOTENCY_CONFLICT");
      }
      return existing.receipt();
    }
    validateRules(safeRules);
    String planId = UUID.randomUUID().toString();
    String versionId = UUID.randomUUID().toString();
    Instant now = Instant.now(clock);
    CompensationPlanVersion version = new CompensationPlanVersion(versionId, planId, versionNumber, now, null,
        stableHash(safeRules), CompPlanStatus.DRAFT.name(), null, null, safeRules);
    CompensationPlan plan = new CompensationPlan(tenantId, planId, LO_PLAN_TYPE, name, CompPlanStatus.DRAFT,
        List.of(version), actorId, actorId, now, now);
    store.plans().put(new PlanKey(tenantId, planId), plan);
    CommandReceipt receipt = new CommandReceipt(planId, plan.status(), version.versionNumber(), correlationId, List.of(),
        "audit:" + planId);
    store.idempotencyReceipts().put(idemKey, new IdempotencyRecord(requestHash, receipt));
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId, "LO_COMP_DRAFT_CREATED", clock));
    return receipt;
  }

  public CommandReceipt submitForApproval(String tenantId, String planId, String actorId, String correlationId) {
    return transition(tenantId, planId, actorId, correlationId, CompPlanStatus.DRAFT,
        CompPlanStatus.PENDING_APPROVAL, "LO_COMP_SUBMITTED");
  }

  public CommandReceipt complianceApprove(String tenantId, String planId, String actorId, String correlationId) {
    CompensationPlan plan = findById(tenantId, planId);
    if (plan.createdBy().equals(actorId)) {
      throw new CompException("COMP_APPROVAL_SOD_VIOLATION");
    }
    CommandReceipt receipt = transition(tenantId, planId, actorId, correlationId, CompPlanStatus.PENDING_APPROVAL,
        CompPlanStatus.APPROVED, "LO_COMP_APPROVED");
    CompensationPlan approved = findById(tenantId, planId);
    CompensationPlanVersion current = approved.currentVersion();
    CompensationPlanVersion approvedVersion = current.withApproval(CompPlanStatus.APPROVED.name(), actorId,
        Instant.now(clock));
    store.plans().put(new PlanKey(tenantId, planId), approved.withVersion(approvedVersion, actorId, Instant.now(clock)));
    return receipt;
  }

  public CommandReceipt publish(String tenantId, String planId, String actorId, String correlationId) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    CompensationPlan plan = findById(tenantId, planId);
    if (plan.status() != CompPlanStatus.APPROVED) {
      throw new CompException("COMP_VERSION_STALE");
    }
    rejectPublishedOverlap(plan);
    Instant now = Instant.now(clock);
    CompensationPlanVersion version = plan.currentVersion().withApproval(CompPlanStatus.PUBLISHED.name(), actorId, now);
    CompensationPlan published = plan.withVersion(version, actorId, now).withStatus(CompPlanStatus.PUBLISHED, actorId, now);
    store.plans().put(new PlanKey(tenantId, planId), published);
    CompPlanPublishedEvent event = new CompPlanPublishedEvent(tenantId, planId, version.versionId(),
        version.configHash(), actorId, correlationId, now);
    store.outbox().add(event);
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId, "LO_COMP_PUBLISHED", clock));
    return new CommandReceipt(planId, CompPlanStatus.PUBLISHED, version.versionNumber(), correlationId, List.of(event),
        "audit:" + planId);
  }

  public CommandReceipt suspend(String tenantId, String planId, String actorId, String correlationId) {
    return transition(tenantId, planId, actorId, correlationId, CompPlanStatus.PUBLISHED, CompPlanStatus.SUSPENDED,
        "LO_COMP_SUSPENDED");
  }

  public CommandReceipt createAssignment(String tenantId, String planId, String actorId, String idempotencyKey,
      String correlationId, int versionNumber, CompensationAssignment assignment) {
    requireCommand(tenantId, actorId, idempotencyKey, correlationId);
    Objects.requireNonNull(assignment, "assignment is required");
    CompensationPlan plan = findById(tenantId, planId);
    CompensationPlanVersion version = versionByNumber(plan, versionNumber);
    if (plan.status() != CompPlanStatus.PUBLISHED || !CompPlanStatus.PUBLISHED.name().equals(version.approvalStatus())) {
      throw new CompException("COMP_VERSION_NOT_PUBLISHED");
    }
    String requestHash = requestHash(planId, versionNumber, assignment);
    String idemKey = idempotencyKey(tenantId, idempotencyKey);
    IdempotencyRecord existing = store.idempotencyReceipts().get(idemKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        throw new CompException("IDEMPOTENCY_CONFLICT");
      }
      return existing.receipt();
    }
    CompensationAssignment changed = assignment.normalized(version.versionId());
    validateAssignment(changed);
    if (assignmentOverlaps(tenantId, changed)) {
      compAssignmentOverlapRejectedTotal.incrementAndGet();
      store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId, "LO_COMP_ASSIGNMENT_OVERLAP_REJECTED", clock));
      throw new CompException("COMP_ASSIGNMENT_OVERLAP");
    }
    store.assignments().add(changed);
    CompAssignmentChangedEvent event = new CompAssignmentChangedEvent(tenantId, changed.assignmentId(),
        version.versionId(), changed.payeeType(), changed.payeeId(), scopeHash(changed), actorId, correlationId,
        Instant.now(clock));
    store.outbox().add(event);
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId, "LO_COMP_ASSIGNMENT_CREATED", clock));
    CommandReceipt receipt = new CommandReceipt(planId, plan.status(), version.versionNumber(), correlationId,
        List.of(event), "audit:" + planId);
    store.idempotencyReceipts().put(idemKey, new IdempotencyRecord(requestHash, receipt));
    return receipt;
  }

  public CompCalculationResult simulateQuote(String tenantId, String planId, int versionNumber, CompBasis basis,
      String amountRef, BigDecimal priceBeforeComp, Map<String, Object> priceContext, ConfigResolver configResolver) {
    requireText(tenantId, "tenantId");
    Objects.requireNonNull(basis, "basis is required");
    Objects.requireNonNull(priceBeforeComp, "priceBeforeComp is required");
    Objects.requireNonNull(configResolver, "configResolver is required");
    loCompResolveTotal.incrementAndGet();
    CompensationPlan plan = findById(tenantId, planId);
    CompensationPlanVersion version = versionByNumber(plan, versionNumber);
    CompensationRule rule = selectRule(version, basis, amountRef);
    String resolvedAmountRef = hasText(amountRef) ? amountRef : rule.amountRef();
    BigDecimal rawAmount = resolveRuleAmount(configResolver, resolvedAmountRef, rule.amountExpression(), tenantId,
        planId, "LO_COMP_AMOUNT_MISSING");
    BigDecimal floorAmount = resolveOptional(configResolver, rule.floorRef(), tenantId, planId, "LO_COMP_FLOOR_MISSING");
    BigDecimal capAmount = resolveOptional(configResolver, rule.capRef(), tenantId, planId, "LO_COMP_CAP_MISSING");
    BigDecimal minAmount = resolveOptional(configResolver, rule.minRef(), tenantId, planId, "LO_COMP_MIN_MISSING");
    BigDecimal maxAmount = resolveOptional(configResolver, rule.maxRef(), tenantId, planId, "LO_COMP_MAX_MISSING");
    if (capAmount != null && floorAmount != null && capAmount.compareTo(floorAmount) < 0) {
      throw new CompException("COMP_CAP_FLOOR_INVALID");
    }
    BigDecimal bounded = rawAmount;
    if (floorAmount != null) {
      bounded = bounded.max(floorAmount);
    }
    if (minAmount != null) {
      bounded = bounded.max(minAmount);
    }
    if (capAmount != null) {
      bounded = bounded.min(capAmount);
    }
    if (maxAmount != null) {
      bounded = bounded.min(maxAmount);
    }
    BigDecimal pricePoints = toPricePoints(basis, bounded, safeMap(priceContext), tenantId, planId);
    BigDecimal priceImpactBps = pricePoints.movePointRight(2);
    BigDecimal priceAfterComp = priceBeforeComp.subtract(pricePoints).setScale(priceBeforeComp.scale(), RoundingMode.HALF_UP);
    boolean capFloorApplied = rawAmount.compareTo(bounded) != 0;
    CompLedgerStep step = new CompLedgerStep("LO_COMPENSATION", priceBeforeComp, bounded, basis, capFloorApplied,
        capAmount, floorAmount, priceAfterComp, rule.reasonCode(), replayHash(rule, priceBeforeComp, bounded, priceAfterComp));
    CompCalculationResult result = new CompCalculationResult(planId, version.versionId(), basis, rawAmount, floorAmount,
        capAmount, bounded, priceImpactBps, priceAfterComp, List.of(step), null);
    store.resultVisibility().put(step.replayHash(), rule.visibilityClassification());
    return result;
  }

  public Optional<CompensationAssignment> resolveActiveAssignment(String tenantId, String payeeId, String branchId,
      String channel, String productFamily, Instant effectiveAt) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    requireText(payeeId, "payeeId");
    Objects.requireNonNull(effectiveAt, "effectiveAt is required");
    List<CompensationAssignment> matches = store.assignments().stream()
        .filter(assignment -> LO_PAYEE_TYPE.equals(assignment.payeeType()))
        .filter(assignment -> assignment.payeeId().equals(payeeId))
        .filter(assignment -> contains(assignment.effectiveFrom(), assignment.effectiveTo(), effectiveAt))
        .filter(assignment -> scopeMatches(assignment.branchScope(), branchId))
        .filter(assignment -> scopeMatches(assignment.channelScope(), channel))
        .filter(assignment -> scopeMatches(assignment.productScope(), productFamily))
        .filter(assignment -> assignmentTenantMatches(tenantId, assignment))
        .toList();
    if (matches.size() > 1) {
      throw new CompException("COMP_ASSIGNMENT_AMBIGUOUS");
    }
    return matches.stream().findFirst();
  }

  public CompCalculationResult applyVisibility(String viewerPermission, CompCalculationResult compResult) {
    Objects.requireNonNull(compResult, "compResult is required");
    if (SENSITIVE_PERMISSION.equals(viewerPermission)) {
      return compResult;
    }
    String classification = compResult.steps().stream()
        .map(step -> store.resultVisibility().get(step.replayHash()))
        .filter(Objects::nonNull)
        .findFirst()
        .orElseGet(() -> compResult.steps().stream()
            .map(CompLedgerStep::reasonCode)
            .filter(LoCompensationService::isVisibilityClassification)
            .findFirst()
            .orElse("SENSITIVE"));
    if ("PUBLIC".equals(classification)) {
      return compResult;
    }
    compSensitiveViewDeniedTotal.incrementAndGet();
    if ("AGGREGATE".equals(classification)) {
      return new CompCalculationResult(compResult.planId(), compResult.versionId(), compResult.basis(), null, null, null,
          compResult.boundedAmount(), compResult.priceImpactBps(), compResult.priceAfterComp(), List.of(), compResult.payeeId());
    }
    return new CompCalculationResult(compResult.planId(), compResult.versionId(), compResult.basis(), null, null, null,
        null, null, null, List.of(), compResult.payeeId());
  }

  public Optional<CompensationPlan> findPlan(String tenantId, String planId) {
    requireDurableStoreOrExplicitTestHarness();
    return Optional.ofNullable(store.plans().get(new PlanKey(tenantId, planId)));
  }

  public List<CompensationAssignment> assignments() {
    requireDurableStoreOrExplicitTestHarness();
    return List.copyOf(store.assignments());
  }

  public List<Object> outboxEvents() {
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

  private CommandReceipt transition(String tenantId, String planId, String actorId, String correlationId,
      CompPlanStatus from, CompPlanStatus to, String action) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    CompensationPlan plan = findById(tenantId, planId);
    if (plan.status() != from) {
      throw new CompException("COMP_VERSION_STALE");
    }
    CompensationPlan changed = plan.withStatus(to, actorId, Instant.now(clock));
    store.plans().put(new PlanKey(tenantId, planId), changed);
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId, action, clock));
    return new CommandReceipt(planId, to, changed.currentVersion().versionNumber(), correlationId, List.of(),
        "audit:" + planId);
  }

  private CompensationRule selectRule(CompensationPlanVersion version, CompBasis basis, String amountRef) {
    return version.rules().stream()
        .filter(rule -> rule.basis() == basis)
        .filter(rule -> !hasText(amountRef) || amountRef.equals(rule.amountRef()))
        .sorted(Comparator.comparingInt(CompensationRule::sortOrder))
        .findFirst()
        .orElseThrow(() -> new CompException("POLICY_NOT_SATISFIED"));
  }

  private BigDecimal resolveRuleAmount(ConfigResolver configResolver, String amountRef, Map<String, Object> expression,
      String tenantId, String planId, String auditAction) {
    if (hasText(amountRef)) {
      return resolveRequired(configResolver, amountRef, tenantId, planId, auditAction);
    }
    Object fixedValue = expression.get("value");
    if (fixedValue instanceof BigDecimal decimal) {
      return decimal;
    }
    if (fixedValue instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    Object valueRef = expression.get("valueRef");
    if (valueRef instanceof String ref && hasText(ref)) {
      return resolveRequired(configResolver, ref, tenantId, planId, auditAction);
    }
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, "system", "missing-config", auditAction, clock));
    throw new CompException("POLICY_NOT_SATISFIED");
  }

  private BigDecimal resolveRequired(ConfigResolver configResolver, String ref, String tenantId, String planId,
      String auditAction) {
    if (ref.startsWith("prohibited.") && !configResolver.allowsProhibitedTerm(ref)) {
      store.auditRecords().add(AuditRecord.completed(tenantId, planId, "system", "prohibited-term", auditAction, clock));
      throw new CompException("COMP_BASIS_INVALID");
    }
    Optional<BigDecimal> value = configResolver.resolve(ref);
    if (value.isEmpty()) {
      store.auditRecords().add(AuditRecord.completed(tenantId, planId, "system", "missing-config", auditAction, clock));
      if (ref.startsWith("prohibited.")) {
        throw new CompException("COMP_BASIS_INVALID");
      }
      throw new CompException("POLICY_NOT_SATISFIED");
    }
    return value.get();
  }

  private BigDecimal resolveOptional(ConfigResolver configResolver, String ref, String tenantId, String planId,
      String auditAction) {
    if (!hasText(ref)) {
      return null;
    }
    return resolveRequired(configResolver, ref, tenantId, planId, auditAction);
  }

  private BigDecimal toPricePoints(CompBasis basis, BigDecimal amount, Map<String, Object> priceContext,
      String tenantId, String planId) {
    if (basis == CompBasis.PRICE_POINTS) {
      return amount;
    }
    Object loanAmountValue = priceContext.get("loanAmount");
    BigDecimal loanAmount = numericContextValue(loanAmountValue);
    if (loanAmount == null || loanAmount.compareTo(BigDecimal.ZERO) <= 0) {
      store.auditRecords().add(AuditRecord.completed(tenantId, planId, "system", "missing-config", "LO_COMP_LOAN_AMOUNT_MISSING", clock));
      throw new CompException("POLICY_NOT_SATISFIED");
    }
    return amount.divide(loanAmount, 10, RoundingMode.HALF_UP).movePointRight(2);
  }

  private static BigDecimal numericContextValue(Object value) {
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    if (value instanceof String text && hasText(text)) {
      return new BigDecimal(text);
    }
    return null;
  }

  private void validateRules(List<CompensationRule> rules) {
    if (rules.isEmpty()) {
      throw new CompException("VALIDATION_FAILED");
    }
    for (CompensationRule rule : rules) {
      Objects.requireNonNull(rule.basis(), "basis is required");
      if (!hasText(rule.amountRef()) && rule.amountExpression().isEmpty()) {
        throw new CompException("VALIDATION_FAILED");
      }
      requireText(rule.reasonCode(), "reasonCode");
      requireText(rule.visibilityClassification(), "visibilityClassification");
      if (hasText(rule.capRef()) && hasText(rule.floorRef()) && rule.capRef().equals(rule.floorRef())) {
        continue;
      }
    }
  }

  private void validateAssignment(CompensationAssignment assignment) {
    requireText(assignment.assignmentId(), "assignmentId");
    requireText(assignment.planVersionId(), "planVersionId");
    requireText(assignment.payeeType(), "payeeType");
    requireText(assignment.payeeId(), "payeeId");
    if (!LO_PAYEE_TYPE.equals(assignment.payeeType())) {
      throw new CompException("COMP_PAYEE_TYPE_INVALID");
    }
    Objects.requireNonNull(assignment.effectiveFrom(), "effectiveFrom is required");
  }

  private boolean assignmentOverlaps(String tenantId, CompensationAssignment candidate) {
    return store.assignments().stream()
        .filter(assignment -> assignmentTenantMatches(tenantId, assignment))
        .filter(assignment -> LO_PAYEE_TYPE.equals(assignment.payeeType()))
        .filter(assignment -> assignment.payeeId().equals(candidate.payeeId()))
        .filter(assignment -> scopesEqual(assignment, candidate))
        .anyMatch(assignment -> overlaps(assignment.effectiveFrom(), assignment.effectiveTo(), candidate.effectiveFrom(),
            candidate.effectiveTo()));
  }

  private boolean assignmentTenantMatches(String tenantId, CompensationAssignment assignment) {
    return store.plans().values().stream()
        .filter(plan -> plan.tenantId().equals(tenantId))
        .flatMap(plan -> plan.versions().stream())
        .anyMatch(version -> version.versionId().equals(assignment.planVersionId()));
  }

  private void rejectPublishedOverlap(CompensationPlan candidate) {
    CompensationPlanVersion candidateVersion = candidate.currentVersion();
    boolean overlap = store.plans().values().stream()
        .filter(plan -> !plan.planId().equals(candidate.planId()))
        .filter(plan -> plan.tenantId().equals(candidate.tenantId()))
        .filter(plan -> LO_PLAN_TYPE.equals(plan.planType()))
        .filter(plan -> plan.status() == CompPlanStatus.PUBLISHED)
        .map(CompensationPlan::currentVersion)
        .anyMatch(version -> overlaps(version.effectiveFrom(), version.effectiveTo(), candidateVersion.effectiveFrom(),
            candidateVersion.effectiveTo()));
    if (overlap) {
      throw new CompException("COMP_PLAN_EFFECTIVE_WINDOW_OVERLAP");
    }
  }

  private CompensationPlan findById(String tenantId, String planId) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(planId, "planId");
    return Optional.ofNullable(store.plans().get(new PlanKey(tenantId, planId))).orElseThrow(() -> new CompException("NOT_FOUND"));
  }

  private static CompensationPlanVersion versionByNumber(CompensationPlan plan, int versionNumber) {
    return plan.versions().stream()
        .filter(version -> version.versionNumber() == versionNumber)
        .findFirst()
        .orElseThrow(() -> new CompException("NOT_FOUND"));
  }

  private static boolean scopesEqual(CompensationAssignment left, CompensationAssignment right) {
    return left.branchScope().equals(right.branchScope())
        && left.channelScope().equals(right.channelScope())
        && left.productScope().equals(right.productScope());
  }

  private static boolean scopeMatches(Map<String, Object> scope, String value) {
    if (!hasText(value) || scope.isEmpty()) {
      return true;
    }
    return scope.values().stream().anyMatch(scopeValue -> "*".equals(scopeValue) || Objects.equals(String.valueOf(scopeValue), value));
  }

  private static boolean contains(Instant from, Instant to, Instant instant) {
    return !instant.isBefore(from) && (to == null || instant.isBefore(to));
  }

  private static boolean overlaps(Instant leftFrom, Instant leftTo, Instant rightFrom, Instant rightTo) {
    Instant leftEnd = leftTo == null ? Instant.MAX : leftTo;
    Instant rightEnd = rightTo == null ? Instant.MAX : rightTo;
    return leftFrom.isBefore(rightEnd) && rightFrom.isBefore(leftEnd);
  }

  private static void requireCommand(String tenantId, String actorId, String idempotencyKey, String correlationId) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(idempotencyKey, "idempotencyKey");
    requireText(correlationId, "correlationId");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new CompException(field + " is required");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static boolean isVisibilityClassification(String value) {
    return "PUBLIC".equals(value) || "AGGREGATE".equals(value) || "SENSITIVE".equals(value);
  }

  private static Map<String, Object> safeMap(Map<String, Object> map) {
    return map == null ? Map.of() : Map.copyOf(map);
  }

  private static String idempotencyKey(String tenantId, String idempotencyKey) {
    return tenantId + ":" + idempotencyKey;
  }

  private static String requestHash(Object... values) {
    return Integer.toHexString(Objects.hash(values));
  }

  private static String stableHash(Object value) {
    return Integer.toHexString(Objects.hash(value));
  }

  private static String scopeHash(CompensationAssignment assignment) {
    return Integer.toHexString(Objects.hash(assignment.branchScope(), assignment.channelScope(), assignment.productScope()));
  }

  private static String replayHash(CompensationRule rule, BigDecimal before, BigDecimal amount, BigDecimal after) {
    return Integer.toHexString(Objects.hash(rule.ruleId(), rule.amountRef(), rule.capRef(), rule.floorRef(),
        before.stripTrailingZeros(), amount.stripTrailingZeros(), after.stripTrailingZeros()));
  }

  interface Store {
    Map<PlanKey, CompensationPlan> plans();
    Map<String, IdempotencyRecord> idempotencyReceipts();
    List<CompensationAssignment> assignments();
    List<Object> outbox();
    List<AuditRecord> auditRecords();
    Map<String, String> resultVisibility();

    default void requireAvailable() {}

    static Store failClosed(String component) {
      return new Store() {
        @Override public void requireAvailable() {
          ProcessLocalStatePolicy.requireDurableStoreOrExplicitTestHarness(false, component);
        }
        @Override public Map<PlanKey, CompensationPlan> plans() { return unavailable(); }
        @Override public Map<String, IdempotencyRecord> idempotencyReceipts() { return unavailable(); }
        @Override public List<CompensationAssignment> assignments() { return unavailable(); }
        @Override public List<Object> outbox() { return unavailable(); }
        @Override public List<AuditRecord> auditRecords() { return unavailable(); }
        @Override public Map<String, String> resultVisibility() { return unavailable(); }
        private <T> T unavailable() {
          requireAvailable();
          throw new AssertionError("unreachable");
        }
      };
    }
  }

  record PlanKey(String tenantId, String planId) {}

  record IdempotencyRecord(String requestHash, CommandReceipt receipt) {}

  public enum CompBasis { LOAN_AMOUNT, PRICE_POINTS, FIXED_DOLLARS }

  public enum CompPlanStatus { DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, SUSPENDED }

  public interface ConfigResolver {
    Optional<BigDecimal> resolve(String ref);

    default boolean allowsProhibitedTerm(String ref) {
      return false;
    }
  }

  public record CompensationPlan(String tenantId, String planId, String planType, String name, CompPlanStatus status,
      List<CompensationPlanVersion> versions, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {
    public CompensationPlan {
      versions = List.copyOf(Objects.requireNonNull(versions, "versions is required"));
    }

    public CompensationPlanVersion currentVersion() {
      return versions.stream().max(Comparator.comparingInt(CompensationPlanVersion::versionNumber)).orElseThrow();
    }

    CompensationPlan withStatus(CompPlanStatus status, String actorId, Instant now) {
      return new CompensationPlan(tenantId, planId, planType, name, status, versions, createdBy, actorId, createdAt, now);
    }

    CompensationPlan withVersion(CompensationPlanVersion changed, String actorId, Instant now) {
      List<CompensationPlanVersion> changedVersions = versions.stream()
          .map(version -> version.versionId().equals(changed.versionId()) ? changed : version)
          .toList();
      return new CompensationPlan(tenantId, planId, planType, name, status, changedVersions, createdBy, actorId,
          createdAt, now);
    }
  }

  public record CompensationPlanVersion(String versionId, String planId, int versionNumber, Instant effectiveFrom,
      Instant effectiveTo, String configHash, String approvalStatus, String approvedBy, Instant approvedAt,
      List<CompensationRule> rules) {
    public CompensationPlanVersion {
      rules = List.copyOf(Objects.requireNonNull(rules, "rules is required"));
    }

    CompensationPlanVersion withApproval(String approvalStatus, String approvedBy, Instant approvedAt) {
      return new CompensationPlanVersion(versionId, planId, versionNumber, effectiveFrom, effectiveTo, configHash,
          approvalStatus, approvedBy, approvedAt, rules);
    }
  }

  public record CompensationRule(String ruleId, String versionId, CompBasis basis, String amountRef,
      Map<String, Object> amountExpression, String capRef, String floorRef, String minRef, String maxRef,
      String reasonCode, String visibilityClassification, int sortOrder) {
    public CompensationRule {
      amountExpression = amountExpression == null ? Map.of() : Map.copyOf(amountExpression);
    }
  }

  public record CompensationAssignment(String assignmentId, String planVersionId, String payeeType, String payeeId,
      Map<String, Object> branchScope, Map<String, Object> channelScope, Map<String, Object> productScope,
      Instant effectiveFrom, Instant effectiveTo) {
    public CompensationAssignment {
      assignmentId = hasText(assignmentId) ? assignmentId : UUID.randomUUID().toString();
      payeeType = hasText(payeeType) ? payeeType : LO_PAYEE_TYPE;
      branchScope = branchScope == null ? Map.of() : Map.copyOf(branchScope);
      channelScope = channelScope == null ? Map.of() : Map.copyOf(channelScope);
      productScope = productScope == null ? Map.of() : Map.copyOf(productScope);
    }

    CompensationAssignment normalized(String versionId) {
      return new CompensationAssignment(assignmentId, versionId, payeeType, payeeId, branchScope, channelScope,
          productScope, effectiveFrom, effectiveTo);
    }
  }

  public record CompVisibilityPolicy(String classification, String redactedLabel, String requiredPermission) {}

  public record CommandReceipt(String planId, CompPlanStatus status, int version, String correlationId,
      List<Object> events, String auditRef) {
    public CommandReceipt {
      events = List.copyOf(Objects.requireNonNull(events, "events is required"));
    }
  }

  public record CompCalculationResult(String planId, String versionId, CompBasis basis, BigDecimal rawAmount,
      BigDecimal floorAmount, BigDecimal capAmount, BigDecimal boundedAmount, BigDecimal priceImpactBps,
      BigDecimal priceAfterComp, List<CompLedgerStep> steps, String payeeId) {
    public CompCalculationResult {
      steps = steps == null ? List.of() : List.copyOf(steps);
    }
  }

  public record CompLedgerStep(String stepType, BigDecimal inputPrice, BigDecimal amount, CompBasis basis,
      boolean capFloorApplied, BigDecimal capValue, BigDecimal floorValue, BigDecimal outputPrice, String reasonCode,
      String replayHash) {}

  public record CompPlanPublishedEvent(String tenantId, String planId, String versionId, String configHash,
      String actorId, String correlationId, Instant occurredAt) {}

  public record CompAssignmentChangedEvent(String tenantId, String assignmentId, String planVersionId, String payeeType,
      String payeeId, String scopeHash, String actorId, String correlationId, Instant occurredAt) {}

  public record AuditRecord(String tenantId, String planId, String actorId, String correlationId, String action,
      Instant recordedAt) {
    static AuditRecord completed(String tenantId, String planId, String actorId, String correlationId, String action,
        Clock clock) {
      return new AuditRecord(tenantId, planId, actorId, correlationId, action, Instant.now(clock));
    }
  }

  public static final class CompException extends RuntimeException {
    public CompException(String message) {
      super(message);
    }
  }
}
