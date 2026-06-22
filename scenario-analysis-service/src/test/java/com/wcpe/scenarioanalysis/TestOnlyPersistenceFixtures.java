package com.wcpe.scenarioanalysis;

import com.wcpe.scenarioanalysis.BatchSensitivityGridService.BatchGridRepository;
import com.wcpe.scenarioanalysis.BatchSensitivityGridService.BatchGridResponse;
import com.wcpe.scenarioanalysis.BatchSensitivityGridService.ProductEvent;
import com.wcpe.scenarioanalysis.BatchSensitivityGridService.StoredBatchGrid;
import com.wcpe.scenarioanalysis.FicoSensitivityService.FicoSensitivityRepository;
import com.wcpe.scenarioanalysis.FicoSensitivityService.StoredFicoSensitivityRun;
import com.wcpe.scenarioanalysis.LockPeriodComparisonService.LockPeriodComparisonRepository;
import com.wcpe.scenarioanalysis.LockPeriodComparisonService.StoredLockPeriodComparisonRun;
import com.wcpe.scenarioanalysis.LtvSensitivityService.LtvSensitivityRepository;
import com.wcpe.scenarioanalysis.LtvSensitivityService.StoredLtvSensitivityRun;
import com.wcpe.scenarioanalysis.ProductComparisonService.ProductComparisonEvent;
import com.wcpe.scenarioanalysis.ProductComparisonService.ProductComparisonRepository;
import com.wcpe.scenarioanalysis.ProductComparisonService.StoredProductComparisonRun;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.AnalysisResponse;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.NoteHistoryEntry;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.SavedAnalysisEvent;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.SavedAnalysisRepository;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.SelectionAvailabilityChecker;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.SelectionAvailabilityResult;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.StoredAnalysis;
import com.wcpe.scenarioanalysis.ScenarioCloneService.ScenarioLineageRecord;
import com.wcpe.scenarioanalysis.ScenarioCloneService.ScenarioLineageRepository;
import com.wcpe.scenarioanalysis.WhatIfExportService.ExportEvent;
import com.wcpe.scenarioanalysis.WhatIfExportService.ExportRepository;
import com.wcpe.scenarioanalysis.WhatIfExportService.ExportResponse;
import com.wcpe.scenarioanalysis.WhatIfExportService.ExportStorage;
import com.wcpe.scenarioanalysis.WhatIfExportService.StoredExport;
import com.wcpe.scenarioanalysis.WhatIfGuardrailService.GuardrailEvent;
import com.wcpe.scenarioanalysis.WhatIfGuardrailService.GuardrailPolicy;
import com.wcpe.scenarioanalysis.WhatIfGuardrailService.StoredDecision;
import com.wcpe.scenarioanalysis.WhatIfGuardrailService.WhatIfGuardrailRepository;
import com.wcpe.scenarioanalysis.WhatIfReplayService.ReplayRepository;
import com.wcpe.scenarioanalysis.WhatIfReplayService.StoredReplay;
import com.wcpe.scenarioanalysis.WhatIfVariantService.StoredVariant;
import com.wcpe.scenarioanalysis.WhatIfVariantService.WhatIfVariantRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class TestOnlyInMemoryScenarioLineageRepository implements ScenarioLineageRepository {
  private final Map<String, ScenarioLineageRecord> recordsByVariantId = new ConcurrentHashMap<>();

  @Override
  public void save(ScenarioLineageRecord lineage) {
    recordsByVariantId.put(lineage.variantScenarioId(), lineage);
  }

  @Override
  public Optional<ScenarioLineageRecord> findByVariantScenarioId(String variantScenarioId) {
    return Optional.ofNullable(recordsByVariantId.get(variantScenarioId));
  }

  public int size() {
    return recordsByVariantId.size();
  }
}

