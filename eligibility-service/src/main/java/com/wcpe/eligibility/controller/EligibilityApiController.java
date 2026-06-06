package com.wcpe.eligibility.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.cache.EligibilityCacheHealth;
import com.wcpe.eligibility.domain.extension.UnsupportedProductFamilyException;
import com.wcpe.eligibility.domain.models.EligibilityRequest;
import com.wcpe.eligibility.domain.models.EligibilityExplanationResponse;
import com.wcpe.eligibility.domain.models.EligibilityResult;
import com.wcpe.eligibility.domain.models.FicoLtvEvaluationResult;
import com.wcpe.eligibility.domain.models.FicoLtvMatrixEvaluationRequest;
import com.wcpe.eligibility.domain.models.InvestorOverlayEvaluationRequest;
import com.wcpe.eligibility.domain.models.InvestorOverlayEvaluationResult;
import com.wcpe.eligibility.domain.models.LoanLimitEvaluationRequest;
import com.wcpe.eligibility.domain.models.LoanLimitEvaluationResult;
import com.wcpe.eligibility.domain.models.OccupancyPurposeEvaluationRequest;
import com.wcpe.eligibility.domain.models.OccupancyPurposeEvaluationResult;
import com.wcpe.eligibility.domain.models.PropertyTypeEvaluationRequest;
import com.wcpe.eligibility.domain.models.PropertyTypeEvaluationResult;
import com.wcpe.eligibility.domain.models.QuoteSubmissionRequest;
import com.wcpe.eligibility.domain.models.QuoteSubmissionResponse;
import com.wcpe.eligibility.service.EligibilityApplicationService;
import com.wcpe.eligibility.cache.EligibilityCacheService;
import com.wcpe.eligibility.service.EligibilityExplanationService;
import com.wcpe.eligibility.service.FicoLtvMatrixService;
import com.wcpe.eligibility.service.InvestorOverlayRuleService;
import com.wcpe.eligibility.service.LoanLimitEvaluationService;
import com.wcpe.eligibility.service.OccupancyPurposeRuleService;
import com.wcpe.eligibility.service.PropertyTypeRuleService;
import com.wcpe.eligibility.service.QuoteSubmissionApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class EligibilityApiController {
    private final EligibilityApplicationService service;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FicoLtvMatrixService ficoLtvMatrixService;
    private final OccupancyPurposeRuleService occupancyPurposeRuleService;
    private final PropertyTypeRuleService propertyTypeRuleService;
    private final InvestorOverlayRuleService investorOverlayRuleService;
    private final EligibilityCacheService eligibilityCacheService;
    private final EligibilityExplanationService eligibilityExplanationService;
    private final QuoteSubmissionApplicationService quoteSubmissionApplicationService;
    private final LoanLimitEvaluationService loanLimitEvaluationService;

    public EligibilityApiController(EligibilityApplicationService service, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                           FicoLtvMatrixService ficoLtvMatrixService,
                                           OccupancyPurposeRuleService occupancyPurposeRuleService,
                                            PropertyTypeRuleService propertyTypeRuleService,
                                            InvestorOverlayRuleService investorOverlayRuleService,
                                            EligibilityCacheService eligibilityCacheService,
                                            EligibilityExplanationService eligibilityExplanationService) {
        this(service, jdbcTemplate, objectMapper, ficoLtvMatrixService, occupancyPurposeRuleService, propertyTypeRuleService,
            investorOverlayRuleService, eligibilityCacheService, eligibilityExplanationService, null, null);
    }

    @Autowired
    public EligibilityApiController(EligibilityApplicationService service, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                          FicoLtvMatrixService ficoLtvMatrixService,
                                          OccupancyPurposeRuleService occupancyPurposeRuleService,
                                           PropertyTypeRuleService propertyTypeRuleService,
                                            InvestorOverlayRuleService investorOverlayRuleService,
                                            EligibilityCacheService eligibilityCacheService,
                                            EligibilityExplanationService eligibilityExplanationService,
                                            QuoteSubmissionApplicationService quoteSubmissionApplicationService,
                                            LoanLimitEvaluationService loanLimitEvaluationService) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ficoLtvMatrixService = ficoLtvMatrixService;
        this.occupancyPurposeRuleService = occupancyPurposeRuleService;
        this.propertyTypeRuleService = propertyTypeRuleService;
        this.investorOverlayRuleService = investorOverlayRuleService;
        this.eligibilityCacheService = eligibilityCacheService;
        this.eligibilityExplanationService = eligibilityExplanationService;
        this.quoteSubmissionApplicationService = quoteSubmissionApplicationService;
        this.loanLimitEvaluationService = loanLimitEvaluationService;
    }

    @PostMapping("/conventional-eligibility-core")
    ResponseEntity<QuoteSubmissionResponse> submitConventionalEligibilityCore(
            @PathVariable UUID tenantId,
            @RequestBody QuoteSubmissionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        QuoteSubmissionResponse response = quoteSubmissionApplicationService.submitConventionalPurchase(tenantId, request, idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/quotes/{quoteId}/options/{quoteOptionId}/eligibility-explanation")
    ResponseEntity<EligibilityExplanationResponse> getEligibilityExplanation(
            @PathVariable UUID tenantId,
            @PathVariable UUID quoteId,
            @PathVariable UUID quoteOptionId,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(value = "X-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-Roles", required = false) String roles,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        EligibilityExplanationResponse response = eligibilityExplanationService.getExplanation(
            tenantId,
            quoteId,
            quoteOptionId,
            actorId,
            parsePermissions(permissions, roles),
            correlationId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/eligibility/cache/health")
    public ResponseEntity<EligibilityCacheHealth> cacheHealth(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "CONVENTIONAL") String productFamily,
            @RequestParam(defaultValue = "CONVENTIONAL_PURCHASE") String quoteType) {
        return ResponseEntity.ok(eligibilityCacheService.health(tenantId, productFamily, quoteType));
    }

    @PostMapping("/eligibility/evaluations/investor-overlays")
    ResponseEntity<InvestorOverlayEvaluationResult> evaluateInvestorOverlays(
            @PathVariable UUID tenantId,
            @RequestBody InvestorOverlayEvaluationRequest request) {

        InvestorOverlayEvaluationResult result = investorOverlayRuleService.evaluate(tenantId, request);
        if (result.decisions().stream().anyMatch(d -> d.reasonCode() != null && d.reasonCode().startsWith("UNSUPPORTED_OVERLAY"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }
        if (result.decisions().stream().anyMatch(d -> "BASE_DECISION_NOT_ELIGIBLE".equals(d.reasonCode()))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }
        if (result.decisions().stream().anyMatch(d -> "OVERLAY_CONFLICT".equals(d.reasonCode()))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        if (result.decisions().stream().anyMatch(d -> "OVERLAY_SET_NOT_CONFIGURED".equals(d.reasonCode()))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping({"/evaluate", "/quotes"})
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
            @RequestBody FicoLtvMatrixEvaluationRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        if (request == null || request.facts() == null || request.facts().representativeFico() == null) {
            FicoLtvEvaluationResult result = ficoLtvMatrixService.evaluate(tenantId, request, correlationId);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }

        FicoLtvEvaluationResult result = ficoLtvMatrixService.evaluate(tenantId, request, correlationId);
        if ("MATRIX_NOT_CONFIGURED".equals(result.decision().reasonCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
        if ("MATRIX_GAP_OR_OVERLAP".equals(result.decision().reasonCode())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        if ("INVALID_LTV".equals(result.decision().reasonCode()) || "INVALID_FICO".equals(result.decision().reasonCode())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/eligibility/evaluations/loan-limit")
    ResponseEntity<LoanLimitEvaluationResult> evaluateLoanLimit(
            @PathVariable UUID tenantId,
            @RequestBody LoanLimitEvaluationRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        LoanLimitEvaluationResult result = loanLimitEvaluationService.evaluate(tenantId, request, correlationId);
        if (result.decisions().stream().anyMatch(d -> "OVERLAPPING_LIMIT_VERSION".equals(d.reasonCode()))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        if (result.decisions().stream().anyMatch(d -> "MISSING_COUNTY".equals(d.reasonCode()) || "INVALID_UNIT_COUNT".equals(d.reasonCode()))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }
        if (result.decisions().stream().allMatch(d -> "LIMIT_NOT_CONFIGURED".equals(d.reasonCode()))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
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
        if ("OVERLAPPING_RULE_VERSION".equals(result.decision().reasonCode())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        if ("OCCUPANCY_PURPOSE_RULE_NOT_CONFIGURED".equals(result.decision().reasonCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
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

    @ExceptionHandler(QuoteSubmissionApplicationService.ValidationException.class)
    ResponseEntity<Map<String, Object>> validationFailed(QuoteSubmissionApplicationService.ValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
            "type", "https://pricing/errors/validation-failed",
            "code", "VALIDATION_FAILED",
            "message", ex.getMessage(),
            "fieldErrors", ex.fieldErrors()
        ));
    }

    @ExceptionHandler(QuoteSubmissionApplicationService.IdempotencyConflictException.class)
    ResponseEntity<Map<String, Object>> idempotencyConflict(QuoteSubmissionApplicationService.IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "type", "https://pricing/errors/idempotency-conflict",
            "code", "IDEMPOTENCY_CONFLICT",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(QuoteSubmissionApplicationService.DependencyUnavailableException.class)
    ResponseEntity<Map<String, Object>> dependencyUnavailable(QuoteSubmissionApplicationService.DependencyUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "type", "https://pricing/errors/dependency-unavailable",
            "code", "DEPENDENCY_UNAVAILABLE",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(UnsupportedProductFamilyException.class)
    ResponseEntity<Map<String, Object>> unsupportedProductFamily(UnsupportedProductFamilyException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
            "type", "https://pricing/errors/product-family-not-enabled",
            "code", "PRODUCT_FAMILY_NOT_ENABLED_FOR_SLICE",
            "message", ex.quoteType() + " is not enabled in PII-03.",
            "allowedValues", java.util.List.of("CONVENTIONAL_PURCHASE"),
            "remediation", "Use conventional purchase in this increment."
        ));
    }

    @ExceptionHandler(EligibilityExplanationService.AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> explanationAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ACCESS_DENIED"));
    }

    @ExceptionHandler(EligibilityExplanationService.ExplanationNotFoundException.class)
    ResponseEntity<Map<String, Object>> explanationNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "QUOTE_OPTION_NOT_FOUND"));
    }

    private Set<String> parsePermissions(String permissions, String roles) {
        return Stream.of(permissions, roles)
            .filter(value -> value != null && !value.isBlank())
            .flatMap(value -> Stream.of(value.split(",")))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toSet());
    }
}
