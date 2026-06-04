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

public final class CompanyMarginPolicyService {
  private static final String POLICY_TYPE = "COMPANY";
  private final Clock clock;
  private final Map<PolicyKey, MarginPolicy> policies = new HashMap<>();
  private final Map<String, CommandReceipt> idempotencyReceipts = new HashMap<>();
  private final List<MarginPolicyPublishedEvent> outbox = new ArrayList<>();
  private final List<AuditRecord> auditRecords = new ArrayList<>();

  public CompanyMarginPolicyService(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public CommandReceipt createDraft(CreatePolicyCommand command) {
    requireCommand(command.tenantId(), command.actorId(), command.idempotencyKey(), command.correlationId());
    String idemKey = idempotencyKey(command.tenantId(), command.idempotencyKey());
    CommandReceipt existing = idempotencyReceipts.get(idemKey);
    if (existing != null) {
      if (!existing.requestHash().equals(command.requestHash())) {
        throw new MarginPolicyException("IDEMPOTENCY_CONFLICT");
      }
      return existing;
    }
    validateDraft(command.name(), command.version());
    PolicyKey key = new PolicyKey(command.tenantId(), command.name());
    if (policies.containsKey(key)) {
      throw new MarginPolicyException("MARGIN_POLICY_ALREADY_EXISTS");
    }
    MarginPolicy policy = new MarginPolicy(
        command.tenantId(),
        UUID.randomUUID().toString(),
        command.name(),
        PolicyStatus.DRAFT,
        List.of(command.version()),
        command.actorId(),
        command.actorId(),
        Instant.now(clock),
        Instant.now(clock));
    policies.put(key, policy);
    CommandReceipt receipt = new CommandReceipt(policy.policyId(), policy.status(), policy.currentVersion().versionNumber(),
        command.correlationId(), command.requestHash(), List.of(), "audit:" + policy.policyId());
    idempotencyReceipts.put(idemKey, receipt);
    auditRecords.add(AuditRecord.completed(command.tenantId(), policy.policyId(), command.actorId(), command.correlationId(), "DRAFT_CREATED"));
    return receipt;
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
    policies.put(new PolicyKey(tenantId, policy.name()), published);
    MarginPolicyVersion version = published.currentVersion();
    MarginPolicyPublishedEvent event = new MarginPolicyPublishedEvent(
        tenantId,
        policyId,
        version.versionId(),
        POLICY_TYPE,
        version.scope().stableHash(),
        version.effectiveWindow().effectiveFromUtc(),
        version.configHash(),
        actorId,
        correlationId);
    outbox.add(event);
    auditRecords.add(AuditRecord.completed(tenantId, policyId, actorId, correlationId, "MARGIN_POLICY_PUBLISHED"));
    return new CommandReceipt(policyId, PolicyStatus.PUBLISHED, version.versionNumber(), correlationId, "publish:" + policyId, List.of(event), "audit:" + policyId);
  }

  public CommandReceipt suspend(String tenantId, String policyId, String actorId, String correlationId) {
    return transition(tenantId, policyId, actorId, correlationId, PolicyStatus.PUBLISHED, PolicyStatus.SUSPENDED, "SUSPENDED");
  }

  public Optional<MarginPolicy> resolvePublished(String tenantId, MarginScope scope, Instant pricedAtUtc) {
    List<MarginPolicy> matches = policies.values().stream()
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
    return List.copyOf(outbox);
  }

  public List<AuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
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
    policies.put(new PolicyKey(tenantId, changed.name()), changed);
    auditRecords.add(AuditRecord.completed(tenantId, policyId, actorId, correlationId, action));
    return new CommandReceipt(policyId, to, changed.currentVersion().versionNumber(), correlationId, action + ":" + policyId, List.of(), "audit:" + policyId);
  }

