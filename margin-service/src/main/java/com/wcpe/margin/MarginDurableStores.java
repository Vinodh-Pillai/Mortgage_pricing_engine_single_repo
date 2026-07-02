package com.wcpe.margin;

import java.util.List;
import java.util.Map;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Profile-gated process-local margin store adapters for domains whose persistence contracts and migrations
 * exist in this service. These adapters are allowed only for local/dev/test profiles or an explicit harness
 * property. Production defaults fail closed rather than silently using JVM-static maps when a durable
 * repository adapter is not configured and wired.
 */
final class MarginDurableStores {
  private static final CompanyStore COMPANY = new CompanyStore();
  private static final VersioningStore VERSIONING = new VersioningStore();
  private static final ProfitabilityStore PROFITABILITY = new ProfitabilityStore();
  private static final LoStore LO = new LoStore();
  private static final BrokerStore BROKER = new BrokerStore();
  private static final GovernanceStore GOVERNANCE = new GovernanceStore();
  private static final ReplayStore REPLAY = new ReplayStore();

  private MarginDurableStores() {}

  static CompanyMarginPolicyService.Store companyMarginPolicyStore() {
    if (ProcessLocalStatePolicy.durableStorePath().isPresent()) {
      return durableRepository().companyMarginPolicyStore();
    }
    ProcessLocalStatePolicy.requireProcessLocalStoreProfileOrFailClosed("CompanyMarginPolicyService");
    return COMPANY;
  }

  static MarginVersioningService.Store marginVersioningStore() {
    if (ProcessLocalStatePolicy.durableStorePath().isPresent()) {
      return durableRepository().marginVersioningStore();
    }
    ProcessLocalStatePolicy.requireProcessLocalStoreProfileOrFailClosed("MarginVersioningService");
    return VERSIONING;
  }

  static ProfitabilityFloorService.Store profitabilityFloorStore() {
    if (ProcessLocalStatePolicy.durableStorePath().isPresent()) {
      return durableRepository().profitabilityFloorStore();
    }
    ProcessLocalStatePolicy.requireProcessLocalStoreProfileOrFailClosed("ProfitabilityFloorService");
    return PROFITABILITY;
  }

  static LoCompensationService.Store loCompensationStore() {
    if (ProcessLocalStatePolicy.durableStorePath().isPresent()) {
      return durableRepository().loCompensationStore();
    }
    ProcessLocalStatePolicy.requireProcessLocalStoreProfileOrFailClosed("LoCompensationService");
    return LO;
  }

  static BrokerCompensationService.Store brokerCompensationStore() {
    if (ProcessLocalStatePolicy.durableStorePath().isPresent()) {
      return durableRepository().brokerCompensationStore();
    }
    ProcessLocalStatePolicy.requireProcessLocalStoreProfileOrFailClosed("BrokerCompensationService");
    return BROKER;
  }

  static MarginGovernanceService.Store governanceStore() {
    if (ProcessLocalStatePolicy.durableStorePath().isPresent()) {
      return durableRepository().governanceStore();
    }
    ProcessLocalStatePolicy.requireProcessLocalStoreProfileOrFailClosed("MarginGovernanceService");
    return GOVERNANCE;
  }

  static MarginReplayService.Store replayStore() {
    if (ProcessLocalStatePolicy.durableStorePath().isPresent()) {
      return durableRepository().replayStore();
    }
    ProcessLocalStatePolicy.requireProcessLocalStoreProfileOrFailClosed("MarginReplayService");
    return REPLAY;
  }

  private static MarginFileBackedStores durableRepository() {
    return MarginFileBackedStores.forPath(Paths.get(ProcessLocalStatePolicy.durableStorePath().orElseThrow()));
  }

