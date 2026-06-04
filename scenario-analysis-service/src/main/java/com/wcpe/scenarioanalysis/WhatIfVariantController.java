package com.wcpe.scenarioanalysis;

import com.wcpe.scenarioanalysis.WhatIfVariantService.CreateVariantCommand;
import com.wcpe.scenarioanalysis.WhatIfVariantService.CreateVariantResponse;
import com.wcpe.scenarioanalysis.WhatIfVariantService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.WhatIfVariantService.UnsupportedFieldException;
import com.wcpe.scenarioanalysis.WhatIfVariantService.ValidationException;
import com.wcpe.scenarioanalysis.WhatIfVariantService.VariantChange;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WhatIfVariantController {
  private final WhatIfVariantService service;
  private final FicoSensitivityService ficoSensitivityService;

  public WhatIfVariantController(WhatIfVariantService service, FicoSensitivityService ficoSensitivityService) {
    this.service = service;
    this.ficoSensitivityService = ficoSensitivityService;
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
}
