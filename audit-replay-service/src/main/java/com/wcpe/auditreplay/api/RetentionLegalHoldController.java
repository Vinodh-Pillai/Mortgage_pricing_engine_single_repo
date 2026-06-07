package com.wcpe.auditreplay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.application.RetentionLegalHoldService;
import com.wcpe.auditreplay.domain.LegalHold;
import com.wcpe.auditreplay.domain.RetentionPolicy;
import com.wcpe.auditreplay.domain.RetentionPurgeRun;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RetentionLegalHoldController {

    private final RetentionLegalHoldService service;
    private final ObjectMapper objectMapper;

    public RetentionLegalHoldController(RetentionLegalHoldService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/tenants/{tenantId}/retention-policies")
    @Transactional
    public RetentionPolicyResponse publishPolicy(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RetentionPolicyRequest request) {
        requireAuthorization(authorization);
        requireText(idempotencyKey, "Idempotency-Key is required");
        try {
            return RetentionPolicyResponse.from(service.publishPolicy(request.toCommand(tenantId, idempotencyKey)));
        } catch (RetentionLegalHoldService.PolicyNotSatisfiedException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/api/v1/tenants/{tenantId}/legal-holds")
    @Transactional
    public LegalHoldResponse applyHold(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody LegalHoldRequest request) {
        requireAuthorization(authorization);
        requireText(idempotencyKey, "Idempotency-Key is required");
        try {
            return LegalHoldResponse.from(service.applyHold(request.toCommand(tenantId, idempotencyKey, objectMapper)));
        } catch (RetentionLegalHoldService.PolicyNotSatisfiedException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/api/v1/tenants/{tenantId}/legal-holds/{holdId}/release")
    @Transactional
    public LegalHoldResponse releaseHold(
            @PathVariable UUID tenantId,
            @PathVariable UUID holdId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody LegalHoldReleaseRequest request) {
        requireAuthorization(authorization);
        requireText(idempotencyKey, "Idempotency-Key is required");
        try {
            return LegalHoldResponse.from(service.releaseHold(tenantId, holdId, request.toCommand(idempotencyKey)));
        } catch (RetentionLegalHoldService.RetentionLegalHoldNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/api/v1/tenants/{tenantId}/retention-runs")
    @Transactional
    public RetentionPurgeRunResponse runPurge(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RetentionPurgeRunRequest request) {
        requireAuthorization(authorization);
        requireText(idempotencyKey, "Idempotency-Key is required");
        try {
            RetentionPurgeRun run = service.runPurge(request.toCommand(tenantId, idempotencyKey));
            return RetentionPurgeRunResponse.from(run, service.report(run));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/api/v1/tenants/{tenantId}/retention-runs/{runId}")
    @Transactional(readOnly = true)
    public RetentionPurgeRunResponse getRun(
            @PathVariable UUID tenantId,
            @PathVariable UUID runId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAuthorization(authorization);
        try {
            RetentionPurgeRun run = service.getPurgeRun(tenantId, runId);
            return RetentionPurgeRunResponse.from(run, service.report(run));
        } catch (RetentionLegalHoldService.RetentionLegalHoldNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    public record RetentionPolicyRequest(
            UUID policyId,
            String evidenceType,
            String jurisdiction,
            String productFilter,
            String channelFilter,
            Integer retentionDays,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String createdBy,
            String approvedBy,
            UUID correlationId) {
        RetentionLegalHoldService.RetentionPolicyCommand toCommand(UUID tenantId, String idempotencyKey) {
            return new RetentionLegalHoldService.RetentionPolicyCommand(
                    tenantId, policyId, evidenceType, jurisdiction, productFilter, channelFilter, retentionDays,
                    effectiveFrom, effectiveTo, createdBy, approvedBy, resolveCorrelationId(correlationId), idempotencyKey);
        }
    }

    public record LegalHoldRequest(
            UUID holdId,
            String caseId,
            JsonNode scope,
            String reason,
            String appliedBy,
            String approvedBy,
            UUID correlationId) {
        RetentionLegalHoldService.LegalHoldCommand toCommand(UUID tenantId, String idempotencyKey, ObjectMapper objectMapper) {
            Map<String, Object> scopeMap = scope == null || scope.isNull()
                    ? Map.of()
                    : objectMapper.convertValue(scope, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return new RetentionLegalHoldService.LegalHoldCommand(
                    tenantId, holdId, caseId, scopeMap, reason, appliedBy, approvedBy, resolveCorrelationId(correlationId), idempotencyKey);
        }
    }

    public record LegalHoldReleaseRequest(String releasedBy, String releaseApprovedBy) {
        RetentionLegalHoldService.LegalHoldReleaseCommand toCommand(String idempotencyKey) {
            return new RetentionLegalHoldService.LegalHoldReleaseCommand(releasedBy, releaseApprovedBy, idempotencyKey);
        }
    }

    public record RetentionPurgeRunRequest(LocalDate cutoff, boolean dryRun, String requestedBy, UUID correlationId) {
        RetentionLegalHoldService.RetentionPurgeCommand toCommand(UUID tenantId, String idempotencyKey) {
            return new RetentionLegalHoldService.RetentionPurgeCommand(
                    tenantId, cutoff, dryRun, requestedBy, resolveCorrelationId(correlationId), idempotencyKey);
        }
    }

    public record RetentionPolicyResponse(
            UUID id,
            String status,
            Integer version,
            Map<String, Object> resultSummary,
            List<String> validationMessages,
            String auditRef,
            String replayRef,
            String correlationId) {
        static RetentionPolicyResponse from(RetentionPolicy policy) {
            return new RetentionPolicyResponse(
                    policy.getPolicyId(),
                    policy.getStatus().name(),
                    policy.getVersion(),
                    Map.of(
                            "evidenceType", policy.getEvidenceType(),
                            "jurisdiction", policy.getJurisdiction(),
                            "retentionDays", policy.getRetentionDays(),
                            "effectiveFrom", policy.getEffectiveFrom().toString()),
                    List.of(),
                    "retention-policy:" + policy.getPolicyId(),
                    null,
                    policy.getCorrelationId().toString());
        }
    }

    public record LegalHoldResponse(
            UUID id,
            String status,
            Integer version,
            Map<String, Object> resultSummary,
            List<String> validationMessages,
            String auditRef,
            String replayRef,
            String correlationId) {
        static LegalHoldResponse from(LegalHold hold) {
            return new LegalHoldResponse(
                    hold.getHoldId(),
                    hold.getStatus().name(),
                    1,
                    Map.of(
                            "caseId", hold.getCaseId(),
                            "appliedBy", hold.getAppliedBy(),
                            "approvedBy", hold.getApprovedBy(),
                            "releasedAt", hold.getReleasedAt() == null ? "" : hold.getReleasedAt().toString()),
                    List.of(),
                    "legal-hold:" + hold.getHoldId(),
                    null,
                    hold.getCorrelationId().toString());
        }
    }

    public record RetentionPurgeRunResponse(
            UUID id,
            String status,
            Integer version,
            Map<String, Object> resultSummary,
            List<String> validationMessages,
            String auditRef,
            String replayRef,
            String correlationId,
            JsonNode report) {
        static RetentionPurgeRunResponse from(RetentionPurgeRun run, JsonNode report) {
            return new RetentionPurgeRunResponse(
                    run.getRunId(),
                    run.getStatus().name(),
                    1,
                    Map.of(
                            "candidateCount", run.getCandidateCount(),
                            "purgedCount", run.getPurgedCount(),
                            "skippedHeldCount", run.getSkippedHeldCount(),
                            "reportHash", run.getReportHash()),
                    List.of(),
                    "retention-purge-run:" + run.getRunId(),
                    null,
                    run.getCorrelationId().toString(),
                    report);
        }
    }

    private static UUID resolveCorrelationId(UUID requestCorrelationId) {
        CorrelationContext.Data context = CorrelationContext.get();
        if (context == null || context.correlationId() == null) {
            return requestCorrelationId;
        }
        UUID headerCorrelationId = context.correlationId().getValue();
        if (requestCorrelationId != null && !requestCorrelationId.equals(headerCorrelationId)) {
            throw new IllegalArgumentException("correlationId must match X-Correlation-Id");
        }
        return headerCorrelationId;
    }

    private static void requireAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