class TestOnlyInMemoryWhatIfVariantRepository implements WhatIfVariantRepository {
  private final Map<String, StoredVariant> variantsByTenantAndIdempotency = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredVariant> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
    return Optional.ofNullable(variantsByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
  }

  @Override
  public void save(StoredVariant variant) {
    variantsByTenantAndIdempotency.put(key(variant.tenantId(), variant.idempotencyKeyHash()), variant);
  }

  public int size() {
    return variantsByTenantAndIdempotency.size();
  }

  private static String key(String tenantId, String idempotencyKeyHash) {
    return tenantId + ':' + idempotencyKeyHash;
  }
}

class TestOnlyInMemoryFicoSensitivityRepository implements FicoSensitivityRepository {
  private final Map<String, StoredFicoSensitivityRun> runsByTenantAndIdempotency = new ConcurrentHashMap<>();
  private final Map<String, StoredFicoSensitivityRun> runsByTenantAndAnalysisId = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredFicoSensitivityRun> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
    return Optional.ofNullable(runsByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
  }

  @Override
  public Optional<StoredFicoSensitivityRun> findByAnalysisId(String tenantId, UUID analysisId) {
    return Optional.ofNullable(runsByTenantAndAnalysisId.get(key(tenantId, analysisId.toString())));
  }

  @Override
  public void save(StoredFicoSensitivityRun run) {
    runsByTenantAndIdempotency.put(key(run.tenantId(), run.idempotencyKeyHash()), run);
    runsByTenantAndAnalysisId.put(key(run.tenantId(), run.analysisId().toString()), run);
  }

  int size() {
    return runsByTenantAndAnalysisId.size();
  }

  private static String key(String tenantId, String id) {
    return tenantId + ':' + id;
  }
}

class TestOnlyInMemoryLtvSensitivityRepository implements LtvSensitivityRepository {
  private final Map<String, StoredLtvSensitivityRun> runsByTenantAndIdempotency = new ConcurrentHashMap<>();
  private final Map<String, StoredLtvSensitivityRun> runsByTenantAndAnalysisId = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredLtvSensitivityRun> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
    return Optional.ofNullable(runsByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
  }

  @Override
  public Optional<StoredLtvSensitivityRun> findByAnalysisId(String tenantId, UUID analysisId) {
    return Optional.ofNullable(runsByTenantAndAnalysisId.get(key(tenantId, analysisId.toString())));
  }

  @Override
  public void save(StoredLtvSensitivityRun run) {
    runsByTenantAndIdempotency.put(key(run.tenantId(), run.idempotencyKeyHash()), run);
    runsByTenantAndAnalysisId.put(key(run.tenantId(), run.analysisId().toString()), run);
  }

  int size() {
    return runsByTenantAndAnalysisId.size();
  }

  private static String key(String tenantId, String id) {
    return tenantId + ':' + id;
  }
}

class TestOnlyInMemoryLockPeriodComparisonRepository implements LockPeriodComparisonRepository {
  private final Map<String, StoredLockPeriodComparisonRun> runsByTenantAndIdempotency = new ConcurrentHashMap<>();
  private final Map<String, StoredLockPeriodComparisonRun> runsByTenantAndAnalysisId = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredLockPeriodComparisonRun> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
    return Optional.ofNullable(runsByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
  }

  @Override
  public Optional<StoredLockPeriodComparisonRun> findByAnalysisId(String tenantId, UUID analysisId) {
    return Optional.ofNullable(runsByTenantAndAnalysisId.get(key(tenantId, analysisId.toString())));
  }

  @Override
  public void save(StoredLockPeriodComparisonRun run) {
    runsByTenantAndIdempotency.put(key(run.tenantId(), run.idempotencyKeyHash()), run);
    runsByTenantAndAnalysisId.put(key(run.tenantId(), run.analysisId().toString()), run);
  }

  int size() {
    return runsByTenantAndAnalysisId.size();
  }

  private static String key(String tenantId, String id) {
    return tenantId + ':' + id;
  }
}