  private static final class CompanyStore implements CompanyMarginPolicyService.Store {
    private final Map<CompanyMarginPolicyService.PolicyKey, CompanyMarginPolicyService.MarginPolicy> policies =
        new ConcurrentHashMap<>();
    private final Map<String, CompanyMarginPolicyService.CommandReceipt> idempotencyReceipts =
        new ConcurrentHashMap<>();
    private final List<CompanyMarginPolicyService.MarginPolicyPublishedEvent> outbox = new CopyOnWriteArrayList<>();
    private final List<CompanyMarginPolicyService.AuditRecord> auditRecords = new CopyOnWriteArrayList<>();

    @Override public Map<CompanyMarginPolicyService.PolicyKey, CompanyMarginPolicyService.MarginPolicy> policies() { return policies; }
    @Override public Map<String, CompanyMarginPolicyService.CommandReceipt> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<CompanyMarginPolicyService.MarginPolicyPublishedEvent> outbox() { return outbox; }
    @Override public List<CompanyMarginPolicyService.AuditRecord> auditRecords() { return auditRecords; }
  }

  private static final class VersioningStore implements MarginVersioningService.Store {
    private final Map<MarginVersioningService.VersionKey, MarginVersioningService.PolicyVersionRef> publishedVersions =
        new ConcurrentHashMap<>();
    private final Map<MarginVersioningService.ManifestCacheKey, MarginVersioningService.MarginCompVersionManifest>
        derivedManifestCache = new ConcurrentHashMap<>();
    private final Map<String, MarginVersioningService.MarginCompVersionManifest> replayManifests =
        new ConcurrentHashMap<>();
    private final List<Object> outbox = new CopyOnWriteArrayList<>();
    private final List<MarginVersioningService.AuditRecord> auditRecords = new CopyOnWriteArrayList<>();

    @Override public Map<MarginVersioningService.VersionKey, MarginVersioningService.PolicyVersionRef> publishedVersions() { return publishedVersions; }
    @Override public Map<MarginVersioningService.ManifestCacheKey, MarginVersioningService.MarginCompVersionManifest> derivedManifestCache() { return derivedManifestCache; }
    @Override public Map<String, MarginVersioningService.MarginCompVersionManifest> replayManifests() { return replayManifests; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<MarginVersioningService.AuditRecord> auditRecords() { return auditRecords; }
  }

  private static final class ProfitabilityStore implements ProfitabilityFloorService.Store {
    private final Map<ProfitabilityFloorService.PolicyKey, ProfitabilityFloorService.ProfitabilityPolicy> policies =
        new ConcurrentHashMap<>();
    private final Map<String, ProfitabilityFloorService.IdempotencyRecord> idempotencyReceipts =
        new ConcurrentHashMap<>();
    private final List<Object> outbox = new CopyOnWriteArrayList<>();
    private final List<ProfitabilityFloorService.AuditRecord> auditRecords = new CopyOnWriteArrayList<>();
    private final List<ProfitabilityFloorService.ProfitabilityEvaluation> evaluations = new CopyOnWriteArrayList<>();

    @Override public Map<ProfitabilityFloorService.PolicyKey, ProfitabilityFloorService.ProfitabilityPolicy> policies() { return policies; }
    @Override public Map<String, ProfitabilityFloorService.IdempotencyRecord> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<ProfitabilityFloorService.AuditRecord> auditRecords() { return auditRecords; }
    @Override public List<ProfitabilityFloorService.ProfitabilityEvaluation> evaluations() { return evaluations; }
  }

  private static final class LoStore implements LoCompensationService.Store {
    private final Map<LoCompensationService.PlanKey, LoCompensationService.CompensationPlan> plans =
        new ConcurrentHashMap<>();
    private final Map<String, LoCompensationService.IdempotencyRecord> idempotencyReceipts =
        new ConcurrentHashMap<>();
    private final List<LoCompensationService.CompensationAssignment> assignments = new CopyOnWriteArrayList<>();
    private final List<Object> outbox = new CopyOnWriteArrayList<>();
    private final List<LoCompensationService.AuditRecord> auditRecords = new CopyOnWriteArrayList<>();
    private final Map<String, String> resultVisibility = new ConcurrentHashMap<>();

