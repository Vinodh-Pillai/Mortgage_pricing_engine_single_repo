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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class BrokerCompensationService {
  public static final String BROKER_PLAN_TYPE = "BROKER";
  public static final String BROKER_PAYEE_TYPE = "BROKER";
  public static final String SENSITIVE_PERMISSION = "pricing.comp.broker.view_sensitive";

  public final AtomicInteger brokerCompResolveTotal = new AtomicInteger();
  public final AtomicInteger brokerCompVisibilityRedactionTotal = new AtomicInteger();
  public final AtomicInteger brokerCompFailClosedTotal = new AtomicInteger();
  public final AtomicInteger brokerAssignmentOverlapRejectedTotal = new AtomicInteger();

  private final Clock clock;
  private final Store store;

  public BrokerCompensationService(Clock clock) {
    this(clock, MarginDurableStores.brokerCompensationStore());
  }

  BrokerCompensationService(Clock clock, Store store) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.store = Objects.requireNonNull(store, "store is required");
  }

  public CommandReceipt createDraftPlan(String tenantId, String requestId, String actorId, String idempotencyKey,
      String correlationId, String name, int versionNumber, List<BrokerCompensationRule> rules) {
    requireDurableStoreOrExplicitTestHarness();
    requireCommand(tenantId, actorId, idempotencyKey, correlationId);
    requireText(requestId, "requestId");
    requireText(name, "name");
    List<BrokerCompensationRule> safeRules = List.copyOf(Objects.requireNonNull(rules, "rules is required"));
    String requestHash = requestHash(requestId, actorId, name, versionNumber, safeRules);
    String idemKey = idempotencyKey(tenantId, idempotencyKey);
    IdempotencyRecord existing = store.idempotencyReceipts().get(idemKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        throw new BrokerCompException("IDEMPOTENCY_CONFLICT");
      }
      return existing.receipt();
    }
    validateRules(safeRules);
    String planId = UUID.randomUUID().toString();
    String versionId = UUID.randomUUID().toString();
    Instant now = Instant.now(clock);
    BrokerCompensationPlanVersion version = new BrokerCompensationPlanVersion(versionId, planId, versionNumber, now,
        null, stableHash(safeRules), PlanStatus.DRAFT.name(), null, null, safeRules);
    BrokerCompensationPlan plan = new BrokerCompensationPlan(tenantId, planId, BROKER_PLAN_TYPE, name,
        PlanStatus.DRAFT, List.of(version), actorId, actorId, now, now);
    store.plans().put(new PlanKey(tenantId, planId), plan);
    CommandReceipt receipt = new CommandReceipt(planId, plan.status(), version.versionNumber(), correlationId, List.of(),
        "audit:" + planId);
    store.idempotencyReceipts().put(idemKey, new IdempotencyRecord(requestHash, receipt));
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId,
        "BROKER_COMP_DRAFT_CREATED", clock));
    return receipt;
  }

  public CommandReceipt submitForApproval(String tenantId, String planId, String actorId, String correlationId) {
    return transition(tenantId, planId, actorId, correlationId, PlanStatus.DRAFT, PlanStatus.PENDING_APPROVAL,
        "BROKER_COMP_SUBMITTED");
  }

  public CommandReceipt complianceApprove(String tenantId, String planId, String actorId, String correlationId) {
    BrokerCompensationPlan plan = findById(tenantId, planId);
    if (plan.createdBy().equals(actorId)) {
      throw new BrokerCompException("BROKER_COMP_APPROVAL_SOD_VIOLATION");
    }
    CommandReceipt receipt = transition(tenantId, planId, actorId, correlationId, PlanStatus.PENDING_APPROVAL,
        PlanStatus.APPROVED, "BROKER_COMP_APPROVED");
    BrokerCompensationPlan approved = findById(tenantId, planId);
    BrokerCompensationPlanVersion current = approved.currentVersion();
    BrokerCompensationPlanVersion approvedVersion = current.withApproval(PlanStatus.APPROVED.name(), actorId,
        Instant.now(clock));
    store.plans().put(new PlanKey(tenantId, planId), approved.withVersion(approvedVersion, actorId, Instant.now(clock)));
    return receipt;
  }

  public CommandReceipt publish(String tenantId, String planId, String actorId, String correlationId) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    BrokerCompensationPlan plan = findById(tenantId, planId);
    if (plan.status() != PlanStatus.APPROVED) {
      throw new BrokerCompException("VERSION_CONFLICT");
    }
    rejectPublishedOverlap(plan);
    Instant now = Instant.now(clock);
    BrokerCompensationPlanVersion version = plan.currentVersion().withApproval(PlanStatus.PUBLISHED.name(), actorId,
        now);
    BrokerCompensationPlan published = plan.withVersion(version, actorId, now).withStatus(PlanStatus.PUBLISHED,
        actorId, now);
    store.plans().put(new PlanKey(tenantId, planId), published);
    BrokerCompensationPlanPublishedEvent event = new BrokerCompensationPlanPublishedEvent(tenantId, planId,
        version.versionId(), version.configHash(), actorId, correlationId, now);
    store.outbox().add(event);
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId,
        "BROKER_COMPENSATION_PLAN_PUBLISHED", clock));
    return new CommandReceipt(planId, PlanStatus.PUBLISHED, version.versionNumber(), correlationId, List.of(event),
        "audit:" + planId);
  }

  public CommandReceipt createAssignment(String tenantId, String planId, String actorId, String idempotencyKey,
      String correlationId, int versionNumber, BrokerCompensationAssignment assignment) {
    requireCommand(tenantId, actorId, idempotencyKey, correlationId);
    Objects.requireNonNull(assignment, "assignment is required");
    BrokerCompensationPlan plan = findById(tenantId, planId);
    BrokerCompensationPlanVersion version = versionByNumber(plan, versionNumber);
    if (plan.status() != PlanStatus.PUBLISHED || !PlanStatus.PUBLISHED.name().equals(version.approvalStatus())) {
      throw new BrokerCompException("VERSION_CONFLICT");
    }
    String requestHash = requestHash(planId, versionNumber, assignment);
    String idemKey = idempotencyKey(tenantId, idempotencyKey);
    IdempotencyRecord existing = store.idempotencyReceipts().get(idemKey);
    if (existing != null) {
      if (!existing.requestHash().equals(requestHash)) {
        throw new BrokerCompException("IDEMPOTENCY_CONFLICT");
      }
      return existing.receipt();
    }
    BrokerCompensationAssignment changed = assignment.normalized(version.versionId());
    validateAssignment(version, changed);
    if (assignmentOverlaps(tenantId, changed)) {
      brokerAssignmentOverlapRejectedTotal.incrementAndGet();
      store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId,
          "BROKER_COMP_ASSIGNMENT_OVERLAP_REJECTED", clock));
      throw new BrokerCompException("BROKER_ASSIGNMENT_OVERLAP");
    }
    store.assignments().add(changed);
    BrokerCompensationAssignmentChangedEvent event = new BrokerCompensationAssignmentChangedEvent(tenantId,
        changed.assignmentId(), version.versionId(), changed.payeeType(), changed.payeeId(), changed.channel(),
        scopeHash(changed), actorId, correlationId, Instant.now(clock));
    store.outbox().add(event);
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId,
        "BROKER_COMPENSATION_ASSIGNMENT_CHANGED", clock));
    CommandReceipt receipt = new CommandReceipt(planId, plan.status(), version.versionNumber(), correlationId,
        List.of(event), "audit:" + planId);
    store.idempotencyReceipts().put(idemKey, new IdempotencyRecord(requestHash, receipt));
    return receipt;
  }

  public BrokerCompensationResult simulateQuote(String tenantId, String planId, int versionNumber, String brokerId,
      String channel, PaymentResponsibility paymentResponsibility, BigDecimal priceBeforeBrokerComp,
      Map<String, Object> priceContext, ConfigResolver configResolver) {
    requireText(tenantId, "tenantId");
    requireText(brokerId, "brokerId");
    requireText(channel, "channel");
    Objects.requireNonNull(paymentResponsibility, "paymentResponsibility is required");
    Objects.requireNonNull(priceBeforeBrokerComp, "priceBeforeBrokerComp is required");
    Objects.requireNonNull(configResolver, "configResolver is required");
    brokerCompResolveTotal.incrementAndGet();
    BrokerCompensationPlan plan = findById(tenantId, planId);
    BrokerCompensationPlanVersion version = versionByNumber(plan, versionNumber);
    if (plan.status() != PlanStatus.PUBLISHED) {
      throw failClosed(tenantId, planId, "BROKER_COMP_PLAN_NOT_PUBLISHED");
    }
    BrokerCompensationAssignment assignment = resolveActiveAssignment(tenantId, brokerId, channel, Instant.now(clock))
        .orElseThrow(() -> failClosed(tenantId, planId, "BROKER_COMP_PLAN_MISSING"));
    BrokerCompensationRule rule = selectRule(tenantId, planId, version, channel, paymentResponsibility);
    BigDecimal rawAmount = resolveRequired(configResolver, rule.amountRef(), tenantId, planId,
        "BROKER_COMP_AMOUNT_MISSING");
    BigDecimal floorAmount = resolveOptional(configResolver, rule.floorRef(), tenantId, planId,
        "BROKER_COMP_FLOOR_MISSING");
    BigDecimal capAmount = resolveOptional(configResolver, rule.capRef(), tenantId, planId,
        "BROKER_COMP_CAP_MISSING");
    if (capAmount != null && floorAmount != null && capAmount.compareTo(floorAmount) < 0) {
      throw failClosed(tenantId, planId, "BROKER_COMP_CAP_FLOOR_INVALID");
    }
    BigDecimal bounded = rawAmount;
    if (floorAmount != null) {
      bounded = bounded.max(floorAmount);
    }
    if (capAmount != null) {
      bounded = bounded.min(capAmount);
    }
    BigDecimal pricePoints = toPricePoints(rule.basis(), bounded, safeMap(priceContext), tenantId, planId);
    BigDecimal priceAfterBrokerComp = paymentResponsibility == PaymentResponsibility.LENDER_PAID
        ? priceBeforeBrokerComp.subtract(pricePoints).setScale(priceBeforeBrokerComp.scale(), RoundingMode.HALF_UP)
        : priceBeforeBrokerComp;
    boolean capFloorApplied = rawAmount.compareTo(bounded) != 0;
    String replayHash = replayHash(rule, assignment, priceBeforeBrokerComp, bounded, priceAfterBrokerComp);
    BrokerCompensationLedgerStep step = new BrokerCompensationLedgerStep("BROKER_COMPENSATION", brokerId,
        paymentResponsibility, priceBeforeBrokerComp, bounded, rule.basis(), capFloorApplied, capAmount, floorAmount,
        priceAfterBrokerComp, rule.reasonCode(), replayHash);
    BrokerCompensationResult result = new BrokerCompensationResult(planId, version.versionId(), assignment.assignmentId(),
        brokerId, channel, paymentResponsibility, rule.basis(), rawAmount, floorAmount, capAmount, bounded,
        pricePoints.movePointRight(2), priceAfterBrokerComp, List.of(step), rule.disclosureLabel());
    store.resultVisibility().put(replayHash, new BrokerVisibilityPolicy(rule.visibilityClassification(), rule.disclosureLabel(),
        SENSITIVE_PERMISSION));
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, "system", "broker-comp-resolve",
        "BROKER_COMPENSATION_RESOLVED", clock));
    return result;
  }

  public Optional<BrokerCompensationAssignment> resolveActiveAssignment(String tenantId, String payeeId,
      String channel, Instant effectiveAt) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(tenantId, "tenantId");
    requireText(payeeId, "payeeId");
    requireText(channel, "channel");
    Objects.requireNonNull(effectiveAt, "effectiveAt is required");
    List<BrokerCompensationAssignment> matches = store.assignments().stream()
        .filter(assignment -> BROKER_PAYEE_TYPE.equals(assignment.payeeType()))
        .filter(assignment -> assignment.payeeId().equals(payeeId))
        .filter(assignment -> assignment.channel().equals(channel))
        .filter(assignment -> contains(assignment.effectiveFrom(), assignment.effectiveTo(), effectiveAt))
        .filter(assignment -> assignmentTenantMatches(tenantId, assignment))
        .toList();
    if (matches.size() > 1) {
      throw new BrokerCompException("BROKER_ASSIGNMENT_OVERLAP");
    }
    return matches.stream().findFirst();
  }

  public BrokerCompensationResult applyVisibility(String viewerPermission, BrokerCompensationResult result) {
    Objects.requireNonNull(result, "result is required");
    if (SENSITIVE_PERMISSION.equals(viewerPermission)) {
      return result;
    }
    BrokerVisibilityPolicy policy = result.steps().stream()
        .map(step -> store.resultVisibility().get(step.replayHash()))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(new BrokerVisibilityPolicy("SENSITIVE", result.disclosureLabel(), SENSITIVE_PERMISSION));
    if ("PUBLIC".equals(policy.classification())) {
      return result;
    }
    brokerCompVisibilityRedactionTotal.incrementAndGet();
    return new BrokerCompensationResult(result.planId(), result.versionId(), result.assignmentId(), result.brokerId(),
        result.channel(), result.paymentResponsibility(), result.basis(), null, null, null, null, null,
        result.priceAfterBrokerComp(), List.of(), policy.redactedLabel());
  }

  public Optional<BrokerCompensationPlan> findPlan(String tenantId, String planId) {
    requireDurableStoreOrExplicitTestHarness();
    return Optional.ofNullable(store.plans().get(new PlanKey(tenantId, planId)));
  }

  public List<BrokerCompensationAssignment> assignments() {
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
      PlanStatus from, PlanStatus to, String action) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(correlationId, "correlationId");
    BrokerCompensationPlan plan = findById(tenantId, planId);
    if (plan.status() != from) {
      throw new BrokerCompException("VERSION_CONFLICT");
    }
    BrokerCompensationPlan changed = plan.withStatus(to, actorId, Instant.now(clock));
    store.plans().put(new PlanKey(tenantId, planId), changed);
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, actorId, correlationId, action, clock));
    return new CommandReceipt(planId, to, changed.currentVersion().versionNumber(), correlationId, List.of(),
        "audit:" + planId);
  }

  private BrokerCompensationRule selectRule(String tenantId, String planId, BrokerCompensationPlanVersion version, String channel,
      PaymentResponsibility paymentResponsibility) {
    return version.rules().stream()
        .filter(rule -> rule.paymentResponsibility() == paymentResponsibility)
        .filter(rule -> rule.eligibleChannels().contains(channel))
        .sorted(Comparator.comparingInt(BrokerCompensationRule::sortOrder))
        .findFirst()
        .orElseThrow(() -> channelInvalid(tenantId, planId));
  }

  private BigDecimal resolveRequired(ConfigResolver configResolver, String ref, String tenantId, String planId,
      String auditAction) {
    requireText(ref, "configRef");
    Optional<BigDecimal> value = configResolver.resolve(ref);
    if (value.isEmpty()) {
      throw failClosed(tenantId, planId, auditAction);
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
      throw failClosed(tenantId, planId, "BROKER_COMP_LOAN_AMOUNT_MISSING");
    }
    return amount.divide(loanAmount, 10, RoundingMode.HALF_UP).movePointRight(2);
  }

  private void validateRules(List<BrokerCompensationRule> rules) {
    if (rules.isEmpty()) {
      throw new BrokerCompException("VALIDATION_FAILED");
    }
    for (BrokerCompensationRule rule : rules) {
      if (rule.basis() == null) {
        throw new BrokerCompException("basis is required");
      }
      if (rule.paymentResponsibility() == null) {
        throw new BrokerCompException("paymentResponsibility is required");
      }
      requireText(rule.amountRef(), "amountRef");
      requireText(rule.reasonCode(), "reasonCode");
      requireText(rule.visibilityClassification(), "visibilityClassification");
      requireText(rule.disclosureLabel(), "disclosureLabel");
      if (rule.eligibleChannels().isEmpty()) {
        throw new BrokerCompException("BROKER_COMP_CHANNEL_INVALID");
      }
    }
  }

  private void validateAssignment(BrokerCompensationPlanVersion version, BrokerCompensationAssignment assignment) {
    requireText(assignment.assignmentId(), "assignmentId");
    requireText(assignment.planVersionId(), "planVersionId");
    requireText(assignment.payeeType(), "payeeType");
    requireText(assignment.payeeId(), "payeeId");
    requireText(assignment.channel(), "channel");
    if (!BROKER_PAYEE_TYPE.equals(assignment.payeeType())) {
      throw new BrokerCompException("BROKER_COMP_CHANNEL_INVALID");
    }
    if (version.rules().stream().noneMatch(rule -> rule.eligibleChannels().contains(assignment.channel()))) {
      throw new BrokerCompException("BROKER_COMP_CHANNEL_INVALID");
    }
    Objects.requireNonNull(assignment.effectiveFrom(), "effectiveFrom is required");
  }

  private boolean assignmentOverlaps(String tenantId, BrokerCompensationAssignment candidate) {
    return store.assignments().stream()
        .filter(assignment -> assignmentTenantMatches(tenantId, assignment))
        .filter(assignment -> BROKER_PAYEE_TYPE.equals(assignment.payeeType()))
        .filter(assignment -> assignment.payeeId().equals(candidate.payeeId()))
        .filter(assignment -> assignment.channel().equals(candidate.channel()))
        .anyMatch(assignment -> overlaps(assignment.effectiveFrom(), assignment.effectiveTo(), candidate.effectiveFrom(),
            candidate.effectiveTo()));
  }

  private boolean assignmentTenantMatches(String tenantId, BrokerCompensationAssignment assignment) {
    return store.plans().values().stream()
        .filter(plan -> plan.tenantId().equals(tenantId))
        .flatMap(plan -> plan.versions().stream())
        .anyMatch(version -> version.versionId().equals(assignment.planVersionId()));
  }

  private void rejectPublishedOverlap(BrokerCompensationPlan candidate) {
    BrokerCompensationPlanVersion candidateVersion = candidate.currentVersion();
    boolean overlap = store.plans().values().stream()
        .filter(plan -> !plan.planId().equals(candidate.planId()))
        .filter(plan -> plan.tenantId().equals(candidate.tenantId()))
        .filter(plan -> BROKER_PLAN_TYPE.equals(plan.planType()))
        .filter(plan -> plan.status() == PlanStatus.PUBLISHED)
        .map(BrokerCompensationPlan::currentVersion)
        .anyMatch(version -> overlaps(version.effectiveFrom(), version.effectiveTo(), candidateVersion.effectiveFrom(),
            candidateVersion.effectiveTo()));
    if (overlap) {
      throw new BrokerCompException("BROKER_ASSIGNMENT_OVERLAP");
    }
  }

  private BrokerCompensationPlan findById(String tenantId, String planId) {
    requireDurableStoreOrExplicitTestHarness();
    requireText(planId, "planId");
    return Optional.ofNullable(store.plans().get(new PlanKey(tenantId, planId)))
        .orElseThrow(() -> new BrokerCompException("NOT_FOUND"));
  }

  private static BrokerCompensationPlanVersion versionByNumber(BrokerCompensationPlan plan, int versionNumber) {
    return plan.versions().stream()
        .filter(version -> version.versionNumber() == versionNumber)
        .findFirst()
        .orElseThrow(() -> new BrokerCompException("NOT_FOUND"));
  }

  private BrokerCompException failClosed(String tenantId, String planId, String action) {
    brokerCompFailClosedTotal.incrementAndGet();
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, "system", "fail-closed", action, clock));
    return new BrokerCompException("POLICY_NOT_SATISFIED");
  }

  private BrokerCompException channelInvalid(String tenantId, String planId) {
    brokerCompFailClosedTotal.incrementAndGet();
    store.auditRecords().add(AuditRecord.completed(tenantId, planId, "system", "fail-closed", "BROKER_COMP_CHANNEL_INVALID", clock));
    return new BrokerCompException("BROKER_COMP_CHANNEL_INVALID");
  }

  private static boolean contains(Instant from, Instant to, Instant instant) {
    return !instant.isBefore(from) && (to == null || instant.isBefore(to));
  }

  private static boolean overlaps(Instant leftFrom, Instant leftTo, Instant rightFrom, Instant rightTo) {
    Instant leftEnd = leftTo == null ? Instant.MAX : leftTo;
    Instant rightEnd = rightTo == null ? Instant.MAX : rightTo;
    return leftFrom.isBefore(rightEnd) && rightFrom.isBefore(leftEnd);
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

  private static void requireCommand(String tenantId, String actorId, String idempotencyKey, String correlationId) {
    requireText(tenantId, "tenantId");
    requireText(actorId, "actorId");
    requireText(idempotencyKey, "idempotencyKey");
    requireText(correlationId, "correlationId");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new BrokerCompException(field + " is required");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
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

  private static String scopeHash(BrokerCompensationAssignment assignment) {
    return Integer.toHexString(Objects.hash(assignment.payeeId(), assignment.channel()));
  }

  private static String replayHash(BrokerCompensationRule rule, BrokerCompensationAssignment assignment,
      BigDecimal before, BigDecimal amount, BigDecimal after) {
    return Integer.toHexString(Objects.hash(rule.ruleId(), assignment.assignmentId(), rule.amountRef(), rule.capRef(),
        rule.floorRef(), before.stripTrailingZeros(), amount.stripTrailingZeros(), after.stripTrailingZeros()));
  }

  interface Store {
    Map<PlanKey, BrokerCompensationPlan> plans();
    Map<String, IdempotencyRecord> idempotencyReceipts();
    List<BrokerCompensationAssignment> assignments();
    List<Object> outbox();
    List<AuditRecord> auditRecords();
    Map<String, BrokerVisibilityPolicy> resultVisibility();

    default void requireAvailable() {}

    static Store failClosed(String component) {
      return new Store() {
        @Override public void requireAvailable() {
          ProcessLocalStatePolicy.requireDurableStoreOrExplicitTestHarness(false, component);
        }
        @Override public Map<PlanKey, BrokerCompensationPlan> plans() { return unavailable(); }
        @Override public Map<String, IdempotencyRecord> idempotencyReceipts() { return unavailable(); }
        @Override public List<BrokerCompensationAssignment> assignments() { return unavailable(); }
        @Override public List<Object> outbox() { return unavailable(); }
        @Override public List<AuditRecord> auditRecords() { return unavailable(); }
        @Override public Map<String, BrokerVisibilityPolicy> resultVisibility() { return unavailable(); }
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

  public enum PaymentResponsibility { LENDER_PAID, BORROWER_PAID }

  public enum PlanStatus { DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, SUSPENDED }

  public interface ConfigResolver {
    Optional<BigDecimal> resolve(String ref);
  }

  public record BrokerCompensationPlan(String tenantId, String planId, String planType, String name, PlanStatus status,
      List<BrokerCompensationPlanVersion> versions, String createdBy, String updatedBy, Instant createdAt,
      Instant updatedAt) {
    public BrokerCompensationPlan {
      versions = List.copyOf(Objects.requireNonNull(versions, "versions is required"));
    }

    public BrokerCompensationPlanVersion currentVersion() {
      return versions.stream().max(Comparator.comparingInt(BrokerCompensationPlanVersion::versionNumber)).orElseThrow();
    }

    BrokerCompensationPlan withStatus(PlanStatus status, String actorId, Instant now) {
      return new BrokerCompensationPlan(tenantId, planId, planType, name, status, versions, createdBy, actorId,
          createdAt, now);
    }

    BrokerCompensationPlan withVersion(BrokerCompensationPlanVersion changed, String actorId, Instant now) {
      List<BrokerCompensationPlanVersion> changedVersions = versions.stream()
          .map(version -> version.versionId().equals(changed.versionId()) ? changed : version)
          .toList();
      return new BrokerCompensationPlan(tenantId, planId, planType, name, status, changedVersions, createdBy, actorId,
          createdAt, now);
    }
  }

  public record BrokerCompensationPlanVersion(String versionId, String planId, int versionNumber,
      Instant effectiveFrom, Instant effectiveTo, String configHash, String approvalStatus, String approvedBy,
      Instant approvedAt, List<BrokerCompensationRule> rules) {
    public BrokerCompensationPlanVersion {
      rules = List.copyOf(Objects.requireNonNull(rules, "rules is required"));
    }

    BrokerCompensationPlanVersion withApproval(String approvalStatus, String approvedBy, Instant approvedAt) {
      return new BrokerCompensationPlanVersion(versionId, planId, versionNumber, effectiveFrom, effectiveTo, configHash,
          approvalStatus, approvedBy, approvedAt, rules);
    }
  }

  public record BrokerCompensationRule(String ruleId, CompBasis basis, PaymentResponsibility paymentResponsibility,
      String amountRef, String capRef, String floorRef, String reasonCode, String visibilityClassification,
      String disclosureLabel, Set<String> eligibleChannels, int sortOrder) {
    public BrokerCompensationRule {
      eligibleChannels = Set.copyOf(Objects.requireNonNull(eligibleChannels, "eligibleChannels is required"));
    }
  }

  public record BrokerCompensationAssignment(String assignmentId, String planVersionId, String payeeType,
      String payeeId, String channel, Instant effectiveFrom, Instant effectiveTo) {
    public BrokerCompensationAssignment {
      assignmentId = hasText(assignmentId) ? assignmentId : UUID.randomUUID().toString();
      payeeType = hasText(payeeType) ? payeeType : BROKER_PAYEE_TYPE;
    }

    BrokerCompensationAssignment normalized(String versionId) {
      return new BrokerCompensationAssignment(assignmentId, versionId, payeeType, payeeId, channel, effectiveFrom,
          effectiveTo);
    }
  }

  public record BrokerVisibilityPolicy(String classification, String redactedLabel, String requiredPermission) {}

  public record CommandReceipt(String planId, PlanStatus status, int version, String correlationId, List<Object> events,
      String auditRef) {
    public CommandReceipt {
      events = List.copyOf(Objects.requireNonNull(events, "events is required"));
    }
  }

  public record BrokerCompensationResult(String planId, String versionId, String assignmentId, String brokerId,
      String channel, PaymentResponsibility paymentResponsibility, CompBasis basis, BigDecimal rawAmount,
      BigDecimal floorAmount, BigDecimal capAmount, BigDecimal boundedAmount, BigDecimal priceImpactBps,
      BigDecimal priceAfterBrokerComp, List<BrokerCompensationLedgerStep> steps, String disclosureLabel) {
    public BrokerCompensationResult {
      steps = steps == null ? List.of() : List.copyOf(steps);
    }
  }

  public record BrokerCompensationLedgerStep(String stepType, String brokerId,
      PaymentResponsibility paymentResponsibility, BigDecimal inputPrice, BigDecimal amount, CompBasis basis,
      boolean capFloorApplied, BigDecimal capValue, BigDecimal floorValue, BigDecimal outputPrice, String reasonCode,
      String replayHash) {}

  public record BrokerCompensationPlanPublishedEvent(String tenantId, String planId, String versionId,
      String configHash, String actorId, String correlationId, Instant occurredAt) {}

  public record BrokerCompensationAssignmentChangedEvent(String tenantId, String assignmentId, String planVersionId,
      String payeeType, String payeeId, String channel, String scopeHash, String actorId, String correlationId,
      Instant occurredAt) {}

  public record AuditRecord(String tenantId, String planId, String actorId, String correlationId, String action,
      Instant recordedAt) {
    static AuditRecord completed(String tenantId, String planId, String actorId, String correlationId, String action,
        Clock clock) {
      return new AuditRecord(tenantId, planId, actorId, correlationId, action, Instant.now(clock));
    }
  }

  public static final class BrokerCompException extends RuntimeException {
    public BrokerCompException(String message) {
      super(message);
    }
  }
}
