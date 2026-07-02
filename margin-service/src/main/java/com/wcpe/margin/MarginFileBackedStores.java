package com.wcpe.margin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** File-backed margin repository adapter used only when wcpe.margin.durable-store.path is configured. */
final class MarginFileBackedStores {
  private static final ObjectMapper JSON = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .registerModule(new Jdk8Module())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private static final Map<Path, MarginFileBackedStores> REPOSITORIES = new ConcurrentHashMap<>();

  static MarginFileBackedStores forPath(Path storageFile) {
    Path normalized = storageFile.toAbsolutePath().normalize();
    return REPOSITORIES.computeIfAbsent(normalized, MarginFileBackedStores::new);
  }

  private final Object lock = new Object();
  private final Path storageFile;
  private Snapshot snapshot;
  private CompanyStore companyStore;
  private VersioningStore versioningStore;
  private ProfitabilityStore profitabilityStore;
  private LoStore loStore;
  private BrokerStore brokerStore;
  private GovernanceStore governanceStore;
  private ReplayStore replayStore;

  private MarginFileBackedStores(Path storageFile) {
    this.storageFile = storageFile;
    synchronized (lock) {
      snapshot = load(storageFile);
      reindexStores();
    }
  }

  CompanyMarginPolicyService.Store companyMarginPolicyStore() { return companyStore; }
  MarginVersioningService.Store marginVersioningStore() { return versioningStore; }
  ProfitabilityFloorService.Store profitabilityFloorStore() { return profitabilityStore; }
  LoCompensationService.Store loCompensationStore() { return loStore; }
  BrokerCompensationService.Store brokerCompensationStore() { return brokerStore; }
  MarginGovernanceService.Store governanceStore() { return governanceStore; }
  MarginReplayService.Store replayStore() { return replayStore; }

  private Snapshot load(Path path) {
    if (!Files.exists(path)) {
      return new Snapshot();
    }
    try {
      Snapshot loaded = JSON.readValue(path.toFile(), Snapshot.class);
      return loaded == null ? new Snapshot() : loaded;
    } catch (IOException e) {
      throw new ProcessLocalStatePolicy.PersistenceNotConfiguredException(
          "MarginFileBackedStores:PERSISTENCE_STORE_UNREADABLE:" + e.getMessage());
    }
  }