class TestOnlyInMemoryProductComparisonRepository implements ProductComparisonRepository {
  private final Map<String, StoredProductComparisonRun> runsByTenantAndIdempotency = new ConcurrentHashMap<>();
  private final Map<String, StoredProductComparisonRun> runsByTenantAndAnalysisId = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredProductComparisonRun> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
    return Optional.ofNullable(runsByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
  }

  @Override
  public Optional<StoredProductComparisonRun> findByAnalysisId(String tenantId, UUID analysisId) {
    return Optional.ofNullable(runsByTenantAndAnalysisId.get(key(tenantId, analysisId.toString())));
  }

  @Override
  public void save(StoredProductComparisonRun run) {
    runsByTenantAndIdempotency.put(key(run.tenantId(), run.idempotencyKeyHash()), run);
    runsByTenantAndAnalysisId.put(key(run.tenantId(), run.analysisId().toString()), run);
  }

  @Override
  public void appendEvent(String tenantId, UUID analysisId, ProductComparisonEvent event) {
    StoredProductComparisonRun run = runsByTenantAndAnalysisId.get(key(tenantId, analysisId.toString()));
    if (run == null) {
      throw new ProductComparisonService.NotFoundException("product comparison run was not found");
    }
    List<ProductComparisonEvent> events = new ArrayList<>(run.events());
    events.add(event);
    save(new StoredProductComparisonRun(
        run.tenantId(), run.analysisId(), run.requestHash(), run.idempotencyKeyHash(), run.response(), List.copyOf(events), run.createdAt()));
  }

  int size() {
    return runsByTenantAndAnalysisId.size();
  }

  Optional<StoredProductComparisonRun> firstRun() {
    return runsByTenantAndAnalysisId.values().stream().findFirst();
  }

  private static String key(String tenantId, String id) {
    return tenantId + ':' + id;
  }
}

class TestOnlyInMemoryBatchGridRepository implements BatchGridRepository {
  private final Map<String, StoredBatchGrid> gridsByTenantAndIdempotency = new ConcurrentHashMap<>();
  private final Map<String, StoredBatchGrid> gridsByTenantAndGridId = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredBatchGrid> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
    return Optional.ofNullable(gridsByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
  }

  @Override
  public Optional<StoredBatchGrid> findByGridId(String tenantId, UUID gridId) {
    return Optional.ofNullable(gridsByTenantAndGridId.get(key(tenantId, gridId.toString())));
  }

  @Override
  public void save(StoredBatchGrid grid) {
    gridsByTenantAndIdempotency.put(key(grid.tenantId(), grid.idempotencyKeyHash()), grid);
    gridsByTenantAndGridId.put(key(grid.tenantId(), grid.gridId().toString()), grid);
  }

  @Override
  public void update(StoredBatchGrid current, BatchGridResponse response, ProductEvent event) {
    List<ProductEvent> events = new ArrayList<>(current.events());
    events.add(event);
    save(new StoredBatchGrid(current.tenantId(), current.gridId(), current.requestHash(), current.idempotencyKeyHash(), response, List.copyOf(events), current.createdAt()));
  }

  int size() {
    return gridsByTenantAndGridId.size();
  }

  private static String key(String tenantId, String id) {
    return tenantId + ':' + id;
  }
}

class InMemorySelectionAvailabilityChecker implements SelectionAvailabilityChecker {
  private final Set<String> missingVariants = ConcurrentHashMap.newKeySet();
  private final Set<String> expiredVariants = ConcurrentHashMap.newKeySet();
  private final Set<String> missingGridCells = ConcurrentHashMap.newKeySet();
  private final Set<String> expiredGridCells = ConcurrentHashMap.newKeySet();

  public void markVariantMissing(String tenantId, String sourceQuoteId, String variantId) {
    missingVariants.add(selectionKey(tenantId, sourceQuoteId, variantId));
  }

