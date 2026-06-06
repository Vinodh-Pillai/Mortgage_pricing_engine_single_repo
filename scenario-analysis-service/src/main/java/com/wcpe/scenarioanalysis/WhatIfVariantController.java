package com.wcpe.scenarioanalysis;

import com.wcpe.scenarioanalysis.WhatIfVariantService.CreateVariantCommand;
import com.wcpe.scenarioanalysis.WhatIfVariantService.CreateVariantResponse;
import com.wcpe.scenarioanalysis.WhatIfVariantService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.WhatIfVariantService.UnsupportedFieldException;
import com.wcpe.scenarioanalysis.WhatIfVariantService.ValidationException;
import com.wcpe.scenarioanalysis.WhatIfVariantService.VariantChange;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WhatIfVariantController {
  private final WhatIfVariantService service;
  private final FicoSensitivityService ficoSensitivityService;
  private final LtvSensitivityService ltvSensitivityService;
  private final LockPeriodComparisonService lockPeriodComparisonService;
  private final ProductComparisonService productComparisonService;
  private final BatchSensitivityGridService batchSensitivityGridService;
  private final SavedWhatIfAnalysisService savedWhatIfAnalysisService;
  private final WhatIfExportService whatIfExportService;
  private final WhatIfReplayService whatIfReplayService;
  private final WhatIfGuardrailService whatIfGuardrailService;

  public WhatIfVariantController(
      WhatIfVariantService service,
      FicoSensitivityService ficoSensitivityService,
      LtvSensitivityService ltvSensitivityService,
      LockPeriodComparisonService lockPeriodComparisonService,
      ProductComparisonService productComparisonService,
      BatchSensitivityGridService batchSensitivityGridService,
      SavedWhatIfAnalysisService savedWhatIfAnalysisService,
      WhatIfExportService whatIfExportService,
      WhatIfReplayService whatIfReplayService,
      WhatIfGuardrailService whatIfGuardrailService) {
    this.service = service;
    this.ficoSensitivityService = ficoSensitivityService;
    this.ltvSensitivityService = ltvSensitivityService;
    this.lockPeriodComparisonService = lockPeriodComparisonService;
    this.productComparisonService = productComparisonService;
    this.batchSensitivityGridService = batchSensitivityGridService;
    this.savedWhatIfAnalysisService = savedWhatIfAnalysisService;
    this.whatIfExportService = whatIfExportService;
    this.whatIfReplayService = whatIfReplayService;
    this.whatIfGuardrailService = whatIfGuardrailService;
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/scenarios/{sourceQuoteId}/variants")
  public ResponseEntity<CreateVariantResponse> createVariant(
      @PathVariable String tenantId,
      @PathVariable String sourceQuoteId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody CreateVariantRequest request) {
    enforceGuardrails(tenantId, "CREATE_VARIANT", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        Map.of("sourceTenantId", tenantId));
    CreateVariantResponse response = service.createVariant(new CreateVariantCommand(
        tenantId,
        sourceQuoteId,
        request.variantName(),
        request.reasonCode(),
        request.sourceQuoteVersion(),
        request.pricingAsOf(),
        request.changes(),
        idempotencyKey,
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/scenarios/{sourceQuoteId}/fico-sensitivity")
  public ResponseEntity<FicoSensitivityService.FicoSensitivityResponse> createFicoSensitivityRun(
      @PathVariable String tenantId,
      @PathVariable String sourceQuoteId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody FicoSensitivityRequest request) {
    enforceGuardrails(tenantId, "RUN_SENSITIVITY", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        Map.of("sourceTenantId", tenantId, "maxCellsConfigured", "true"));
    var response = ficoSensitivityService.createRun(new FicoSensitivityService.FicoSensitivityCommand(
        tenantId,
        sourceQuoteId,
        request.sourceQuoteVersion(),
        request.sourceFico(),
        request.baseVariantId(),
        request.scores(),
        request.includeIneligible(),
        request.pricingAsOf(),
        idempotencyKey,
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/fico-sensitivity/{analysisId}")
  public FicoSensitivityService.FicoSensitivityResponse getFicoSensitivityRun(
      @PathVariable String tenantId,
      @PathVariable UUID analysisId) {
    return ficoSensitivityService.getRun(tenantId, analysisId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/scenarios/{sourceQuoteId}/ltv-sensitivity")
  public ResponseEntity<LtvSensitivityService.LtvSensitivityResponse> createLtvSensitivityRun(
      @PathVariable String tenantId,
      @PathVariable String sourceQuoteId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody LtvSensitivityRequest request) {
    enforceGuardrails(tenantId, "RUN_SENSITIVITY", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        Map.of("sourceTenantId", tenantId, "maxCellsConfigured", "true"));
    var response = ltvSensitivityService.createRun(new LtvSensitivityService.LtvSensitivityCommand(
        tenantId,
        sourceQuoteId,
        request.sourceQuoteVersion(),
        request.mode(),
        request.values(),
        request.propertyValue(),
        request.purchasePrice(),
        request.currentLoanAmount(),
        request.subordinateLienAmount(),
        request.includeMiEstimate(),
        request.pricingAsOf(),
        idempotencyKey,
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/ltv-sensitivity/{analysisId}")
  public LtvSensitivityService.LtvSensitivityResponse getLtvSensitivityRun(
      @PathVariable String tenantId,
      @PathVariable UUID analysisId) {
    return ltvSensitivityService.getRun(tenantId, analysisId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/what-if/config/lock-periods")
  public LockPeriodComparisonService.LockPeriodConfigResponse getLockPeriodConfig(
      @PathVariable String tenantId,
      @RequestParam(required = false) String productId,
      @RequestParam(required = false) String investorId,
      @RequestParam(required = false) String channel) {
    return lockPeriodComparisonService.getConfig(tenantId, productId, investorId, channel);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/scenarios/{sourceQuoteId}/lock-period-comparison")
  public ResponseEntity<LockPeriodComparisonService.LockPeriodComparisonResponse> createLockPeriodComparisonRun(
      @PathVariable String tenantId,
      @PathVariable String sourceQuoteId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody LockPeriodComparisonRequest request) {
    enforceGuardrails(tenantId, "RUN_SENSITIVITY", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        Map.of("sourceTenantId", tenantId, "maxCellsConfigured", "true"));
    var response = lockPeriodComparisonService.createRun(new LockPeriodComparisonService.LockPeriodComparisonCommand(
        tenantId,
        sourceQuoteId,
        request.sourceQuoteVersion(),
        request.baseVariantId(),
        request.lockPeriods(),
        request.lockStartDate(),
        request.includeExtensionEstimate(),
        request.pricingAsOf(),
        idempotencyKey,
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/lock-period-comparison/{analysisId}")
  public LockPeriodComparisonService.LockPeriodComparisonResponse getLockPeriodComparisonRun(
      @PathVariable String tenantId,
      @PathVariable UUID analysisId) {
    return lockPeriodComparisonService.getRun(tenantId, analysisId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/what-if/config/comparable-products")
  public ProductComparisonService.ComparableProductsConfigResponse getComparableProductsConfig(
      @PathVariable String tenantId,
      @RequestParam(required = false) String sourceQuoteId,
      @RequestParam(required = false) String channel,
      @RequestParam(required = false) String productFamily,
      @RequestParam(required = false) String investorId) {
    return productComparisonService.getComparableProductsConfig(tenantId, sourceQuoteId, channel, productFamily, investorId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/scenarios/{sourceQuoteId}/product-comparison")
  public ResponseEntity<ProductComparisonService.ProductComparisonResponse> createProductComparisonRun(
      @PathVariable String tenantId,
      @PathVariable String sourceQuoteId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody ProductComparisonRequest request) {
    enforceGuardrails(tenantId, "RUN_SENSITIVITY", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        Map.of("sourceTenantId", tenantId, "maxCellsConfigured", "true"));
    var response = productComparisonService.createRun(new ProductComparisonService.ProductComparisonCommand(
        tenantId,
        sourceQuoteId,
        request.sourceQuoteVersion(),
        request.candidateProductIds(),
        request.investorIds(),
        request.includeIneligible(),
        request.baselineProductId(),
        request.pricingAsOf(),
        idempotencyKey,
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/product-comparison/{analysisId}")
  public ProductComparisonService.ProductComparisonResponse getProductComparisonRun(
      @PathVariable String tenantId,
      @PathVariable UUID analysisId) {
    return productComparisonService.getRun(tenantId, analysisId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/product-comparison/{analysisId}/variants/{variantId}/promote")
  public ResponseEntity<ProductComparisonService.ProductPromotionResponse> promoteProductComparisonVariant(
      @PathVariable String tenantId,
      @PathVariable UUID analysisId,
      @PathVariable UUID variantId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody(required = false) ProductPromotionRequest request) {
    String requestActor = request == null ? null : request.actorId();
    enforceGuardrails(tenantId, "PROMOTE_VARIANT", actorId == null || actorId.isBlank() ? requestActor : actorId,
        Map.of("sourceTenantId", tenantId));
    var response = productComparisonService.promoteVariant(new ProductComparisonService.ProductPromotionCommand(
        tenantId,
        analysisId,
        variantId,
        idempotencyKey,
        actorId == null || actorId.isBlank() ? requestActor : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/scenarios/{sourceQuoteId}/batch-grids")
  public ResponseEntity<BatchSensitivityGridService.BatchGridResponse> createBatchSensitivityGrid(
      @PathVariable String tenantId,
      @PathVariable String sourceQuoteId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody BatchGridRequest request) {
    enforceGuardrails(tenantId, "RUN_SENSITIVITY", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        request.maxCells() == null ? Map.of("sourceTenantId", tenantId) : Map.of("sourceTenantId", tenantId, "maxCellsConfigured", "true"));
    var response = batchSensitivityGridService.createGrid(new BatchSensitivityGridService.BatchGridCommand(
        tenantId,
        sourceQuoteId,
        request.sourceQuoteVersion(),
        request.gridName(),
        request.axes(),
        request.includeIneligible(),
        request.maxCells(),
        request.pricingAsOf(),
        idempotencyKey,
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/what-if/batch-grids/{gridId}")
  public BatchSensitivityGridService.BatchGridResponse getBatchSensitivityGrid(
      @PathVariable String tenantId,
      @PathVariable UUID gridId) {
    return batchSensitivityGridService.getGrid(tenantId, gridId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/batch-grids/{gridId}:pause")
  public BatchSensitivityGridService.BatchGridResponse pauseBatchSensitivityGrid(
      @PathVariable String tenantId,
      @PathVariable UUID gridId) {
    enforceGuardrails(tenantId, "RUN_SENSITIVITY", "system", Map.of("sourceTenantId", tenantId, "maxCellsConfigured", "true"));
    return batchSensitivityGridService.pauseGrid(tenantId, gridId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/batch-grids/{gridId}:resume")
  public BatchSensitivityGridService.BatchGridResponse resumeBatchSensitivityGrid(
      @PathVariable String tenantId,
      @PathVariable UUID gridId) {
    enforceGuardrails(tenantId, "RUN_SENSITIVITY", "system", Map.of("sourceTenantId", tenantId, "maxCellsConfigured", "true"));
    return batchSensitivityGridService.resumeGrid(tenantId, gridId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/batch-grids/{gridId}:cancel")
  public BatchSensitivityGridService.BatchGridResponse cancelBatchSensitivityGrid(
      @PathVariable String tenantId,
      @PathVariable UUID gridId) {
    enforceGuardrails(tenantId, "RUN_SENSITIVITY", "system", Map.of("sourceTenantId", tenantId, "maxCellsConfigured", "true"));
    return batchSensitivityGridService.cancelGrid(tenantId, gridId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/batch-grids/{gridId}:retry-failed")
  public BatchSensitivityGridService.BatchGridResponse retryFailedBatchSensitivityGrid(
      @PathVariable String tenantId,
      @PathVariable UUID gridId) {
    enforceGuardrails(tenantId, "RUN_SENSITIVITY", "system", Map.of("sourceTenantId", tenantId, "maxCellsConfigured", "true"));
    return batchSensitivityGridService.retryFailed(tenantId, gridId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/analyses")
  public ResponseEntity<SavedWhatIfAnalysisService.AnalysisResponse> saveAnalysis(
      @PathVariable String tenantId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody SaveAnalysisRequest request) {
    enforceGuardrails(tenantId, "SAVE_ANALYSIS", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        Map.of("sourceTenantId", tenantId));
    var response = savedWhatIfAnalysisService.saveAnalysis(new SavedWhatIfAnalysisService.CreateAnalysisCommand(
        tenantId,
        request.name(),
        request.reasonCode(),
        request.sourceQuoteId(),
        request.sourceQuoteVersion(),
        request.pricingConfigVersion(),
        request.selectedVariantIds(),
        request.selectedGridCellIds(),
        request.notes(),
        request.tags(),
        request.visibility(),
        request.retentionCategory(),
        request.linkToQuoteDecision(),
        idempotencyKey,
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PatchMapping("/api/v1/tenants/{tenantId}/what-if/analyses/{analysisId}")
  public SavedWhatIfAnalysisService.AnalysisResponse patchAnalysis(
      @PathVariable String tenantId,
      @PathVariable UUID analysisId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody PatchAnalysisRequest request) {
    enforceGuardrails(tenantId, "SAVE_ANALYSIS", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        Map.of("sourceTenantId", tenantId));
    return savedWhatIfAnalysisService.patchAnalysis(new SavedWhatIfAnalysisService.PatchAnalysisCommand(
        tenantId,
        analysisId,
        parseVersion(ifMatch),
        request.name(),
        request.notes(),
        request.tags(),
        request.visibility(),
        request.status(),
        request.sharePermissions(),
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
  }

  @GetMapping("/api/v1/tenants/{tenantId}/what-if/analyses")
  public SavedWhatIfAnalysisService.AnalysisSearchResponse searchAnalyses(
      @PathVariable String tenantId,
      @RequestParam(required = false) String sourceQuoteId,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String status) {
    return savedWhatIfAnalysisService.searchAnalyses(tenantId, sourceQuoteId, tag, status);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/what-if/analyses/{analysisId}")
  public SavedWhatIfAnalysisService.AnalysisResponse getAnalysis(
      @PathVariable String tenantId,
      @PathVariable UUID analysisId,
      @RequestParam(required = false) Integer currentSourceQuoteVersion,
      @RequestParam(required = false) String currentPricingConfigVersion) {
    return savedWhatIfAnalysisService.getAnalysis(tenantId, analysisId, currentSourceQuoteVersion, currentPricingConfigVersion);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/exports")
  public ResponseEntity<WhatIfExportService.ExportResponse> createWhatIfExport(
      @PathVariable String tenantId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody WhatIfExportRequest request) {
    enforceGuardrails(tenantId, "EXPORT", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        Map.of("sourceTenantId", tenantId, "recipientType", request.recipientType() == null ? "" : request.recipientType()));
    var response = whatIfExportService.createExport(new WhatIfExportService.CreateExportCommand(
        tenantId,
        request.sourceType(),
        request.sourceId(),
        request.rowIds(),
        request.format(),
        request.recipientType(),
        request.includeLedger(),
        request.includeIneligible(),
        request.fileName(),
        idempotencyKey,
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/what-if/exports/{exportId}")
  public WhatIfExportService.ExportResponse getWhatIfExport(
      @PathVariable String tenantId,
      @PathVariable UUID exportId) {
    return whatIfExportService.getExport(tenantId, exportId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/what-if/exports/{exportId}/download")
  public ResponseEntity<byte[]> downloadWhatIfExport(
      @PathVariable String tenantId,
      @PathVariable UUID exportId) {
    WhatIfExportService.ExportDownload download = whatIfExportService.download(tenantId, exportId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
        .contentType(MediaType.parseMediaType(download.contentType()))
        .body(download.content());
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/exports/{exportId}:revoke")
  public WhatIfExportService.ExportResponse revokeWhatIfExport(
      @PathVariable String tenantId,
      @PathVariable UUID exportId,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
    enforceGuardrails(tenantId, "EXPORT", actorId == null || actorId.isBlank() ? "system" : actorId,
        Map.of("sourceTenantId", tenantId));
    return whatIfExportService.revoke(tenantId, exportId, actorId, correlationId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/replays")
  public ResponseEntity<WhatIfReplayService.ReplayResponse> runWhatIfReplay(
      @PathVariable String tenantId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
      @RequestBody WhatIfReplayRequest request) {
    enforceGuardrails(tenantId, "REPLAY", actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        Map.of("sourceTenantId", tenantId));
    var response = whatIfReplayService.runReplay(new WhatIfReplayService.CreateReplayCommand(
        tenantId,
        request.sourceType(),
        request.sourceId(),
        request.mode(),
        request.reasonCode(),
        request.includeLedger(),
        request.toleranceProfileId(),
        request.replayPackage(),
        request.currentVersionRefs(),
        request.replayDiffs(),
        idempotencyKey,
        actorId == null || actorId.isBlank() ? request.actorId() : actorId,
        correlationId,
        causationId));
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/what-if/replays/{replayId}")
  public WhatIfReplayService.ReplayResponse getWhatIfReplay(
      @PathVariable String tenantId,
      @PathVariable UUID replayId) {
    return whatIfReplayService.getReplay(tenantId, replayId);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/what-if/guardrails/effective")
  public WhatIfGuardrailService.EffectivePolicyResponse getEffectiveGuardrailPolicy(@PathVariable String tenantId) {
    return whatIfGuardrailService.effectivePolicy(tenantId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/guardrails/evaluate")
  public WhatIfGuardrailService.EvaluationResponse evaluateGuardrails(
      @PathVariable String tenantId,
      @RequestBody GuardrailEvaluateRequest request) {
    return whatIfGuardrailService.evaluate(tenantId, new WhatIfGuardrailService.EvaluateCommand(
        request.action(),
        request.actorId(),
        request.context()));
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/guardrail-policies")
  public ResponseEntity<WhatIfGuardrailService.GuardrailPolicyResponse> createGuardrailPolicy(
      @PathVariable String tenantId,
      @RequestBody GuardrailPolicyRequest request) {
    var response = whatIfGuardrailService.createPolicy(tenantId, new WhatIfGuardrailService.CreatePolicyCommand(
        request.actorId(),
        request.rules()));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/guardrail-policies/{policyId}:validate")
  public WhatIfGuardrailService.GuardrailPolicyResponse validateGuardrailPolicy(
      @PathVariable String tenantId,
      @PathVariable UUID policyId) {
    return whatIfGuardrailService.validatePolicy(tenantId, policyId);
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/guardrail-policies/{policyId}:publish")
  public WhatIfGuardrailService.GuardrailPolicyResponse publishGuardrailPolicy(
      @PathVariable String tenantId,
      @PathVariable UUID policyId,
      @RequestBody GuardrailPolicyActionRequest request) {
    return whatIfGuardrailService.publishPolicy(tenantId, policyId, new WhatIfGuardrailService.PublishPolicyCommand(request.actorId()));
  }

  @PostMapping("/api/v1/tenants/{tenantId}/what-if/guardrail-policies/{policyId}:rollback")
  public WhatIfGuardrailService.GuardrailPolicyResponse rollbackGuardrailPolicy(
      @PathVariable String tenantId,
      @PathVariable UUID policyId,
      @RequestBody GuardrailPolicyActionRequest request) {
    return whatIfGuardrailService.rollbackPolicy(tenantId, policyId, new WhatIfGuardrailService.PublishPolicyCommand(request.actorId()));
  }

  private void enforceGuardrails(String tenantId, String action, String actorId, Map<String, String> context) {
    var response = whatIfGuardrailService.evaluate(tenantId, new WhatIfGuardrailService.EvaluateCommand(action, actorId, context));
    if ("BLOCK".equals(response.severity())) {
      String ruleCodes = response.decisions().stream()
          .map(WhatIfGuardrailService.GuardrailDecision::ruleCode)
          .toList()
          .toString();
      throw new WhatIfGuardrailService.PolicyNotSatisfiedException("what-if guardrail blocked " + action + ": " + ruleCodes);
    }
  }

  private static int parseVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new SavedWhatIfAnalysisService.ValidationException("If-Match version is required");
    }
    String normalized = ifMatch.trim().replace("\"", "");
    if (normalized.startsWith("v")) {
      normalized = normalized.substring(1);
    }
    try {
      return Integer.parseInt(normalized);
    } catch (NumberFormatException ex) {
      throw new SavedWhatIfAnalysisService.ValidationException("If-Match version must be numeric");
    }
  }

  @ExceptionHandler(ValidationException.class)
  ResponseEntity<Map<String, String>> validationFailure(ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(UnsupportedFieldException.class)
  ResponseEntity<Map<String, String>> unsupportedField(UnsupportedFieldException ex) {
    return ResponseEntity.unprocessableEntity()
        .body(Map.of("code", "FIELD_NOT_VARIANT_EDITABLE", "message", ex.getMessage()));
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ResponseEntity<Map<String, String>> idempotencyConflict(IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(FicoSensitivityService.ValidationException.class)
  ResponseEntity<Map<String, String>> ficoValidationFailure(FicoSensitivityService.ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(FicoSensitivityService.SourceFicoRequiredException.class)
  ResponseEntity<Map<String, String>> sourceFicoRequired(FicoSensitivityService.SourceFicoRequiredException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "SOURCE_FICO_REQUIRED", "message", ex.getMessage()));
  }

  @ExceptionHandler(FicoSensitivityService.PolicyNotSatisfiedException.class)
  ResponseEntity<Map<String, String>> policyNotSatisfied(FicoSensitivityService.PolicyNotSatisfiedException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "POLICY_NOT_SATISFIED", "message", ex.getMessage()));
  }

  @ExceptionHandler(FicoSensitivityService.IdempotencyConflictException.class)
  ResponseEntity<Map<String, String>> ficoIdempotencyConflict(FicoSensitivityService.IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(FicoSensitivityService.NotFoundException.class)
  ResponseEntity<Map<String, String>> ficoRunNotFound(FicoSensitivityService.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(LtvSensitivityService.ValidationException.class)
  ResponseEntity<Map<String, String>> ltvValidationFailure(LtvSensitivityService.ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(LtvSensitivityService.PolicyNotSatisfiedException.class)
  ResponseEntity<Map<String, String>> ltvPolicyNotSatisfied(LtvSensitivityService.PolicyNotSatisfiedException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "POLICY_NOT_SATISFIED", "message", ex.getMessage()));
  }

  @ExceptionHandler(LtvSensitivityService.IdempotencyConflictException.class)
  ResponseEntity<Map<String, String>> ltvIdempotencyConflict(LtvSensitivityService.IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(LtvSensitivityService.NotFoundException.class)
  ResponseEntity<Map<String, String>> ltvRunNotFound(LtvSensitivityService.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(LockPeriodComparisonService.ValidationException.class)
  ResponseEntity<Map<String, String>> lockPeriodValidationFailure(LockPeriodComparisonService.ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(LockPeriodComparisonService.PolicyNotSatisfiedException.class)
  ResponseEntity<Map<String, String>> lockPeriodPolicyNotSatisfied(LockPeriodComparisonService.PolicyNotSatisfiedException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "POLICY_NOT_SATISFIED", "message", ex.getMessage()));
  }

  @ExceptionHandler(LockPeriodComparisonService.PricingVersionStaleException.class)
  ResponseEntity<Map<String, String>> lockPeriodPricingVersionStale(LockPeriodComparisonService.PricingVersionStaleException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "PRICING_VERSION_STALE", "message", ex.getMessage()));
  }

  @ExceptionHandler(LockPeriodComparisonService.IdempotencyConflictException.class)
  ResponseEntity<Map<String, String>> lockPeriodIdempotencyConflict(LockPeriodComparisonService.IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(LockPeriodComparisonService.NotFoundException.class)
  ResponseEntity<Map<String, String>> lockPeriodRunNotFound(LockPeriodComparisonService.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(ProductComparisonService.ValidationException.class)
  ResponseEntity<Map<String, String>> productComparisonValidationFailure(ProductComparisonService.ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(ProductComparisonService.PolicyNotSatisfiedException.class)
  ResponseEntity<Map<String, String>> productComparisonPolicyNotSatisfied(ProductComparisonService.PolicyNotSatisfiedException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "POLICY_NOT_SATISFIED", "message", ex.getMessage()));
  }

  @ExceptionHandler(ProductComparisonService.IdempotencyConflictException.class)
  ResponseEntity<Map<String, String>> productComparisonIdempotencyConflict(ProductComparisonService.IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(ProductComparisonService.NotFoundException.class)
  ResponseEntity<Map<String, String>> productComparisonRunNotFound(ProductComparisonService.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(BatchSensitivityGridService.ValidationException.class)
  ResponseEntity<Map<String, String>> batchGridValidationFailure(BatchSensitivityGridService.ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(BatchSensitivityGridService.CellLimitExceededException.class)
  ResponseEntity<Map<String, String>> batchGridCellLimitExceeded(BatchSensitivityGridService.CellLimitExceededException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "GRID_CELL_LIMIT_EXCEEDED", "message", ex.getMessage()));
  }

  @ExceptionHandler(BatchSensitivityGridService.IdempotencyConflictException.class)
  ResponseEntity<Map<String, String>> batchGridIdempotencyConflict(BatchSensitivityGridService.IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(BatchSensitivityGridService.NotFoundException.class)
  ResponseEntity<Map<String, String>> batchGridNotFound(BatchSensitivityGridService.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(SavedWhatIfAnalysisService.ValidationException.class)
  ResponseEntity<Map<String, String>> savedAnalysisValidationFailure(SavedWhatIfAnalysisService.ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(SavedWhatIfAnalysisService.SelectionNotAvailableException.class)
  ResponseEntity<Map<String, String>> savedAnalysisSelectionUnavailable(SavedWhatIfAnalysisService.SelectionNotAvailableException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "SELECTION_NOT_AVAILABLE", "message", ex.getMessage()));
  }

  @ExceptionHandler(SavedWhatIfAnalysisService.AnalysisVersionConflictException.class)
  ResponseEntity<Map<String, String>> savedAnalysisVersionConflict(SavedWhatIfAnalysisService.AnalysisVersionConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "ANALYSIS_VERSION_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(SavedWhatIfAnalysisService.IdempotencyConflictException.class)
  ResponseEntity<Map<String, String>> savedAnalysisIdempotencyConflict(SavedWhatIfAnalysisService.IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(SavedWhatIfAnalysisService.NameConflictException.class)
  ResponseEntity<Map<String, String>> savedAnalysisNameConflict(SavedWhatIfAnalysisService.NameConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "VERSION_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(SavedWhatIfAnalysisService.ShareTargetNotFoundException.class)
  ResponseEntity<Map<String, String>> savedAnalysisShareTargetNotFound(SavedWhatIfAnalysisService.ShareTargetNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(SavedWhatIfAnalysisService.NotFoundException.class)
  ResponseEntity<Map<String, String>> savedAnalysisNotFound(SavedWhatIfAnalysisService.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfExportService.ValidationException.class)
  ResponseEntity<Map<String, String>> whatIfExportValidationFailure(WhatIfExportService.ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfExportService.PolicyNotSatisfiedException.class)
  ResponseEntity<Map<String, String>> whatIfExportPolicyNotSatisfied(WhatIfExportService.PolicyNotSatisfiedException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "POLICY_NOT_SATISFIED", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfExportService.IdempotencyConflictException.class)
  ResponseEntity<Map<String, String>> whatIfExportIdempotencyConflict(WhatIfExportService.IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfExportService.NotFoundException.class)
  ResponseEntity<Map<String, String>> whatIfExportNotFound(WhatIfExportService.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfExportService.ExportExpiredException.class)
  ResponseEntity<Map<String, String>> whatIfExportExpired(WhatIfExportService.ExportExpiredException ex) {
    return ResponseEntity.status(HttpStatus.GONE).body(Map.of("code", "EXPORT_EXPIRED", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfExportService.ExportRevokedException.class)
  ResponseEntity<Map<String, String>> whatIfExportRevoked(WhatIfExportService.ExportRevokedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "EXPORT_REVOKED", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfExportService.DependencyUnavailableException.class)
  ResponseEntity<Map<String, String>> whatIfExportDependencyUnavailable(WhatIfExportService.DependencyUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("code", "DEPENDENCY_UNAVAILABLE", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfReplayService.ValidationException.class)
  ResponseEntity<Map<String, String>> whatIfReplayValidationFailure(WhatIfReplayService.ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfReplayService.ReplayPackageIncompleteException.class)
  ResponseEntity<Map<String, String>> whatIfReplayPackageIncomplete(WhatIfReplayService.ReplayPackageIncompleteException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "REPLAY_PACKAGE_INCOMPLETE", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfReplayService.ReplayVersionUnavailableException.class)
  ResponseEntity<Map<String, String>> whatIfReplayVersionUnavailable(WhatIfReplayService.ReplayVersionUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "REPLAY_VERSION_UNAVAILABLE", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfReplayService.IdempotencyConflictException.class)
  ResponseEntity<Map<String, String>> whatIfReplayIdempotencyConflict(WhatIfReplayService.IdempotencyConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfReplayService.NotFoundException.class)
  ResponseEntity<Map<String, String>> whatIfReplayNotFound(WhatIfReplayService.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfGuardrailService.ValidationException.class)
  ResponseEntity<Map<String, String>> whatIfGuardrailValidationFailure(WhatIfGuardrailService.ValidationException ex) {
    return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfGuardrailService.PolicyNotSatisfiedException.class)
  ResponseEntity<Map<String, String>> whatIfGuardrailPolicyNotSatisfied(WhatIfGuardrailService.PolicyNotSatisfiedException ex) {
    return ResponseEntity.unprocessableEntity().body(Map.of("code", "POLICY_NOT_SATISFIED", "message", ex.getMessage()));
  }

  @ExceptionHandler(WhatIfGuardrailService.NotFoundException.class)
  ResponseEntity<Map<String, String>> whatIfGuardrailNotFound(WhatIfGuardrailService.NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND", "message", ex.getMessage()));
  }

  public record CreateVariantRequest(
      String variantName,
      String reasonCode,
      Integer sourceQuoteVersion,
      Instant pricingAsOf,
      List<VariantChange> changes,
      String actorId) {}

  public record FicoSensitivityRequest(
      Integer sourceQuoteVersion,
      Integer sourceFico,
      String baseVariantId,
      List<Integer> scores,
      boolean includeIneligible,
      Instant pricingAsOf,
      String actorId) {}

  public record LtvSensitivityRequest(
      Integer sourceQuoteVersion,
      LtvSensitivityService.LtvSensitivityMode mode,
      List<BigDecimal> values,
      BigDecimal propertyValue,
      BigDecimal purchasePrice,
      BigDecimal currentLoanAmount,
      BigDecimal subordinateLienAmount,
      boolean includeMiEstimate,
      Instant pricingAsOf,
      String actorId) {}

  public record LockPeriodComparisonRequest(
      Integer sourceQuoteVersion,
      String baseVariantId,
      List<Integer> lockPeriods,
      LocalDate lockStartDate,
      boolean includeExtensionEstimate,
      Instant pricingAsOf,
      String actorId) {}

  public record ProductComparisonRequest(
      Integer sourceQuoteVersion,
      List<String> candidateProductIds,
      List<String> investorIds,
      boolean includeIneligible,
      String baselineProductId,
      Instant pricingAsOf,
      String actorId) {}

  public record ProductPromotionRequest(String actorId) {}

  public record BatchGridRequest(
      Integer sourceQuoteVersion,
      String gridName,
      List<BatchSensitivityGridService.BatchGridAxis> axes,
      boolean includeIneligible,
      Integer maxCells,
      Instant pricingAsOf,
      String actorId) {}

  public record SaveAnalysisRequest(
      String name,
      String reasonCode,
      String sourceQuoteId,
      Integer sourceQuoteVersion,
      String pricingConfigVersion,
      List<String> selectedVariantIds,
      List<String> selectedGridCellIds,
      String notes,
      List<String> tags,
      String visibility,
      String retentionCategory,
      boolean linkToQuoteDecision,
      String actorId) {}

  public record PatchAnalysisRequest(
      String name,
      String notes,
      List<String> tags,
      String visibility,
      String status,
      List<SavedWhatIfAnalysisService.SharePermission> sharePermissions,
      String actorId) {}

  public record WhatIfExportRequest(
      String sourceType,
      String sourceId,
      List<String> rowIds,
      String format,
      String recipientType,
      boolean includeLedger,
      boolean includeIneligible,
      String fileName,
      String actorId) {}

  public record WhatIfReplayRequest(
      String sourceType,
      String sourceId,
      String mode,
      String reasonCode,
      boolean includeLedger,
      String toleranceProfileId,
      WhatIfReplayService.ReplayPackage replayPackage,
      List<WhatIfReplayService.VersionRef> currentVersionRefs,
      List<WhatIfReplayService.ReplayDiff> replayDiffs,
      String actorId) {}

  public record GuardrailEvaluateRequest(
      String action,
      String actorId,
      Map<String, String> context) {}

  public record GuardrailPolicyRequest(
      String actorId,
      List<WhatIfGuardrailService.GuardrailRule> rules) {}

  public record GuardrailPolicyActionRequest(String actorId) {}
}
