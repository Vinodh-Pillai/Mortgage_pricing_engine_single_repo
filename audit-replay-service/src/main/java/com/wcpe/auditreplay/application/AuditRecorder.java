package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.AuditSnapshot;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.AuditSnapshotRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditRecorder {

    static final String AUDIT_RECORD_CREATED_EVENT = "AuditRecordCreated.v1";
    static final String AUDIT_RECORD_SCHEMA_REF = "audit-record-created-v1";

    private final AuditRecordRepository auditRecordRepository;
    private final AuditSnapshotRepository auditSnapshotRepository;
    private final OutboxRecorder outboxRecorder;
    private final ObjectMapper objectMapper;
    private final AuditRedactionPolicy redactionPolicy;
    private final AuditRecorderMetrics metrics;

    @Autowired
    public AuditRecorder(
            AuditRecordRepository auditRecordRepository,
            AuditSnapshotRepository auditSnapshotRepository,
            OutboxRecorder outboxRecorder,
            ObjectMapper objectMapper,
            AuditRedactionPolicy redactionPolicy,
            AuditRecorderMetrics metrics) {
        this.auditRecordRepository = auditRecordRepository;
        this.auditSnapshotRepository = auditSnapshotRepository;
        this.outboxRecorder = outboxRecorder;
        this.objectMapper = objectMapper;
        this.redactionPolicy = redactionPolicy;
        this.metrics = metrics;
    }

    AuditRecorder(
            AuditRecordRepository auditRecordRepository,
            AuditSnapshotRepository auditSnapshotRepository,
            OutboxRecorder outboxRecorder,
            ObjectMapper objectMapper) {
        this(
                auditRecordRepository,
                auditSnapshotRepository,
                outboxRecorder,
                objectMapper,
                new AuditRedactionPolicy(objectMapper),
                new AuditRecorderMetrics(null));
    }

    @Transactional
    public AuditRecord record(AuditRecordCommand command) {
        long started = System.nanoTime();
        CorrelationContext.checkPersistenceBoundary();
        validate(command);
        requireCorrelationContextMatchesCommand(command);
        Optional<AuditRecord> existing = auditRecordRepository.findByTenantIdAndRequestId(command.tenantId(), command.requestId());
        if (existing.isPresent()) {
            return existing.get();
        }
        AuditRecordCommand redactedCommand = withRedactedSnapshot(command);
        UUID beforeRef = saveSnapshot(redactedCommand.tenantId(), redactedCommand.beforeSnapshotJson(), redactedCommand.snapshotEncryptionKeyRef());
        UUID afterRef = saveSnapshot(redactedCommand.tenantId(), redactedCommand.afterSnapshotJson(), redactedCommand.snapshotEncryptionKeyRef());
        Optional<AuditRecord> previousRecord = auditRecordRepository.findTopByTenantIdOrderByOccurredAtDescCreatedAtDesc(redactedCommand.tenantId());
        String previousHash = previousRecord.map(AuditRecord::getIntegrityHash).orElse(null);
        if (previousRecord.isPresent() && (previousHash == null || previousHash.isBlank())) {
            metrics.recordHashChainBreak();
            throw new IllegalStateException("Audit hash chain previous record is missing integrityHash");
        }
        String integrityHash = AuditIntegrityHashService.hash(redactedCommand, previousHash, beforeRef, afterRef, objectMapper);
        AuditRecord record = AuditRecord.create(
                redactedCommand.tenantId(),
                UUID.nameUUIDFromBytes((redactedCommand.tenantId() + ":" + redactedCommand.requestId()).getBytes(StandardCharsets.UTF_8)),
                redactedCommand.action(),
                redactedCommand.subjectType(),
                redactedCommand.subjectId(),
                redactedCommand.subjectVersion(),
                redactedCommand.actorType(),
                redactedCommand.actorId(),
                redactedCommand.actorDisplay(),
                redactedCommand.correlationId(),
                redactedCommand.causationId(),
                redactedCommand.requestId(),
                redactedCommand.sourceIpHash(),
                redactedCommand.userAgentHash(),
                beforeRef,
                afterRef,
                redactedCommand.snapshotJson(),
                redactedCommand.redactionProfile(),
                redactedCommand.configVersionRefsJson(),
                redactedCommand.result(),
                redactedCommand.reasonCode(),
                redactedCommand.occurredAt(),
                redactedCommand.retentionUntil(),
                redactedCommand.legalHold(),
                integrityHash,
                previousHash);
        AuditRecord saved = auditRecordRepository.save(record);
        outboxRecorder.record(toOutboxCommand(saved, redactedCommand.idempotencyKey()));
        metrics.recordWrite(Duration.ofNanos(System.nanoTime() - started));
        return saved;
    }

    private void requireCorrelationContextMatchesCommand(AuditRecordCommand command) {
        CorrelationContext.Data context = CorrelationContext.get();
        if (!context.correlationId().getValue().equals(command.correlationId())) {
            throw new IllegalArgumentException("correlationId must match current CorrelationContext");
        }
    }

    private AuditRecordCommand withRedactedSnapshot(AuditRecordCommand command) {
        try {
            byte[] redactedSnapshot = redactionPolicy.redact(command.snapshotJson());
            return new AuditRecordCommand(
                    command.tenantId(),
                    command.action(),
                    command.subjectType(),
                    command.subjectId(),
                    command.subjectVersion(),
                    command.actorType(),
                    command.actorId(),
                    command.actorDisplay(),
                    command.correlationId(),
                    command.causationId(),
                    command.requestId(),
                    command.sourceIpHash(),
                    command.userAgentHash(),
                    command.beforeSnapshotJson(),
                    command.afterSnapshotJson(),
                    redactedSnapshot,
                    command.snapshotEncryptionKeyRef(),
                    command.redactionProfile(),
                    command.configVersionRefsJson(),
                    command.result(),
                    command.reasonCode(),
                    command.occurredAt(),
                    command.retentionUntil(),
                    command.legalHold(),
                    command.idempotencyKey());
        } catch (IllegalArgumentException ex) {
            metrics.recordRedactionFailure();
            throw ex;
        }
    }

    private UUID saveSnapshot(UUID tenantId, byte[] encryptedSnapshotJson, String encryptionKeyRef) {
        if (encryptedSnapshotJson == null || encryptedSnapshotJson.length == 0) {
            return null;
        }
        requireText(encryptionKeyRef, "snapshotEncryptionKeyRef is required when snapshots are provided");
        AuditSnapshot snapshot = AuditSnapshot.create(tenantId, sha256Hex(encryptedSnapshotJson), encryptionKeyRef, encryptedSnapshotJson);
        return auditSnapshotRepository.save(snapshot).getId();
    }

    private OutboxRecordCommand toOutboxCommand(AuditRecord record, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", record.getId().toString());
        payload.put("tenantId", record.getTenantId().toString());
        payload.put("status", "CREATED");
        payload.put("version", 1);
        payload.put("summary", record.getAction() + " " + record.getSubjectType() + ":" + record.getSubjectId());
        payload.put("sourceRefs", java.util.List.of(record.getSubjectType() + ":" + record.getSubjectId()));
        payload.put("auditRecordId", record.getId().toString());
        payload.put("subject", Map.of("type", record.getSubjectType(), "id", record.getSubjectId()));
        payload.put("action", record.getAction());
        payload.put("actor", Map.of("type", record.getActorType(), "id", record.getActorId()));
        payload.put("result", record.getResult());
        payload.put("occurredAt", record.getOccurredAt().toString());
        payload.put("integrityHash", record.getIntegrityHash());
        payload.put("retentionUntil", record.getRetentionUntil().toString());
        payload.put("legalHold", record.isLegalHold());
        return new OutboxRecordCommand(
                record.getTenantId(),
                "audit-record",
                record.getId().toString(),
                1L,
                AUDIT_RECORD_CREATED_EVENT,
                1,
                record.getTenantId() + ":" + record.getId(),
                record.getTenantId().toString(),
                payload,
                record.getCorrelationId(),
                record.getCausationId() == null ? record.getEventId() : record.getCausationId(),
                record.getActorId(),
                idempotencyKey,
                AUDIT_RECORD_SCHEMA_REF);
    }

    private void validate(AuditRecordCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        requireText(command.action(), "action is required");
        requireText(command.subjectType(), "subjectType is required");
        requireText(command.subjectId(), "subjectId is required");
        requireText(command.actorType(), "actorType is required");
        requireText(command.actorId(), "actorId is required");
        Objects.requireNonNull(command.correlationId(), "correlationId is required");
        Objects.requireNonNull(command.requestId(), "requestId is required");
        requireText(command.redactionProfile(), "redactionProfile is required");
        requireBytes(command.snapshotJson(), "snapshotJson is required and must already be redacted");
        requireBytes(command.configVersionRefsJson(), "configVersionRefsJson is required");
        requireText(command.result(), "result is required");
        Objects.requireNonNull(command.occurredAt(), "occurredAt is required");
        Objects.requireNonNull(command.retentionUntil(), "retentionUntil is required");
        requireText(command.idempotencyKey(), "idempotencyKey is required");
        if (command.occurredAt().isAfter(Instant.now().plusSeconds(60))) {
            throw new IllegalArgumentException("occurredAt cannot be in the future");
        }
    }

    private static void requireBytes(byte[] value, String message) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    static String sha256Hex(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }
}