  public void markVariantExpired(String tenantId, String sourceQuoteId, String variantId) {
    expiredVariants.add(selectionKey(tenantId, sourceQuoteId, variantId));
  }

  public void markGridCellMissing(String tenantId, String sourceQuoteId, String gridCellId) {
    missingGridCells.add(selectionKey(tenantId, sourceQuoteId, gridCellId));
  }

  public void markGridCellExpired(String tenantId, String sourceQuoteId, String gridCellId) {
    expiredGridCells.add(selectionKey(tenantId, sourceQuoteId, gridCellId));
  }

  @Override
  public SelectionAvailabilityResult validateSelections(String tenantId, String sourceQuoteId, List<String> variantIds, List<String> gridCellIds) {
    return new SelectionAvailabilityResult(
        unavailable(tenantId, sourceQuoteId, variantIds, missingVariants),
        unavailable(tenantId, sourceQuoteId, variantIds, expiredVariants),
        unavailable(tenantId, sourceQuoteId, gridCellIds, missingGridCells),
        unavailable(tenantId, sourceQuoteId, gridCellIds, expiredGridCells));
  }

  private static List<String> unavailable(String tenantId, String sourceQuoteId, List<String> ids, Set<String> unavailableKeys) {
    return ids.stream()
        .filter(id -> unavailableKeys.contains(selectionKey(tenantId, sourceQuoteId, id)))
        .toList();
  }

  private static String selectionKey(String tenantId, String sourceQuoteId, String id) {
    return tenantId + ':' + sourceQuoteId + ':' + id;
  }
}

class TestOnlyInMemorySavedAnalysisRepository implements SavedAnalysisRepository {
  private final Map<String, StoredAnalysis> analysesByTenantAndId = new ConcurrentHashMap<>();
  private final Map<String, StoredAnalysis> analysesByTenantAndIdempotency = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredAnalysis> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
    return Optional.ofNullable(analysesByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
  }

  @Override
  public Optional<StoredAnalysis> findByAnalysisId(String tenantId, UUID analysisId) {
    return Optional.ofNullable(analysesByTenantAndId.get(key(tenantId, analysisId.toString())));
  }

  @Override
  public List<StoredAnalysis> findByTenant(String tenantId) {
    return analysesByTenantAndId.values().stream()
        .filter(analysis -> analysis.tenantId().equals(tenantId))
        .toList();
  }

  @Override
  public boolean activeNameExists(String tenantId, String sourceQuoteId, String createdBy, String name, UUID excludingAnalysisId) {
    String normalizedName = name.trim().toLowerCase();
    return analysesByTenantAndId.values().stream()
        .filter(analysis -> analysis.tenantId().equals(tenantId))
        .filter(analysis -> excludingAnalysisId == null || !analysis.analysisId().equals(excludingAnalysisId))
        .filter(analysis -> "SAVED".equals(analysis.response().status()))
        .anyMatch(analysis -> analysis.createdBy().equals(createdBy)
            && analysis.response().sourceQuoteId().equals(sourceQuoteId)
            && analysis.response().name().trim().toLowerCase().equals(normalizedName));
  }

  @Override
  public void save(StoredAnalysis analysis) {
    analysesByTenantAndId.put(key(analysis.tenantId(), analysis.analysisId().toString()), analysis);
    analysesByTenantAndIdempotency.put(key(analysis.tenantId(), analysis.idempotencyKeyHash()), analysis);
  }

  @Override
  public void update(StoredAnalysis current, AnalysisResponse response, String notesHash, List<NoteHistoryEntry> noteHistory, SavedAnalysisEvent event) {
    List<SavedAnalysisEvent> events = new ArrayList<>(current.events());
    events.add(event);
    StoredAnalysis updated = new StoredAnalysis(
        current.tenantId(),
        current.analysisId(),
        current.requestHash(),
        current.idempotencyKeyHash(),
        current.createdBy(),
        response,
        notesHash,
        noteHistory,
        List.copyOf(events),
        current.createdAt());
    analysesByTenantAndId.put(key(updated.tenantId(), updated.analysisId().toString()), updated);
    analysesByTenantAndIdempotency.put(key(updated.tenantId(), updated.idempotencyKeyHash()), updated);
  }

