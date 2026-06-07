package com.wcpe.auditreplay.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.application.AuditRecordCommand;
import com.wcpe.auditreplay.application.AuditRecorder;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuditRecordController {

    private final AuditRecorder auditRecorder;
    private final AuditRecordRepository auditRecordRepository;
    private final ObjectMapper objectMapper;

    public AuditRecordController(
            AuditRecorder auditRecorder,
            AuditRecordRepository auditRecordRepository,
            ObjectMapper objectMapper) {
        this.auditRecorder = auditRecorder;
        this.auditRecordRepository = auditRecordRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/internal/v1/tenants/{tenantId}/audit-records")
    @Transactional
    public AuditRecordResponse create(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AuditRecordRequest request) {
        requireAuthorization(authorization);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
        }
        try {
            AuditRecord record = auditRecorder.record(request.toCommand(tenantId, idempotencyKey, objectMapper));
            return AuditRecordResponse.from(record, objectMapper);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/api/v1/tenants/{tenantId}/audit-records/{auditRecordId}")
    @Transactional(readOnly = true)
    public AuditRecordResponse get(
            @PathVariable UUID tenantId,
            @PathVariable UUID auditRecordId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        requireAuthorization(authorization);
        return auditRecordRepository.findByTenantIdAndId(tenantId, auditRecordId)
                .map(record -> AuditRecordResponse.from(record, objectMapper))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit record not found"));
    }

    public record AuditRecordRequest(
            String action,
            String subjectType,
            String subjectId,
            Long subjectVersion,
            String actorType,
            String actorId,
            String actorDisplay,
            UUID correlationId,
            UUID causationId,
            UUID requestId,
            String sourceIpHash,
            String userAgentHash,
            JsonNode beforeSnapshotJson,
            JsonNode afterSnapshotJson,
            JsonNode snapshotJson,
            String snapshotEncryptionKeyRef,
            String redactionProfile,
            JsonNode configVersionRefs,
            String result,
            String reasonCode,
            Instant occurredAt,
            LocalDate retentionUntil,
            boolean legalHold) {

        AuditRecordCommand toCommand(UUID tenantId, String idempotencyKey, ObjectMapper objectMapper) {
            return new AuditRecordCommand(
                    tenantId,
                    action,
                    subjectType,
                    subjectId,
                    subjectVersion,
                    actorType,
                    actorId,
                    actorDisplay,
                    correlationId,
                    causationId,
                    requestId,
                    sourceIpHash,
                    userAgentHash,
                    toJsonBytes(objectMapper, beforeSnapshotJson),
                    toJsonBytes(objectMapper, afterSnapshotJson),
                    toJsonBytes(objectMapper, snapshotJson),
                    snapshotEncryptionKeyRef,
                    redactionProfile,
                    toJsonBytes(objectMapper, configVersionRefs),
                    result,
                    reasonCode,
                    occurredAt,
                    retentionUntil,
                    legalHold,
                    idempotencyKey);
        }
    }

    public record AuditRecordResponse(
            UUID id,
            UUID tenantId,
            UUID eventId,
            String action,
            String subjectType,
            String subjectId,
            Long subjectVersion,
            String actorType,
            String actorId,
            String actorDisplay,
            UUID correlationId,
            UUID causationId,
            UUID requestId,
            String sourceIpHash,
            String userAgentHash,
            UUID beforeRef,
            UUID afterRef,
            JsonNode snapshotJson,
            String redactionProfile,
            JsonNode configVersionRefs,
            String result,
            String reasonCode,
            Instant occurredAt,
            Instant createdAt,
            LocalDate retentionUntil,
            boolean legalHold,
            String integrityHash,
            String previousHash) {
        static AuditRecordResponse from(AuditRecord record, ObjectMapper objectMapper) {
            return new AuditRecordResponse(
                    record.getId(),
                    record.getTenantId(),
                    record.getEventId(),
                    record.getAction(),
                    record.getSubjectType(),
                    record.getSubjectId(),
                    record.getSubjectVersion(),
                    record.getActorType(),
                    record.getActorId(),
                    record.getActorDisplay(),
                    record.getCorrelationId(),
                    record.getCausationId(),
                    record.getRequestId(),
                    record.getSourceIpHash(),
                    record.getUserAgentHash(),
                    record.getBeforeRef(),
                    record.getAfterRef(),
                    readJson(objectMapper, record.getSnapshotJson()),
                    record.getRedactionProfile(),
                    readJson(objectMapper, record.getConfigVersionRefs()),
                    record.getResult(),
                    record.getReasonCode(),
                    record.getOccurredAt(),
                    record.getCreatedAt(),
                    record.getRetentionUntil(),
                    record.isLegalHold(),
                    record.getIntegrityHash(),
                    record.getPreviousHash());
        }
    }

    private static byte[] toJsonBytes(ObjectMapper objectMapper, JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Request JSON field cannot be serialized", ex);
        }
    }

    private static JsonNode readJson(ObjectMapper objectMapper, byte[] value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Stored audit JSON cannot be parsed", ex);
        }
    }

    private static void requireAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required");
        }
    }
}