  private void persist() {
    synchronized (lock) {
      snapshot = snapshot();
      try {
        Path parent = storageFile.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Path lockFile = storageFile.resolveSibling(storageFile.getFileName() + ".lock");
        Path tmp = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
          JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), snapshot);
          Files.move(tmp, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
      } catch (IOException e) {
        throw new ProcessLocalStatePolicy.PersistenceNotConfiguredException(
            "MarginFileBackedStores:PERSISTENCE_STORE_UNAVAILABLE:" + e.getMessage());
      }
    }
  }

  private void reindexStores() {
    companyStore = new CompanyStore(snapshot.company, this::persist);
    versioningStore = new VersioningStore(snapshot.versioning, this::persist);
    profitabilityStore = new ProfitabilityStore(snapshot.profitability, this::persist);
    loStore = new LoStore(snapshot.lo, this::persist);
    brokerStore = new BrokerStore(snapshot.broker, this::persist);
    governanceStore = new GovernanceStore(snapshot.governance, this::persist);
    replayStore = new ReplayStore(snapshot.replay, this::persist);
  }

  private Snapshot snapshot() {
    Snapshot next = new Snapshot();
    next.company = companyStore.snapshot();
    next.versioning = versioningStore.snapshot();
    next.profitability = profitabilityStore.snapshot();
    next.lo = loStore.snapshot();
    next.broker = brokerStore.snapshot();
    next.governance = governanceStore.snapshot();
    next.replay = replayStore.snapshot();
    return next;
  }

  static final class Snapshot {
    public CompanySnapshot company = new CompanySnapshot();
    public VersioningSnapshot versioning = new VersioningSnapshot();
    public ProfitabilitySnapshot profitability = new ProfitabilitySnapshot();
    public LoSnapshot lo = new LoSnapshot();
    public BrokerSnapshot broker = new BrokerSnapshot();
    public GovernanceSnapshot governance = new GovernanceSnapshot();
    public ReplaySnapshot replay = new ReplaySnapshot();
  }

  public static final class CompanySnapshot {
    public List<CompanyMarginPolicyService.MarginPolicy> policies = new ArrayList<>();
    public Map<String, CompanyMarginPolicyService.CommandReceipt> idempotencyReceipts = new LinkedHashMap<>();
    public List<CompanyMarginPolicyService.MarginPolicyPublishedEvent> outbox = new ArrayList<>();
    public List<CompanyMarginPolicyService.AuditRecord> auditRecords = new ArrayList<>();
  }

  public static final class VersioningSnapshot {
    public List<MarginVersioningService.PolicyVersionRef> publishedVersions = new ArrayList<>();
    public List<MarginVersioningService.MarginCompVersionManifest> derivedManifestCache = new ArrayList<>();
    public Map<String, MarginVersioningService.MarginCompVersionManifest> replayManifests = new LinkedHashMap<>();
    public List<Object> outbox = new ArrayList<>();
    public List<MarginVersioningService.AuditRecord> auditRecords = new ArrayList<>();
  }

  public static final class ProfitabilitySnapshot {
    public List<ProfitabilityFloorService.ProfitabilityPolicy> policies = new ArrayList<>();
    public Map<String, ProfitabilityFloorService.IdempotencyRecord> idempotencyReceipts = new LinkedHashMap<>();
    public List<Object> outbox = new ArrayList<>();
    public List<ProfitabilityFloorService.AuditRecord> auditRecords = new ArrayList<>();
    public List<ProfitabilityFloorService.ProfitabilityEvaluation> evaluations = new ArrayList<>();
  }

  public static final class LoSnapshot {
    public List<LoCompensationService.CompensationPlan> plans = new ArrayList<>();
    public Map<String, LoCompensationService.IdempotencyRecord> idempotencyReceipts = new LinkedHashMap<>();
    public List<LoCompensationService.CompensationAssignment> assignments = new ArrayList<>();
    public List<Object> outbox = new ArrayList<>();
    public List<LoCompensationService.AuditRecord> auditRecords = new ArrayList<>();
    public Map<String, String> resultVisibility = new LinkedHashMap<>();
  }

  public static final class BrokerSnapshot {
    public List<BrokerCompensationService.BrokerCompensationPlan> plans = new ArrayList<>();
    public Map<String, BrokerCompensationService.IdempotencyRecord> idempotencyReceipts = new LinkedHashMap<>();
    public List<BrokerCompensationService.BrokerCompensationAssignment> assignments = new ArrayList<>();
    public List<Object> outbox = new ArrayList<>();
    public List<BrokerCompensationService.AuditRecord> auditRecords = new ArrayList<>();
    public Map<String, BrokerCompensationService.BrokerVisibilityPolicy> resultVisibility = new LinkedHashMap<>();
  }

  public static final class GovernanceSnapshot {
    public List<MarginGovernanceService.MarginGovernanceChangeRequest> changes = new ArrayList<>();
    public Map<String, MarginGovernanceService.GovernanceReceipt> idempotencyReceipts = new LinkedHashMap<>();
    public List<Object> outbox = new ArrayList<>();
    public List<MarginGovernanceService.AuditRecord> auditRecords = new ArrayList<>();
  }

  public static final class ReplaySnapshot {
    public List<MarginReplayService.ReplayFixture> fixtureCatalog = new ArrayList<>();
    public Map<String, MarginReplayService.ReplayRun> replayRuns = new LinkedHashMap<>();
    public List<MarginReplayService.DecisionReplayedEvent> outbox = new ArrayList<>();
    public List<MarginReplayService.AuditEvidence> auditPackages = new ArrayList<>();
  }

  private static final class CompanyStore implements CompanyMarginPolicyService.Store {
    private final SavingMap<CompanyMarginPolicyService.PolicyKey, CompanyMarginPolicyService.MarginPolicy> policies;
    private final SavingMap<String, CompanyMarginPolicyService.CommandReceipt> idempotencyReceipts;
    private final SavingList<CompanyMarginPolicyService.MarginPolicyPublishedEvent> outbox;
    private final SavingList<CompanyMarginPolicyService.AuditRecord> auditRecords;
    CompanyStore(CompanySnapshot snapshot, Runnable save) {
      policies = SavingMap.of(save, snapshot.policies, p -> new CompanyMarginPolicyService.PolicyKey(p.tenantId(), p.policyId()));
      idempotencyReceipts = SavingMap.of(save, snapshot.idempotencyReceipts);
      outbox = SavingList.of(save, snapshot.outbox);
      auditRecords = SavingList.of(save, snapshot.auditRecords);
    }
    @Override public Map<CompanyMarginPolicyService.PolicyKey, CompanyMarginPolicyService.MarginPolicy> policies() { return policies; }
    @Override public Map<String, CompanyMarginPolicyService.CommandReceipt> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<CompanyMarginPolicyService.MarginPolicyPublishedEvent> outbox() { return outbox; }
    @Override public List<CompanyMarginPolicyService.AuditRecord> auditRecords() { return auditRecords; }
    CompanySnapshot snapshot() { CompanySnapshot s = new CompanySnapshot(); s.policies = new ArrayList<>(policies.values()); s.idempotencyReceipts = new LinkedHashMap<>(idempotencyReceipts); s.outbox = new ArrayList<>(outbox); s.auditRecords = new ArrayList<>(auditRecords); return s; }
  }

  private static final class VersioningStore implements MarginVersioningService.Store {
    private final SavingMap<MarginVersioningService.VersionKey, MarginVersioningService.PolicyVersionRef> publishedVersions;
    private final SavingMap<MarginVersioningService.ManifestCacheKey, MarginVersioningService.MarginCompVersionManifest> derivedManifestCache;
    private final SavingMap<String, MarginVersioningService.MarginCompVersionManifest> replayManifests;
    private final SavingList<Object> outbox;
    private final SavingList<MarginVersioningService.AuditRecord> auditRecords;
    VersioningStore(VersioningSnapshot snapshot, Runnable save) {
      publishedVersions = SavingMap.of(save, snapshot.publishedVersions, v -> new MarginVersioningService.VersionKey(v.tenantId(), v.policyType(), v.policyId(), v.versionId()));
      derivedManifestCache = SavingMap.of(save, snapshot.derivedManifestCache, m -> new MarginVersioningService.ManifestCacheKey(m.tenantId(), m.scopeHash(), m.activeAtUtc(), m.configHash()));
      replayManifests = SavingMap.of(save, snapshot.replayManifests);
      outbox = SavingList.of(save, snapshot.outbox);
      auditRecords = SavingList.of(save, snapshot.auditRecords);
    }
    @Override public Map<MarginVersioningService.VersionKey, MarginVersioningService.PolicyVersionRef> publishedVersions() { return publishedVersions; }
    @Override public Map<MarginVersioningService.ManifestCacheKey, MarginVersioningService.MarginCompVersionManifest> derivedManifestCache() { return derivedManifestCache; }
    @Override public Map<String, MarginVersioningService.MarginCompVersionManifest> replayManifests() { return replayManifests; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<MarginVersioningService.AuditRecord> auditRecords() { return auditRecords; }
    VersioningSnapshot snapshot() { VersioningSnapshot s = new VersioningSnapshot(); s.publishedVersions = new ArrayList<>(publishedVersions.values()); s.derivedManifestCache = new ArrayList<>(derivedManifestCache.values()); s.replayManifests = new LinkedHashMap<>(replayManifests); s.outbox = new ArrayList<>(outbox); s.auditRecords = new ArrayList<>(auditRecords); return s; }
  }

  private static final class ProfitabilityStore implements ProfitabilityFloorService.Store {
    private final SavingMap<ProfitabilityFloorService.PolicyKey, ProfitabilityFloorService.ProfitabilityPolicy> policies;
    private final SavingMap<String, ProfitabilityFloorService.IdempotencyRecord> idempotencyReceipts;
    private final SavingList<Object> outbox;
    private final SavingList<ProfitabilityFloorService.AuditRecord> auditRecords;
    private final SavingList<ProfitabilityFloorService.ProfitabilityEvaluation> evaluations;
    ProfitabilityStore(ProfitabilitySnapshot snapshot, Runnable save) {
      policies = SavingMap.of(save, snapshot.policies, p -> new ProfitabilityFloorService.PolicyKey(p.tenantId(), p.policyId()));
      idempotencyReceipts = SavingMap.of(save, snapshot.idempotencyReceipts);
      outbox = SavingList.of(save, snapshot.outbox);
      auditRecords = SavingList.of(save, snapshot.auditRecords);
      evaluations = SavingList.of(save, snapshot.evaluations);
    }
    @Override public Map<ProfitabilityFloorService.PolicyKey, ProfitabilityFloorService.ProfitabilityPolicy> policies() { return policies; }
    @Override public Map<String, ProfitabilityFloorService.IdempotencyRecord> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<ProfitabilityFloorService.AuditRecord> auditRecords() { return auditRecords; }
    @Override public List<ProfitabilityFloorService.ProfitabilityEvaluation> evaluations() { return evaluations; }
    ProfitabilitySnapshot snapshot() { ProfitabilitySnapshot s = new ProfitabilitySnapshot(); s.policies = new ArrayList<>(policies.values()); s.idempotencyReceipts = new LinkedHashMap<>(idempotencyReceipts); s.outbox = new ArrayList<>(outbox); s.auditRecords = new ArrayList<>(auditRecords); s.evaluations = new ArrayList<>(evaluations); return s; }
  }

  private static final class LoStore implements LoCompensationService.Store {
    private final SavingMap<LoCompensationService.PlanKey, LoCompensationService.CompensationPlan> plans;
    private final SavingMap<String, LoCompensationService.IdempotencyRecord> idempotencyReceipts;
    private final SavingList<LoCompensationService.CompensationAssignment> assignments;
    private final SavingList<Object> outbox;
    private final SavingList<LoCompensationService.AuditRecord> auditRecords;
    private final SavingMap<String, String> resultVisibility;
    LoStore(LoSnapshot snapshot, Runnable save) {
      plans = SavingMap.of(save, snapshot.plans, p -> new LoCompensationService.PlanKey(p.tenantId(), p.planId()));
      idempotencyReceipts = SavingMap.of(save, snapshot.idempotencyReceipts);
      assignments = SavingList.of(save, snapshot.assignments);
      outbox = SavingList.of(save, snapshot.outbox);
      auditRecords = SavingList.of(save, snapshot.auditRecords);
      resultVisibility = SavingMap.of(save, snapshot.resultVisibility);
    }
    @Override public Map<LoCompensationService.PlanKey, LoCompensationService.CompensationPlan> plans() { return plans; }
    @Override public Map<String, LoCompensationService.IdempotencyRecord> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<LoCompensationService.CompensationAssignment> assignments() { return assignments; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<LoCompensationService.AuditRecord> auditRecords() { return auditRecords; }
    @Override public Map<String, String> resultVisibility() { return resultVisibility; }
    LoSnapshot snapshot() { LoSnapshot s = new LoSnapshot(); s.plans = new ArrayList<>(plans.values()); s.idempotencyReceipts = new LinkedHashMap<>(idempotencyReceipts); s.assignments = new ArrayList<>(assignments); s.outbox = new ArrayList<>(outbox); s.auditRecords = new ArrayList<>(auditRecords); s.resultVisibility = new LinkedHashMap<>(resultVisibility); return s; }
  }

  private static final class BrokerStore implements BrokerCompensationService.Store {
    private final SavingMap<BrokerCompensationService.PlanKey, BrokerCompensationService.BrokerCompensationPlan> plans;
    private final SavingMap<String, BrokerCompensationService.IdempotencyRecord> idempotencyReceipts;
    private final SavingList<BrokerCompensationService.BrokerCompensationAssignment> assignments;
    private final SavingList<Object> outbox;
    private final SavingList<BrokerCompensationService.AuditRecord> auditRecords;
    private final SavingMap<String, BrokerCompensationService.BrokerVisibilityPolicy> resultVisibility;
    BrokerStore(BrokerSnapshot snapshot, Runnable save) {
      plans = SavingMap.of(save, snapshot.plans, p -> new BrokerCompensationService.PlanKey(p.tenantId(), p.planId()));
      idempotencyReceipts = SavingMap.of(save, snapshot.idempotencyReceipts);
      assignments = SavingList.of(save, snapshot.assignments);
      outbox = SavingList.of(save, snapshot.outbox);
      auditRecords = SavingList.of(save, snapshot.auditRecords);
      resultVisibility = SavingMap.of(save, snapshot.resultVisibility);
    }
    @Override public Map<BrokerCompensationService.PlanKey, BrokerCompensationService.BrokerCompensationPlan> plans() { return plans; }
    @Override public Map<String, BrokerCompensationService.IdempotencyRecord> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<BrokerCompensationService.BrokerCompensationAssignment> assignments() { return assignments; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<BrokerCompensationService.AuditRecord> auditRecords() { return auditRecords; }
    @Override public Map<String, BrokerCompensationService.BrokerVisibilityPolicy> resultVisibility() { return resultVisibility; }
    BrokerSnapshot snapshot() { BrokerSnapshot s = new BrokerSnapshot(); s.plans = new ArrayList<>(plans.values()); s.idempotencyReceipts = new LinkedHashMap<>(idempotencyReceipts); s.assignments = new ArrayList<>(assignments); s.outbox = new ArrayList<>(outbox); s.auditRecords = new ArrayList<>(auditRecords); s.resultVisibility = new LinkedHashMap<>(resultVisibility); return s; }
  }

  private static final class GovernanceStore implements MarginGovernanceService.Store {
    private final SavingMap<MarginGovernanceService.ChangeKey, MarginGovernanceService.MarginGovernanceChangeRequest> changes;
    private final SavingMap<String, MarginGovernanceService.GovernanceReceipt> idempotencyReceipts;
    private final SavingList<Object> outbox;
    private final SavingList<MarginGovernanceService.AuditRecord> auditRecords;
    GovernanceStore(GovernanceSnapshot snapshot, Runnable save) {
      changes = SavingMap.of(save, snapshot.changes, c -> new MarginGovernanceService.ChangeKey(c.tenantId(), c.changeId()));
      idempotencyReceipts = SavingMap.of(save, snapshot.idempotencyReceipts);
      outbox = SavingList.of(save, snapshot.outbox);
      auditRecords = SavingList.of(save, snapshot.auditRecords);
    }
    @Override public Map<MarginGovernanceService.ChangeKey, MarginGovernanceService.MarginGovernanceChangeRequest> changes() { return changes; }
    @Override public Map<String, MarginGovernanceService.GovernanceReceipt> idempotencyReceipts() { return idempotencyReceipts; }
    @Override public List<Object> outbox() { return outbox; }
    @Override public List<MarginGovernanceService.AuditRecord> auditRecords() { return auditRecords; }
    GovernanceSnapshot snapshot() { GovernanceSnapshot s = new GovernanceSnapshot(); s.changes = new ArrayList<>(changes.values()); s.idempotencyReceipts = new LinkedHashMap<>(idempotencyReceipts); s.outbox = new ArrayList<>(outbox); s.auditRecords = new ArrayList<>(auditRecords); return s; }
  }

  private static final class ReplayStore implements MarginReplayService.Store {
    private final SavingMap<MarginReplayService.FixtureKey, MarginReplayService.ReplayFixture> fixtureCatalog;
    private final SavingMap<String, MarginReplayService.ReplayRun> replayRuns;
    private final SavingList<MarginReplayService.DecisionReplayedEvent> outbox;
    private final SavingList<MarginReplayService.AuditEvidence> auditPackages;
    ReplayStore(ReplaySnapshot snapshot, Runnable save) {
      fixtureCatalog = SavingMap.of(save, snapshot.fixtureCatalog, f -> new MarginReplayService.FixtureKey(f.tenantId(), f.fixtureId()));
      replayRuns = SavingMap.of(save, snapshot.replayRuns);
      outbox = SavingList.of(save, snapshot.outbox);
      auditPackages = SavingList.of(save, snapshot.auditPackages);
    }
    @Override public Map<MarginReplayService.FixtureKey, MarginReplayService.ReplayFixture> fixtureCatalog() { return fixtureCatalog; }
    @Override public Map<String, MarginReplayService.ReplayRun> replayRuns() { return replayRuns; }
    @Override public List<MarginReplayService.DecisionReplayedEvent> outbox() { return outbox; }
    @Override public List<MarginReplayService.AuditEvidence> auditPackages() { return auditPackages; }
    ReplaySnapshot snapshot() { ReplaySnapshot s = new ReplaySnapshot(); s.fixtureCatalog = new ArrayList<>(fixtureCatalog.values()); s.replayRuns = new LinkedHashMap<>(replayRuns); s.outbox = new ArrayList<>(outbox); s.auditPackages = new ArrayList<>(auditPackages); return s; }
  }

  private static final class SavingMap<K, V> extends LinkedHashMap<K, V> {
    private final Runnable save;
    private SavingMap(Runnable save) { this.save = Objects.requireNonNull(save); }
    static <K, V> SavingMap<K, V> of(Runnable save, Map<K, V> values) { SavingMap<K, V> map = new SavingMap<>(save); if (values != null) { map.putAllSilently(values); } return map; }
    static <K, V> SavingMap<K, V> of(Runnable save, List<V> values, java.util.function.Function<V, K> key) { SavingMap<K, V> map = new SavingMap<>(save); if (values != null) { for (V value : values) { map.superPut(key.apply(value), value); } } return map; }
    private void putAllSilently(Map<K, V> values) { values.forEach(this::superPut); }
    private void superPut(K key, V value) { super.put(key, value); }
    @Override public V put(K key, V value) { V previous = super.put(key, value); save.run(); return previous; }
    @Override public V remove(Object key) { V previous = super.remove(key); save.run(); return previous; }
    @Override public void clear() { super.clear(); save.run(); }
    @Override public void putAll(Map<? extends K, ? extends V> m) { super.putAll(m); save.run(); }
  }

  private static final class SavingList<V> extends ArrayList<V> {
    private final Runnable save;
    private SavingList(Runnable save) { this.save = Objects.requireNonNull(save); }
    static <V> SavingList<V> of(Runnable save, List<V> values) { SavingList<V> list = new SavingList<>(save); if (values != null) { list.addAllSilently(values); } return list; }
    private void addAllSilently(List<V> values) { super.addAll(values); }
    @Override public boolean add(V value) { boolean changed = super.add(value); if (changed) save.run(); return changed; }
    @Override public void add(int index, V element) { super.add(index, element); save.run(); }
    @Override public boolean addAll(java.util.Collection<? extends V> c) { boolean changed = super.addAll(c); if (changed) save.run(); return changed; }
    @Override public boolean remove(Object o) { boolean changed = super.remove(o); if (changed) save.run(); return changed; }
    @Override public V remove(int index) { V removed = super.remove(index); save.run(); return removed; }
    @Override public void clear() { super.clear(); save.run(); }
    @Override public V set(int index, V element) { V previous = super.set(index, element); save.run(); return previous; }
  }
}
