package com.wcpe.margin;

import com.wcpe.margin.overlay.OverlayRule;
import com.wcpe.margin.overlay.OverlayRuleRepository;
import com.wcpe.margin.overlay.OverlayRuleTestFixtures;
import com.wcpe.margin.srp.SrpCalculationService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MarginServiceTestStores {
  private MarginServiceTestStores() {}

  static CompanyMarginPolicyService companyMarginPolicyService(Clock clock) {
    return companyMarginPolicyService(clock, new SrpCalculationService(), staticOverlayRules(List.of()));
  }

  static CompanyMarginPolicyService companyMarginPolicyService(Clock clock,
      SrpCalculationService srpCalculationService, OverlayRuleRepository overlayRuleRepository) {
    return new CompanyMarginPolicyService(clock, srpCalculationService, overlayRuleRepository, new CompanyStore());
  }

  static MarginReplayService marginReplayService(Clock clock) {
    return new MarginReplayService(clock, new ReplayStore());
  }

  static MarginGovernanceService marginGovernanceService(Clock clock) {
    return new MarginGovernanceService(clock, new GovernanceStore());
  }

  static ProfitabilityFloorService profitabilityFloorService(Clock clock) {
    return new ProfitabilityFloorService(clock, new ProfitabilityStore());
  }

  static MarginVersioningService marginVersioningService(Clock clock) {
    return new MarginVersioningService(clock, new VersioningStore());
  }

  static BrokerCompensationService brokerCompensationService(Clock clock) {
    return new BrokerCompensationService(clock, new BrokerStore());
  }

  static LoCompensationService loCompensationService(Clock clock) {
    return new LoCompensationService(clock, new LoStore());
  }

  static OverlayRuleRepository staticOverlayRules(List<OverlayRule> rules) {
    return OverlayRuleTestFixtures.staticRules(rules);
  }

  private static final class CompanyStore implements CompanyMarginPolicyService.Store {
    private final Map<CompanyMarginPolicyService.PolicyKey, CompanyMarginPolicyService.MarginPolicy> policies = new HashMap<>();
    private final Map<String, CompanyMarginPolicyService.CommandReceipt> idempotencyReceipts = new HashMap<>();
    private final List<CompanyMarginPolicyService.MarginPolicyPublishedEvent> outbox = new ArrayList<>();
    private final List<CompanyMarginPolicyService.AuditRecord> auditRecords = new ArrayList<>();
    @Override public Map<CompanyMarginPolicyService.PolicyKey, CompanyMarginPolicyService.MarginPolicy> policies() { return policies; }
    @Override public Map<String, CompanyMarginPolicyService.CommandReceipt> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<CompanyMarginPolicyService.MarginPolicyPublishedEvent> outbox() { return outbox; }
    @Override public List<CompanyMarginPolicyService.AuditRecord> auditRecords() { return auditRecords; }
  }

  private static final class ReplayStore implements MarginReplayService.Store {
    private final Map<MarginReplayService.FixtureKey, MarginReplayService.ReplayFixture> fixtureCatalog = new HashMap<>();
    private final Map<String, MarginReplayService.ReplayRun> replayRuns = new HashMap<>();
    private final List<MarginReplayService.DecisionReplayedEvent> outbox = new ArrayList<>();
    private final List<MarginReplayService.AuditEvidence> auditPackages = new ArrayList<>();
    @Override public Map<MarginReplayService.FixtureKey, MarginReplayService.ReplayFixture> fixtureCatalog() { return fixtureCatalog; }
    @Override public Map<String, MarginReplayService.ReplayRun> replayRuns() { return replayRuns; }
    @Override public List<MarginReplayService.DecisionReplayedEvent> outbox() { return outbox; }
    @Override public List<MarginReplayService.AuditEvidence> auditPackages() { return auditPackages; }
  }

  private static final class GovernanceStore implements MarginGovernanceService.Store {
    private final Map<MarginGovernanceService.ChangeKey, MarginGovernanceService.MarginGovernanceChangeRequest> changes = new HashMap<>();
    private final Map<String, MarginGovernanceService.GovernanceReceipt> idempotencyReceipts = new HashMap<>();
    private final List<Object> outbox = new ArrayList<>();
    private final List<MarginGovernanceService.AuditRecord> auditRecords = new ArrayList<>();
    @Override public Map<MarginGovernanceService.ChangeKey, MarginGovernanceService.MarginGovernanceChangeRequest> changes() { return changes; }
    @Override public Map<String, MarginGovernanceService.GovernanceReceipt> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<MarginGovernanceService.AuditRecord> auditRecords() { return auditRecords; }
  }

  private static final class ProfitabilityStore implements ProfitabilityFloorService.Store {
    private final Map<ProfitabilityFloorService.PolicyKey, ProfitabilityFloorService.ProfitabilityPolicy> policies = new HashMap<>();
    private final Map<String, ProfitabilityFloorService.IdempotencyRecord> idempotencyReceipts = new HashMap<>();
    private final List<Object> outbox = new ArrayList<>();
    private final List<ProfitabilityFloorService.AuditRecord> auditRecords = new ArrayList<>();
    private final List<ProfitabilityFloorService.ProfitabilityEvaluation> evaluations = new ArrayList<>();
    @Override public Map<ProfitabilityFloorService.PolicyKey, ProfitabilityFloorService.ProfitabilityPolicy> policies() { return policies; }
    @Override public Map<String, ProfitabilityFloorService.IdempotencyRecord> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<ProfitabilityFloorService.AuditRecord> auditRecords() { return auditRecords; }
    @Override public List<ProfitabilityFloorService.ProfitabilityEvaluation> evaluations() { return evaluations; }
  }

  private static final class VersioningStore implements MarginVersioningService.Store {
    private final Map<MarginVersioningService.VersionKey, MarginVersioningService.PolicyVersionRef> publishedVersions = new HashMap<>();
    private final Map<MarginVersioningService.ManifestCacheKey, MarginVersioningService.MarginCompVersionManifest> derivedManifestCache = new HashMap<>();
    private final Map<String, MarginVersioningService.MarginCompVersionManifest> replayManifests = new HashMap<>();
    private final List<Object> outbox = new ArrayList<>();
    private final List<MarginVersioningService.AuditRecord> auditRecords = new ArrayList<>();
    @Override public Map<MarginVersioningService.VersionKey, MarginVersioningService.PolicyVersionRef> publishedVersions() { return publishedVersions; }
    @Override public Map<MarginVersioningService.ManifestCacheKey, MarginVersioningService.MarginCompVersionManifest> derivedManifestCache() { return derivedManifestCache; }
    @Override public Map<String, MarginVersioningService.MarginCompVersionManifest> replayManifests() { return replayManifests; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<MarginVersioningService.AuditRecord> auditRecords() { return auditRecords; }
  }

  private static final class BrokerStore implements BrokerCompensationService.Store {
    private final Map<BrokerCompensationService.PlanKey, BrokerCompensationService.BrokerCompensationPlan> plans = new HashMap<>();
    private final Map<String, BrokerCompensationService.IdempotencyRecord> idempotencyReceipts = new HashMap<>();
    private final List<BrokerCompensationService.BrokerCompensationAssignment> assignments = new ArrayList<>();
    private final List<Object> outbox = new ArrayList<>();
    private final List<BrokerCompensationService.AuditRecord> auditRecords = new ArrayList<>();
    private final Map<String, BrokerCompensationService.BrokerVisibilityPolicy> resultVisibility = new HashMap<>();
    @Override public Map<BrokerCompensationService.PlanKey, BrokerCompensationService.BrokerCompensationPlan> plans() { return plans; }
    @Override public Map<String, BrokerCompensationService.IdempotencyRecord> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<BrokerCompensationService.BrokerCompensationAssignment> assignments() { return assignments; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<BrokerCompensationService.AuditRecord> auditRecords() { return auditRecords; }
    @Override public Map<String, BrokerCompensationService.BrokerVisibilityPolicy> resultVisibility() { return resultVisibility; }
  }

  private static final class LoStore implements LoCompensationService.Store {
    private final Map<LoCompensationService.PlanKey, LoCompensationService.CompensationPlan> plans = new HashMap<>();
    private final Map<String, LoCompensationService.IdempotencyRecord> idempotencyReceipts = new HashMap<>();
    private final List<LoCompensationService.CompensationAssignment> assignments = new ArrayList<>();
    private final List<Object> outbox = new ArrayList<>();
    private final List<LoCompensationService.AuditRecord> auditRecords = new ArrayList<>();
    private final Map<String, String> resultVisibility = new HashMap<>();
    @Override public Map<LoCompensationService.PlanKey, LoCompensationService.CompensationPlan> plans() { return plans; }
    @Override public Map<String, LoCompensationService.IdempotencyRecord> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<LoCompensationService.CompensationAssignment> assignments() { return assignments; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<LoCompensationService.AuditRecord> auditRecords() { return auditRecords; }
    @Override public Map<String, String> resultVisibility() { return resultVisibility; }
  }

}
