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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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

  public WhatIfVariantController(
      WhatIfVariantService service,
      FicoSensitivityService ficoSensitivityService,
      LtvSensitivityService ltvSensitivityService,
      LockPeriodComparisonService lockPeriodComparisonService,
      ProductComparisonService productComparisonService) {
    this.service = service;
    this.ficoSensitivityService = ficoSensitivityService;
    this.ltvSensitivityService = ltvSensitivityService;
    this.lockPeriodComparisonService = lockPeriodComparisonService;
    this.productComparisonService = productComparisonService;
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
}