  private MarginCalculationStep calculate(MarginRule rule, ConfigResolver configResolver, BigDecimal priceBeforeMargin, String sourceVersionId) {
    BigDecimal amount = resolveRequired(configResolver, rule.amountRef());
    BigDecimal min = resolveRequired(configResolver, rule.minRef());
    BigDecimal max = resolveRequired(configResolver, rule.maxRef());
    if (min.compareTo(max) > 0) {
      throw new MarginPolicyException("MARGIN_BOUND_INVALID");
    }
    BigDecimal margin = amount.max(min).min(max);
    BigDecimal points = rule.unit() == MarginUnit.BPS ? margin.movePointLeft(2) : margin;
    BigDecimal priceAfterMargin = priceBeforeMargin.subtract(points).setScale(rule.roundingScale(), RoundingMode.HALF_UP);
    return new MarginCalculationStep("COMPANY_MARGIN", sourceVersionId, priceBeforeMargin, priceAfterMargin, points,
        rule.reasonCode(), replayHash(rule, priceBeforeMargin, priceAfterMargin));
  }

  private BigDecimal resolveRequired(ConfigResolver configResolver, String ref) {
    return configResolver.resolve(ref).orElseThrow(() -> new MarginPolicyException("MARGIN_CONFIG_UNAVAILABLE"));
  }

  private void validateDraft(String name, MarginPolicyVersion version) {
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
    }
  }

  private void rejectPublishedOverlap(MarginPolicy candidate) {
    boolean overlap = policies.values().stream()
        .filter(policy -> !policy.policyId().equals(candidate.policyId()))
        .filter(policy -> policy.tenantId().equals(candidate.tenantId()))
        .filter(policy -> policy.status() == PolicyStatus.PUBLISHED)
        .anyMatch(policy -> policy.currentVersion().scope().matches(candidate.currentVersion().scope())
            && policy.currentVersion().effectiveWindow().overlaps(candidate.currentVersion().effectiveWindow()));
    if (overlap) {
      throw new MarginPolicyException("MARGIN_SCOPE_OVERLAP");
    }
  }

  private MarginPolicy findById(String tenantId, String policyId) {
    return policies.values().stream()
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

  private record PolicyKey(String tenantId, String name) {}

  public enum PolicyStatus { DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, SUSPENDED }

  public enum MarginUnit { BPS, PRICE_POINTS }

  public interface ConfigResolver {
    Optional<BigDecimal> resolve(String ref);
  }

  public record CreatePolicyCommand(String tenantId, String requestId, String actorId, String idempotencyKey,
      String correlationId, String name, MarginPolicyVersion version, String requestHash) {}

  public record CommandReceipt(String policyId, PolicyStatus status, int version, String correlationId,
      String requestHash, List<MarginPolicyPublishedEvent> events, String auditRef) {}

  public record MarginPolicy(String tenantId, String policyId, String name, PolicyStatus status,
      List<MarginPolicyVersion> versions, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {
    public MarginPolicyVersion currentVersion() {
      return versions.stream().max(Comparator.comparingInt(MarginPolicyVersion::versionNumber)).orElseThrow();
    }

    MarginPolicy withStatus(PolicyStatus status, String actorId, Instant now) {
      return new MarginPolicy(tenantId, policyId, name, status, versions, createdBy, actorId, createdAt, now);
    }
  }

  public record MarginPolicyVersion(String versionId, int versionNumber, MarginScope scope,
      EffectiveWindow effectiveWindow, List<MarginRule> rules, String configHash) {}

  public record MarginRule(int priority, MarginUnit unit, String amountRef, String minRef, String maxRef,
      int roundingScale, String reasonCode) {}

  public record MarginScope(String productFamily, String investorGroup, String channel, String state,
      String occupancy, String purpose, String lockPeriodBucket) {
    boolean matches(MarginScope other) {
      return match(productFamily, other.productFamily) && match(investorGroup, other.investorGroup)
          && match(channel, other.channel) && match(state, other.state)
          && match(occupancy, other.occupancy) && match(purpose, other.purpose)
          && match(lockPeriodBucket, other.lockPeriodBucket);
    }

    String stableHash() {
      return Integer.toHexString(Objects.hash(productFamily, investorGroup, channel, state, occupancy, purpose, lockPeriodBucket));
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
