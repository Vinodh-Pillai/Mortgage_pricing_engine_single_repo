package com.wcpe.auditreplay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.application.EvidenceExportCommand;
import com.wcpe.auditreplay.application.EvidenceExportManifest;
import com.wcpe.auditreplay.application.EvidenceExportService;
import com.wcpe.auditreplay.domain.EvidenceExportJob;
import java.time.Instant;
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
public class EvidenceExportController {

    private final EvidenceExportService evidenceExportService;
    private final ObjectMapper objectMapper;

    public EvidenceExportController(EvidenceExportService evidenceExportService, ObjectMapper objectMapper) {
        this.evidenceExportService = evidenceExportService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/tenants/{tenantId}/evidence-exports")
    @Transactional
    public EvidenceExportResponse create(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody EvidenceExportRequest request) {
        requireAuthorization(authorization);
        requireText(idempotencyKey, "Idempotency-Key is required");
        try {
            EvidenceExportJob job = evidenceExportService.requestExport(request.toCommand(tenantId, idempotencyKey, objectMapper));
            return EvidenceExportResponse.from(job, evidenceExportService.manifest(job));
        } catch (EvidenceExportService.IdempotencyConflictException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (EvidenceExportService.EvidenceExportNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/api/v1/tenants/{tenantId}/evidence-exports/{exportId}")
    @Transactional(readOnly = true)
    public EvidenceExportResponse get(
            @PathVariable UUID tenantId,
            @PathVariable UUID exportId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAuthorization(authorization);
        try {
            EvidenceExportJob job = evidenceExportService.getExport(tenantId, exportId);
            return EvidenceExportResponse.from(job, evidenceExportService.manifest(job));
        } catch (EvidenceExportService.EvidenceExportNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/api/v1/tenants/{tenantId}/evidence-exports/{exportId}/download")
    @Transactional
    public EvidenceDownloadResponse download(
            @PathVariable UUID tenantId,
            @PathVariable UUID exportId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId) {
        requireAuthorization(authorization);
        requireText(idempotencyKey, "Idempotency-Key is required");
        requireText(actorId, "X-Actor-Id is required");
        try {
            return EvidenceDownloadResponse.from(evidenceExportService.recordDownload(tenantId, exportId, actorId, idempotencyKey));
        } catch (EvidenceExportService.EvidenceExportNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    public record EvidenceExportRequest(
            List<UUID> sourceAuditRecordIds,
            List<UUID> replayRunIds,
            String purpose,
            String format,
            String redactionProfile,
            boolean includeSnapshots,
            boolean includeDiffs,
            boolean includeHashes,
            String requestedBy,
            Instant expiresAt,
            JsonNode clientContext) {

        EvidenceExportCommand toCommand(UUID tenantId, String idempotencyKey, ObjectMapper objectMapper) {
            CorrelationContext.Data context = CorrelationContext.get();
            if (context == null || context.correlationId() == null || context.requestId() == null) {
                throw new IllegalArgumentException("CorrelationContext is required");
            }
            return new EvidenceExportCommand(
                    tenantId,
                    sourceAuditRecordIds,
                    replayRunIds == null ? List.of() : replayRunIds,
                    purpose,
                    format,
                    redactionProfile,
                    includeSnapshots,
                    includeDiffs,
                    includeHashes,
                    requestedBy,
                    context.correlationId().getValue(),
                    context.causationId() == null || !context.causationId().isPresent() ? null : context.causationId().get(),
                    idempotencyKey,
                    expiresAt,
                    clientContext == null || clientContext.isNull()
                            ? Map.of()
                            : objectMapper.convertValue(clientContext, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        }
    }

    public record EvidenceExportResponse(
            UUID exportId,
            UUID tenantId,
            String status,
            String format,
            String purpose,
            String redactionProfile,
            String artifactUri,
            String manifestHash,
            String expiresAt,
            String retentionUntil,
            boolean legalHold,
            String correlationId,
            JsonNode manifest,
            Map<String, Object> resultSummary) {
        static EvidenceExportResponse from(EvidenceExportJob job, JsonNode manifest) {
            return new EvidenceExportResponse(
                    job.getExportId(),
                    job.getTenantId(),
                    job.getStatus().name(),
                    job.getFormat(),
                    job.getPurpose(),
                    job.getRedactionProfile(),
                    job.getArtifactUri(),
                    job.getManifestHash(),
                    job.getExpiresAt() == null ? null : job.getExpiresAt().toString(),
                    job.getRetentionUntil().toString(),
                    job.isLegalHold(),
                    job.getCorrelationId().toString(),
                    manifest,
                    EvidenceExportManifest.resultSummary(job));
        }
    }

    public record EvidenceDownloadResponse(
            UUID exportId,
            String status,
            String artifactUri,
            String manifestHash,
            String expiresAt,
            String note) {
        static EvidenceDownloadResponse from(EvidenceExportJob job) {
            return new EvidenceDownloadResponse(
                    job.getExportId(),
                    job.getStatus().name(),
                    job.getArtifactUri(),
                    job.getManifestHash(),
                    job.getExpiresAt() == null ? null : job.getExpiresAt().toString(),
                    "Internal artifact reference returned; signed URL provider is outside the acquired lock scope.");
        }
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