  public int size() {
    return analysesByTenantAndId.size();
  }

  private static String key(String tenantId, String id) {
    return tenantId + ':' + id;
  }
}

class TestOnlyInMemoryExportRepository implements ExportRepository {
  private final Map<String, StoredExport> exportsByTenantAndId = new ConcurrentHashMap<>();
  private final Map<String, StoredExport> exportsByTenantAndIdempotency = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredExport> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
    return Optional.ofNullable(exportsByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
  }

  @Override
  public Optional<StoredExport> findByExportId(String tenantId, UUID exportId) {
    return Optional.ofNullable(exportsByTenantAndId.get(key(tenantId, exportId.toString())));
  }

  @Override
  public void save(StoredExport export) {
    exportsByTenantAndId.put(key(export.tenantId(), export.exportId().toString()), export);
    exportsByTenantAndIdempotency.put(key(export.tenantId(), export.idempotencyKeyHash()), export);
  }

  @Override
  public void update(StoredExport current, ExportResponse response, ExportEvent event) {
    List<ExportEvent> events = new ArrayList<>(current.events());
    events.add(event);
    StoredExport updated = new StoredExport(
        current.tenantId(),
        current.exportId(),
        current.requestHash(),
        current.idempotencyKeyHash(),
        current.createdBy(),
        response,
        current.content(),
        List.copyOf(events),
        current.createdAt());
    exportsByTenantAndId.put(key(updated.tenantId(), updated.exportId().toString()), updated);
    exportsByTenantAndIdempotency.put(key(updated.tenantId(), updated.idempotencyKeyHash()), updated);
  }

  public int size() {
    return exportsByTenantAndId.size();
  }

  private static String key(String tenantId, String id) {
    return tenantId + ':' + id;
  }
}

class TestOnlyInMemoryExportStorage implements ExportStorage {
  private final Map<String, byte[]> contentByUri = new LinkedHashMap<>();

  @Override
  public String store(String tenantId, UUID exportId, byte[] content, String format) {
    String uri = "memory://what-if-export/" + tenantId + '/' + exportId + '.' + format.toLowerCase();
    contentByUri.put(uri, content.clone());
    return uri;
  }

  @Override
  public Optional<byte[]> read(String storageUri) {
    byte[] content = contentByUri.get(storageUri);
    return content == null ? Optional.empty() : Optional.of(content.clone());
  }
}

class TestOnlyInMemoryReplayRepository implements ReplayRepository {
  private final Map<String, StoredReplay> replaysByTenantAndId = new ConcurrentHashMap<>();
  private final Map<String, StoredReplay> replaysByTenantAndIdempotency = new ConcurrentHashMap<>();

  @Override
  public Optional<StoredReplay> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
    return Optional.ofNullable(replaysByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
  }

  @Override
  public Optional<StoredReplay> findByReplayId(String tenantId, UUID replayId) {
    return Optional.ofNullable(replaysByTenantAndId.get(key(tenantId, replayId.toString())));
  }

  @Override
  public void save(StoredReplay replay) {
    replaysByTenantAndId.put(key(replay.tenantId(), replay.replayId().toString()), replay);
    replaysByTenantAndIdempotency.put(key(replay.tenantId(), replay.idempotencyKeyHash()), replay);
  }

  public int size() {
    return replaysByTenantAndId.size();
  }

  private static String key(String tenantId, String id) {
    return tenantId + ':' + id;
  }
}

class TestOnlyInMemoryWhatIfGuardrailRepository implements WhatIfGuardrailRepository {
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