    @Override public Map<LoCompensationService.PlanKey, LoCompensationService.CompensationPlan> plans() { return plans; }
    @Override public Map<String, LoCompensationService.IdempotencyRecord> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<LoCompensationService.CompensationAssignment> assignments() { return assignments; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<LoCompensationService.AuditRecord> auditRecords() { return auditRecords; }
    @Override public Map<String, String> resultVisibility() { return resultVisibility; }
  }

  private static final class BrokerStore implements BrokerCompensationService.Store {
    private final Map<BrokerCompensationService.PlanKey, BrokerCompensationService.BrokerCompensationPlan> plans =
        new ConcurrentHashMap<>();
    private final Map<String, BrokerCompensationService.IdempotencyRecord> idempotencyReceipts =
        new ConcurrentHashMap<>();
    private final List<BrokerCompensationService.BrokerCompensationAssignment> assignments = new CopyOnWriteArrayList<>();
    private final List<Object> outbox = new CopyOnWriteArrayList<>();
    private final List<BrokerCompensationService.AuditRecord> auditRecords = new CopyOnWriteArrayList<>();
    private final Map<String, BrokerCompensationService.BrokerVisibilityPolicy> resultVisibility =
        new ConcurrentHashMap<>();

    @Override public Map<BrokerCompensationService.PlanKey, BrokerCompensationService.BrokerCompensationPlan> plans() { return plans; }
    @Override public Map<String, BrokerCompensationService.IdempotencyRecord> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<BrokerCompensationService.BrokerCompensationAssignment> assignments() { return assignments; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<BrokerCompensationService.AuditRecord> auditRecords() { return auditRecords; }
    @Override public Map<String, BrokerCompensationService.BrokerVisibilityPolicy> resultVisibility() { return resultVisibility; }
  }

  private static final class GovernanceStore implements MarginGovernanceService.Store {
    private final Map<MarginGovernanceService.ChangeKey, MarginGovernanceService.MarginGovernanceChangeRequest> changes =
        new ConcurrentHashMap<>();
    private final Map<String, MarginGovernanceService.GovernanceReceipt> idempotencyReceipts =
        new ConcurrentHashMap<>();
    private final List<Object> outbox = new CopyOnWriteArrayList<>();
    private final List<MarginGovernanceService.AuditRecord> auditRecords = new CopyOnWriteArrayList<>();

    @Override public Map<MarginGovernanceService.ChangeKey, MarginGovernanceService.MarginGovernanceChangeRequest> changes() { return changes; }
    @Override public Map<String, MarginGovernanceService.GovernanceReceipt> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<MarginGovernanceService.AuditRecord> auditRecords() { return auditRecords; }
  }

  private static final class ReplayStore implements MarginReplayService.Store {
    private final Map<MarginReplayService.FixtureKey, MarginReplayService.ReplayFixture> fixtureCatalog =
        new ConcurrentHashMap<>();
    private final Map<String, MarginReplayService.ReplayRun> replayRuns = new ConcurrentHashMap<>();
    private final List<MarginReplayService.DecisionReplayedEvent> outbox = new CopyOnWriteArrayList<>();
    private final List<MarginReplayService.AuditEvidence> auditPackages = new CopyOnWriteArrayList<>();

    @Override public Map<MarginReplayService.FixtureKey, MarginReplayService.ReplayFixture> fixtureCatalog() { return fixtureCatalog; }
    @Override public Map<String, MarginReplayService.ReplayRun> replayRuns() { return replayRuns; }
    @Override public List<MarginReplayService.DecisionReplayedEvent> outbox() { return outbox; }
    @Override public List<MarginReplayService.AuditEvidence> auditPackages() { return auditPackages; }
  }
}
