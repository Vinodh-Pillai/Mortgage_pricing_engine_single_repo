package com.wcpe.eligibility.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.models.FicoLtvEvaluationResult;
import com.wcpe.eligibility.domain.models.FicoLtvMatrixEvaluationRequest;
import com.wcpe.eligibility.domain.models.OccupancyPurposeEvaluationRequest;
import com.wcpe.eligibility.domain.models.OccupancyPurposeEvaluationResult;
import com.wcpe.eligibility.domain.models.PropertyTypeEvaluationRequest;
import com.wcpe.eligibility.domain.models.PropertyTypeEvaluationResult;
import com.wcpe.eligibility.service.EligibilityApplicationService;
import com.wcpe.eligibility.service.FicoLtvMatrixService;
import com.wcpe.eligibility.service.OccupancyPurposeRuleService;
import com.wcpe.eligibility.service.PropertyTypeRuleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class EligibilityApiController {
    private final EligibilityApplicationService service;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FicoLtvMatrixService ficoLtvMatrixService;
    private final OccupancyPurposeRuleService occupancyPurposeRuleService;
    private final PropertyTypeRuleService propertyTypeRuleService;

    public EligibilityApiController(EligibilityApplicationService service, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                       FicoLtvMatrixService ficoLtvMatrixService,
                                       OccupancyPurposeRuleService occupancyPurposeRuleService,
                                       PropertyTypeRuleService propertyTypeRuleService) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ficoLtvMatrixService = ficoLtvMatrixService;
        this.occupancyPurposeRuleService = occupancyPurposeRuleService;
        this.propertyTypeRuleService = propertyTypeRuleService;
    }

    @PostMapping("/evaluate")
    ResponseEntity<EligibilityResult> evaluate(
            @PathVariable UUID tenantId,
            @RequestBody EligibilityRequest request,
            HttpServletRequest http) {

        // Idempotency check
        String idempotencyKey = http.getHeader("Idempotency-Key");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String requestHash = Hashing.sha256(objectMapper.writeValueAsString(request));

                // Try to find existing record
                try {
                    String existingResponse = jdbcTemplate.queryForObject(
                        "SELECT response_json::text FROM eligibility.idempotency_record WHERE tenant_id = ? AND idempotency_key = ?",
                        String.class, tenantId, idempotencyKey
                    );
                    if (existingResponse != null) {
                        String existingHash = jdbcTemplate.queryForObject(
                            "SELECT request_hash FROM eligibility.idempotency_record WHERE tenant_id = ? AND idempotency_key = ?",
                            String.class, tenantId, idempotencyKey
                        );
                        if (!existingHash.equals(requestHash)) {
                            return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(null);
                        }
                        // Return cached result
                        EligibilityResult cached = objectMapper.readValue(existingResponse, EligibilityResult.class);
                        return ResponseEntity.ok(cached);
                    }
                } catch (Exception e) {
                    // No existing record, proceed with evaluation
                }
            } catch (JsonProcessingException e) {
                // If serialization fails during idempotency check, proceed with evaluation
            }
        }

        EligibilityResult result = service.evaluate(tenantId, request);

        // Store idempotency record if key was provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String responseJson = objectMapper.writeValueAsString(result);
                String requestHash = Hashing.sha256(objectMapper.writeValueAsString(request));
                jdbcTemplate.update(
                    "INSERT INTO eligibility.idempotency_record (tenant_id, idempotency_key, request_hash, response_type, response_json) " +
                    "VALUES (?, ?, ?, ?, ?::jsonb) " +
                    "ON CONFLICT (tenant_id, idempotency_key) DO NOTHING",
                    tenantId, idempotencyKey, requestHash, "EligibilityResult", responseJson
                );
            } catch (JsonProcessingException e) {
                // Log and continue - idempotency storage is non-critical
            }
        }

        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @PostMapping("/eligibility/evaluations/fico-ltv-matrix")
    ResponseEntity<FicoLtvEvaluationResult> evaluateFicoLtvMatrix(
            @PathVariable UUID tenantId,
            @RequestBody FicoLtvMatrixEvaluationRequest request) {

        if (request.facts() == null || request.facts().representativeFico() == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(null);
        }

        FicoLtvEvaluationResult result = ficoLtvMatrixService.evaluate(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/eligibility/evaluations/occupancy-purpose")
    ResponseEntity<OccupancyPurposeEvaluationResult> evaluateOccupancyPurpose(
            @PathVariable UUID tenantId,
            @RequestBody OccupancyPurposeEvaluationRequest request) {

        if (request.facts() == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(null);
        }

        if (request.facts().loanPurpose() == null || request.facts().loanPurpose().isBlank()) {
            OccupancyPurposeEvaluationResult result = occupancyPurposeRuleService.evaluate(tenantId, request);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }

        if (!"PURCHASE".equals(request.facts().loanPurpose())) {
            OccupancyPurposeEvaluationResult result = occupancyPurposeRuleService.evaluate(tenantId, request);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }

        if (request.facts().occupancyType() == null || request.facts().occupancyType().isBlank()) {
            OccupancyPurposeEvaluationResult result = occupancyPurposeRuleService.evaluate(tenantId, request);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }

        OccupancyPurposeEvaluationResult result = occupancyPurposeRuleService.evaluate(tenantId, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/eligibility/evaluations/property-type")
    ResponseEntity<PropertyTypeEvaluationResult> evaluatePropertyType(
            @PathVariable UUID tenantId,
            @RequestBody PropertyTypeEvaluationRequest request) {

        if (request.facts() == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(null);
        }

        if (request.facts().propertyType() == null || request.facts().propertyType().isBlank()) {
            PropertyTypeEvaluationResult result = propertyTypeRuleService.evaluate(tenantId, request);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }

        if (request.facts().units() < 1 || request.facts().units() > 4) {
            PropertyTypeEvaluationResult result = propertyTypeRuleService.evaluate(tenantId, request);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }

        PropertyTypeEvaluationResult result = propertyTypeRuleService.evaluate(tenantId, request);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "message", ex.getMessage()));
    }
}
