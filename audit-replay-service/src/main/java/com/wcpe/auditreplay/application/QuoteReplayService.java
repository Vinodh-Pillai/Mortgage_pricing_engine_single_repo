package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.QuoteReplayArtifact;
import com.wcpe.auditreplay.domain.QuoteReplayRun;
import com.wcpe.auditreplay.domain.QuoteReplayStatus;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.QuoteReplayArtifactRepository;
import com.wcpe.auditreplay.repository.QuoteReplayRunRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteReplayService {

    static final String QUOTE_REPLAY_REQUESTED_EVENT = "QuoteReplayRequested.v1";
    static final String QUOTE_REPLAY_COMPLETED_EVENT = "QuoteReplayCompleted.v1";
    static final String QUOTE_REPLAY_SCHEMA_REF = "quote-replay-v1";

    private final AuditRecordRepository auditRecordRepository;
    private final QuoteReplayRunRepository runRepository;
    private final QuoteReplayArtifactRepository artifactRepository;
    private final OutboxRecorder outboxRecorder;
    private final ObjectMapper objectMapper;
    private final QuoteReplayMetrics metrics;

    public QuoteReplayService(
            AuditRecordRepository auditRecordRepository,
            QuoteReplayRunRepository runRepository,
            QuoteReplayArtifactRepository artifactRepository,
            OutboxRecorder outboxRecorder,
            ObjectMapper objectMapper,
            QuoteReplayMetrics metrics) {
        this.auditRecordRepository = auditRecordRepository;
        this.runRepository = runRepository;
        this.artifactRepository = artifactRepository;
        this.outboxRecorder = outboxRecorder;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    QuoteReplayService(
            AuditRecordRepository auditRecordRepository,
            QuoteReplayRunRepository runRepository,
            QuoteReplayArtifactRepository artifactRepository,
            OutboxRecorder outboxRecorder,
            ObjectMapper objectMapper) {
        this(auditRecordRepository, runRepository, artifactRepository, outboxRecorder, objectMapper, new QuoteReplayMetrics());
    }

    @Transactional
    public QuoteReplayResult startReplay(QuoteReplayCommand command) {
        long startedNanos = System.nanoTime();
        validate(command);
        Optional<QuoteReplayRun> existing = runRepository.findByTenantIdAndIdempotencyKey(command.tenantId(), command.idempotencyKey());
        String requestHash = requestHash(command);
        if (existing.isPresent()) {
            QuoteReplayRun run = existing.get();
            if (!run.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException("Idempotency-Key was already used for a different quote replay request");
            }
            return new QuoteReplayResult(run, readDiff(run), evidenceExportRef(run.getRunId()));
        }

        AuditRecord auditRecord = resolveSourceAuditRecord(command);
        validateReplaySource(auditRecord);
        Instant startedAt = Instant.now();
        UUID runId = UUID.nameUUIDFromBytes((command.tenantId() + ":" + command.idempotencyKey()).getBytes(StandardCharsets.UTF_8));
        QuoteReplayStatus status = classify(command.expectedHash(), auditRecord.getIntegrityHash());
        String mismatchCode = status == QuoteReplayStatus.MISMATCH ? "OUTPUT_HASH_MISMATCH" : null;
        byte[] diffJson = writeJson(diff(command, auditRecord, status, mismatchCode));
        byte[] ledgerJson = writeJson(ledger(command, auditRecord, status));
        String artifactHash = AuditRecorder.sha256Hex(diffJson);
        QuoteReplayRun run = QuoteReplayRun.completed(
                command.tenantId(),
                runId,
                auditRecord.getSubjectId(),
                auditRecord.getId(),
                command.requestedBy(),
                command.reason(),
                command.mode(),
                status,
                auditRecord.getIntegrityHash(),
                auditRecord.getIntegrityHash(),
                mismatchCode,
                auditRecord.getConfigVersionRefs(),
                "audit-record:" + auditRecord.getId(),
                startedAt,
                Instant.now(),
                command.correlationId(),
                command.idempotencyKey(),
                requestHash);
        QuoteReplayRun saved = runRepository.save(run);
        artifactRepository.save(QuoteReplayArtifact.create(
                command.tenantId(),
                saved.getRunId(),
                auditRecord.getSnapshotJson(),
                ledgerJson,
                diffJson,
                evidenceExportRef(saved.getRunId()),
                artifactHash));
        outboxRecorder.record(toOutboxCommand(saved, QUOTE_REPLAY_REQUESTED_EVENT, "requested", command.idempotencyKey()));
        outboxRecorder.record(toOutboxCommand(saved, QUOTE_REPLAY_COMPLETED_EVENT, "completed", command.idempotencyKey()));
        metrics.record(status, Duration.ofNanos(System.nanoTime() - startedNanos));
        return new QuoteReplayResult(saved, readJson(diffJson), evidenceExportRef(saved.getRunId()));
    }

    @Transactional(readOnly = true)
    public QuoteReplayResult getReplay(UUID tenantId, UUID runId) {
        QuoteReplayRun run = runRepository.findByTenantIdAndRunId(tenantId, runId)
                .orElseThrow(() -> new QuoteReplayNotFoundException("Quote replay run not found"));
        return new QuoteReplayResult(run, readDiff(run), evidenceExportRef(run.getRunId()));
    }

    @Transactional(readOnly = true)
    public JsonNode getDiff(UUID tenantId, UUID runId) {
        QuoteReplayRun run = runRepository.findByTenantIdAndRunId(tenantId, runId)
                .orElseThrow(() -> new QuoteReplayNotFoundException("Quote replay run not found"));
        return readDiff(run);
    }

    private AuditRecord resolveSourceAuditRecord(QuoteReplayCommand command) {
        if (command.sourceAuditRecordId() != null) {
            return auditRecordRepository.findByTenantIdAndId(command.tenantId(), command.sourceAuditRecordId())
                    .orElseThrow(() -> new QuoteReplayNotFoundException("Source audit record not found"));
        }
        requireText(command.sourceQuoteId(), "sourceAuditRecordId or sourceQuoteId is required");
        return auditRecordRepository.findTopByTenantIdAndSubjectTypeAndSubjectIdOrderByOccurredAtDescCreatedAtDesc(
                        command.tenantId(), "quote", command.sourceQuoteId())
                .orElseThrow(() -> new QuoteReplayNotFoundException("Source quote audit record not found"));
    }

    private void validateReplaySource(AuditRecord auditRecord) {
        requireText(auditRecord.getIntegrityHash(), "source audit record integrityHash is required");
        requireBytes(auditRecord.getSnapshotJson(), "source audit snapshot is required");
        requireBytes(auditRecord.getConfigVersionRefs(), "source config version refs are required");
    }

    private QuoteReplayStatus classify(String expectedHash, String replayHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return QuoteReplayStatus.MATCH;
        }
        return expectedHash.equals(replayHash) ? QuoteReplayStatus.MATCH : QuoteReplayStatus.MISMATCH;
    }

    private Map<String, Object> diff(QuoteReplayCommand command, AuditRecord auditRecord, QuoteReplayStatus status, String mismatchCode) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("classification", status.name());
        diff.put("mismatchCode", mismatchCode);
        diff.put("sourceAuditRecordId", auditRecord.getId().toString());
        diff.put("sourceQuoteId", auditRecord.getSubjectId());
        diff.put("originalHash", auditRecord.getIntegrityHash());
        diff.put("replayHash", auditRecord.getIntegrityHash());
        diff.put("expectedHash", command.expectedHash());
        diff.put("ledgerDiff", status == QuoteReplayStatus.MISMATCH
                ? java.util.List.of("expectedHash does not match immutable audit replay hash")
                : java.util.List.of());
        diff.put("borrowerPiiRedacted", true);
        diff.put("quoteStateMutated", false);
        diff.put("configVersionRefsHash", AuditRecorder.sha256Hex(auditRecord.getConfigVersionRefs()));
        return diff;
    }

    private Map<String, Object> ledger(QuoteReplayCommand command, AuditRecord auditRecord, QuoteReplayStatus status) {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("mode", command.mode().name());
        ledger.put("engine", "AUDIT_IMMUTABLE_LOCAL_REPLAY");
        ledger.put("sourceAuditRecordId", auditRecord.getId().toString());
        ledger.put("sourceQuoteId", auditRecord.getSubjectId());
        ledger.put("configVersionRefsHash", AuditRecorder.sha256Hex(auditRecord.getConfigVersionRefs()));
        ledger.put("snapshotHash", AuditRecorder.sha256Hex(auditRecord.getSnapshotJson()));
        ledger.put("result", status.name());
        ledger.put("quoteStateMutated", false);
        return ledger;
    }

    private OutboxRecordCommand toOutboxCommand(QuoteReplayRun run, String eventType, String phase, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", run.getRunId().toString());
        payload.put("tenantId", run.getTenantId().toString());
        payload.put("status", run.getStatus().name());
        payload.put("version", 1);
        payload.put("summary", "Quote replay " + phase + " for " + run.getSourceQuoteId());
        payload.put("sourceRefs", java.util.List.of("audit-record:" + run.getSourceAuditRecordId()));
        payload.put("runId", run.getRunId().toString());
        payload.put("classification", run.getStatus().name());
        payload.put("originalHash", run.getOriginalHash());
        payload.put("replayHash", run.getReplayHash());
        payload.put("mismatchCode", run.getMismatchCode());
        payload.put("quoteStateMutated", false);
        return new OutboxRecordCommand(
                run.getTenantId(),
                "quote-replay-run",
                run.getRunId().toString(),
                1L,
                eventType,
                1,
                run.getTenantId() + ":" + run.getRunId() + ":" + phase,
                run.getTenantId().toString(),
                payload,
                run.getCorrelationId(),
                run.getRunId(),
                run.getRequestedBy(),
                idempotencyKey + ":" + phase,
                QUOTE_REPLAY_SCHEMA_REF);
    }

    private JsonNode readDiff(QuoteReplayRun run) {
        return artifactRepository.findByTenantIdAndRunId(run.getTenantId(), run.getRunId())
                .map(artifact -> readJson(artifact.getDiffJson()))
                .orElseThrow(() -> new QuoteReplayNotFoundException("Quote replay diff not found"));
    }

    private String requestHash(QuoteReplayCommand command) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("tenantId", command.tenantId().toString());
        canonical.put("sourceAuditRecordId", command.sourceAuditRecordId() == null ? null : command.sourceAuditRecordId().toString());
        canonical.put("sourceQuoteId", command.sourceQuoteId());
        canonical.put("mode", command.mode().name());
        canonical.put("reason", command.reason());
        canonical.put("requestedBy", command.requestedBy());
        canonical.put("expectedHash", command.expectedHash());
        return AuditRecorder.sha256Hex(writeJson(canonical));
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Quote replay payload is not JSON serializable", ex);
        }
    }

    private JsonNode readJson(byte[] value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Quote replay artifact JSON cannot be parsed", ex);
        }
    }

    private String evidenceExportRef(UUID runId) {
        return "audit-replay://quote-replays/" + runId + "/diff";
    }

    private void validate(QuoteReplayCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        Objects.requireNonNull(command.mode(), "mode is required");
        requireText(command.reason(), "reason is required");
        requireText(command.requestedBy(), "requestedBy is required");
        Objects.requireNonNull(command.correlationId(), "correlationId is required");
        Objects.requireNonNull(command.requestId(), "requestId is required");
        requireText(command.idempotencyKey(), "idempotencyKey is required");
        CorrelationContext.Data context = CorrelationContext.get();
        if (context != null && context.correlationId() != null && !context.correlationId().getValue().equals(command.correlationId())) {
            throw new IllegalArgumentException("correlationId must match current CorrelationContext");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireBytes(byte[] value, String message) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public static class QuoteReplayNotFoundException extends RuntimeException {
        public QuoteReplayNotFoundException(String message) { super(message); }
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }
}
