package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.AuditRecord;
import com.wcpe.auditreplay.domain.LockReplayArtifact;
import com.wcpe.auditreplay.domain.LockReplayRun;
import com.wcpe.auditreplay.domain.LockReplayStatus;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.LockReplayArtifactRepository;
import com.wcpe.auditreplay.repository.LockReplayRunRepository;
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
public class LockReplayService {

    static final String LOCK_REPLAY_REQUESTED_EVENT = "LockReplayRequested.v1";
    static final String LOCK_REPLAY_COMPLETED_EVENT = "LockReplayCompleted.v1";
    static final String LOCK_REPLAY_SCHEMA_REF = "lock-replay-v1";

    private final AuditRecordRepository auditRecordRepository;
    private final LockReplayRunRepository runRepository;
    private final LockReplayArtifactRepository artifactRepository;
    private final OutboxRecorder outboxRecorder;
    private final ObjectMapper objectMapper;
    private final LockReplayMetrics metrics;

    public LockReplayService(
            AuditRecordRepository auditRecordRepository,
            LockReplayRunRepository runRepository,
            LockReplayArtifactRepository artifactRepository,
            OutboxRecorder outboxRecorder,
            ObjectMapper objectMapper,
            LockReplayMetrics metrics) {
        this.auditRecordRepository = auditRecordRepository;
        this.runRepository = runRepository;
        this.artifactRepository = artifactRepository;
        this.outboxRecorder = outboxRecorder;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    LockReplayService(
            AuditRecordRepository auditRecordRepository,
            LockReplayRunRepository runRepository,
            LockReplayArtifactRepository artifactRepository,
            OutboxRecorder outboxRecorder,
            ObjectMapper objectMapper) {
        this(auditRecordRepository, runRepository, artifactRepository, outboxRecorder, objectMapper, new LockReplayMetrics());
    }

    @Transactional
    public LockReplayResult startReplay(LockReplayCommand command) {
        long startedNanos = System.nanoTime();
        validate(command);
        Optional<LockReplayRun> existing = runRepository.findByTenantIdAndIdempotencyKey(command.tenantId(), command.idempotencyKey());
        String requestHash = requestHash(command);
        if (existing.isPresent()) {
            LockReplayRun run = existing.get();
            if (!run.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException("Idempotency-Key was already used for a different lock replay request");
            }
            return new LockReplayResult(run, readDiff(run), evidenceExportRef(run.getRunId()));
        }

        AuditRecord auditRecord = resolveSourceAuditRecord(command);
        ReplaySource replaySource = assembleReplaySource(auditRecord);
        Instant startedAt = Instant.now();
        UUID runId = UUID.nameUUIDFromBytes((command.tenantId() + ":" + command.idempotencyKey()).getBytes(StandardCharsets.UTF_8));
        LockReplayStatus status = classify(command.expectedHash(), auditRecord.getIntegrityHash());
        String mismatchCode = status == LockReplayStatus.MISMATCH ? mismatchCode(command.decisionType()) : null;
        byte[] diffJson = writeJson(diff(command, auditRecord, replaySource, status, mismatchCode));
        byte[] ledgerJson = writeJson(ledger(command, auditRecord, replaySource, status));
        String artifactHash = AuditRecorder.sha256Hex(diffJson);
        LockReplayRun run = LockReplayRun.completed(
                command.tenantId(),
                runId,
                auditRecord.getSubjectId(),
                auditRecord.getId(),
                command.requestedBy(),
                command.reason(),
                command.mode(),
                command.decisionType(),
                status,
                auditRecord.getIntegrityHash(),
                auditRecord.getIntegrityHash(),
                mismatchCode,
                replaySource.lockPolicyVersionRefs(),
                replaySource.marketSnapshotRef(),
                replaySource.extensionPolicyRef(),
                "audit-record:" + auditRecord.getId(),
                startedAt,
                Instant.now(),
                command.correlationId(),
                command.idempotencyKey(),
                requestHash);
        LockReplayRun saved = runRepository.save(run);
        artifactRepository.save(LockReplayArtifact.create(
                command.tenantId(),
                saved.getRunId(),
                auditRecord.getSnapshotJson(),
                ledgerJson,
                diffJson,
                evidenceExportRef(saved.getRunId()),
                artifactHash));
        outboxRecorder.record(toOutboxCommand(saved, LOCK_REPLAY_REQUESTED_EVENT, "requested", command.idempotencyKey()));
        outboxRecorder.record(toOutboxCommand(saved, LOCK_REPLAY_COMPLETED_EVENT, "completed", command.idempotencyKey()));
        metrics.record(status, Duration.ofNanos(System.nanoTime() - startedNanos));
        return new LockReplayResult(saved, readJson(diffJson), evidenceExportRef(saved.getRunId()));
    }

    @Transactional(readOnly = true)
    public LockReplayResult getReplay(UUID tenantId, UUID runId) {
        LockReplayRun run = runRepository.findByTenantIdAndRunId(tenantId, runId)
                .orElseThrow(() -> new LockReplayNotFoundException("Lock replay run not found"));
        return new LockReplayResult(run, readDiff(run), evidenceExportRef(run.getRunId()));
    }

    @Transactional(readOnly = true)
    public JsonNode getDiff(UUID tenantId, UUID runId) {
        LockReplayRun run = runRepository.findByTenantIdAndRunId(tenantId, runId)
                .orElseThrow(() -> new LockReplayNotFoundException("Lock replay run not found"));
        return readDiff(run);
    }

    private AuditRecord resolveSourceAuditRecord(LockReplayCommand command) {
        if (command.sourceAuditRecordId() != null) {
            return auditRecordRepository.findByTenantIdAndId(command.tenantId(), command.sourceAuditRecordId())
                    .orElseThrow(() -> new LockReplayNotFoundException("Source audit record not found"));
        }
        requireText(command.sourceLockId(), "sourceAuditRecordId or sourceLockId is required");
        return auditRecordRepository.findTopByTenantIdAndSubjectTypeAndSubjectIdOrderByOccurredAtDescCreatedAtDesc(
                        command.tenantId(), "lock", command.sourceLockId())
                .orElseThrow(() -> new LockReplayNotFoundException("Source lock audit record not found"));
    }

    private ReplaySource assembleReplaySource(AuditRecord auditRecord) {
        requireText(auditRecord.getIntegrityHash(), "source audit record integrityHash is required");
        requireBytes(auditRecord.getSnapshotJson(), "source audit snapshot is required");
        requireBytes(auditRecord.getConfigVersionRefs(), "source lock policy version refs are required");
        JsonNode snapshot = readJson(auditRecord.getSnapshotJson());
        JsonNode configRefs = readJson(auditRecord.getConfigVersionRefs());
        String marketSnapshotRef = firstText(snapshot, "marketSnapshotRef", "market_snapshot_ref", "marketDataSnapshotRef");
        if (marketSnapshotRef == null) {
            marketSnapshotRef = firstText(configRefs, "marketSnapshotRef", "market_snapshot_ref", "marketDataSnapshotRef");
        }
        if (marketSnapshotRef == null) {
            metrics.recordMissingSnapshot();
            throw new IllegalArgumentException("marketSnapshotRef is required for lock replay");
        }
        String extensionPolicyRef = firstText(snapshot, "extensionPolicyRef", "extension_policy_ref");
        if (extensionPolicyRef == null) {
            extensionPolicyRef = firstText(configRefs, "extensionPolicyRef", "extension_policy_ref");
        }
        return new ReplaySource(marketSnapshotRef, configRefs, auditRecord.getConfigVersionRefs(), extensionPolicyRef);
    }

    private LockReplayStatus classify(String expectedHash, String replayHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return LockReplayStatus.MATCH;
        }
        return expectedHash.equals(replayHash) ? LockReplayStatus.MATCH : LockReplayStatus.MISMATCH;
    }

    private String mismatchCode(String decisionType) {
        String normalized = decisionType == null ? "" : decisionType.toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("EXPIRATION")) {
            return "EXPIRATION_MISMATCH";
        }
        if (normalized.contains("EXTENSION")) {
            return "EXTENSION_FEE_MISMATCH";
        }
        if (normalized.contains("CANCEL")) {
            return "CANCELLATION_MISMATCH";
        }
        if (normalized.contains("ELIGIBILITY")) {
            return "ELIGIBILITY_MISMATCH";
        }
        if (normalized.contains("PRICE")) {
            return "LOCK_PRICE_MISMATCH";
        }
        return "LOCK_RESULT_HASH_MISMATCH";
    }

    private Map<String, Object> diff(
            LockReplayCommand command,
            AuditRecord auditRecord,
            ReplaySource replaySource,
            LockReplayStatus status,
            String mismatchCode) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("classification", status.name());
        diff.put("mismatchCode", mismatchCode);
        diff.put("sourceAuditRecordId", auditRecord.getId().toString());
        diff.put("sourceLockId", auditRecord.getSubjectId());
        diff.put("decisionType", command.decisionType());
        diff.put("originalHash", auditRecord.getIntegrityHash());
        diff.put("replayHash", auditRecord.getIntegrityHash());
        diff.put("expectedHash", command.expectedHash());
        diff.put("marketSnapshotRef", replaySource.marketSnapshotRef());
        diff.put("extensionPolicyRef", replaySource.extensionPolicyRef());
        diff.put("ledgerDiff", status == LockReplayStatus.MISMATCH
                ? java.util.List.of(mismatchCode + ": expectedHash does not match immutable lock replay hash")
                : java.util.List.of());
        diff.put("borrowerPiiRedacted", true);
        diff.put("lockStateMutated", false);
        diff.put("currentMarketDataUsed", false);
        diff.put("lockPolicyVersionRefsHash", AuditRecorder.sha256Hex(writeJson(replaySource.lockPolicyVersionRefsJson())));
        return diff;
    }

    private Map<String, Object> ledger(LockReplayCommand command, AuditRecord auditRecord, ReplaySource replaySource, LockReplayStatus status) {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("mode", command.mode().name());
        ledger.put("decisionType", command.decisionType());
        ledger.put("engine", "AUDIT_IMMUTABLE_LOCK_REPLAY");
        ledger.put("sourceAuditRecordId", auditRecord.getId().toString());
        ledger.put("sourceLockId", auditRecord.getSubjectId());
        ledger.put("marketSnapshotRef", replaySource.marketSnapshotRef());
        ledger.put("extensionPolicyRef", replaySource.extensionPolicyRef());
        ledger.put("lockPolicyVersionRefsHash", AuditRecorder.sha256Hex(writeJson(replaySource.lockPolicyVersionRefsJson())));
        ledger.put("snapshotHash", AuditRecorder.sha256Hex(auditRecord.getSnapshotJson()));
        ledger.put("result", status.name());
        ledger.put("lockStateMutated", false);
        ledger.put("currentMarketDataUsed", false);
        return ledger;
    }

    private OutboxRecordCommand toOutboxCommand(LockReplayRun run, String eventType, String phase, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", run.getRunId().toString());
        payload.put("tenantId", run.getTenantId().toString());
        payload.put("status", run.getStatus().name());
        payload.put("version", 1);
        payload.put("summary", "Lock replay " + phase + " for " + run.getSourceLockId());
        payload.put("sourceRefs", java.util.List.of("audit-record:" + run.getSourceAuditRecordId()));
        payload.put("runId", run.getRunId().toString());
        payload.put("decisionType", run.getDecisionType());
        payload.put("classification", run.getStatus().name());
        payload.put("originalHash", run.getOriginalHash());
        payload.put("replayHash", run.getReplayHash());
        payload.put("mismatchCode", run.getMismatchCode());
        payload.put("marketSnapshotRef", run.getMarketSnapshotRef());
        payload.put("extensionPolicyRef", run.getExtensionPolicyRef());
        payload.put("lockStateMutated", false);
        return new OutboxRecordCommand(
                run.getTenantId(),
                "lock-replay-run",
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
                LOCK_REPLAY_SCHEMA_REF);
    }

    private JsonNode readDiff(LockReplayRun run) {
        return artifactRepository.findByTenantIdAndRunId(run.getTenantId(), run.getRunId())
                .map(artifact -> readJson(artifact.getDiffJson()))
                .orElseThrow(() -> new LockReplayNotFoundException("Lock replay diff not found"));
    }

    private String requestHash(LockReplayCommand command) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("tenantId", command.tenantId().toString());
        canonical.put("sourceAuditRecordId", command.sourceAuditRecordId() == null ? null : command.sourceAuditRecordId().toString());
        canonical.put("sourceLockId", command.sourceLockId());
        canonical.put("decisionType", command.decisionType());
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
            throw new IllegalArgumentException("Lock replay payload is not JSON serializable", ex);
        }
    }

    private JsonNode readJson(byte[] value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Lock replay artifact JSON cannot be parsed", ex);
        }
    }

    private String evidenceExportRef(UUID runId) {
        return "audit-replay://lock-replays/" + runId + "/diff";
    }

    private void validate(LockReplayCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        Objects.requireNonNull(command.mode(), "mode is required");
        requireText(command.decisionType(), "decisionType is required");
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

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode found = node.findValue(fieldName);
            if (found != null && !found.isNull()) {
                if (found.isTextual() || found.isNumber() || found.isBoolean()) {
                    return found.asText();
                }
                return found.toString();
            }
        }
        return null;
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

    private record ReplaySource(
            String marketSnapshotRef,
            JsonNode lockPolicyVersionRefsJson,
            byte[] lockPolicyVersionRefs,
            String extensionPolicyRef) {
    }

    public static class LockReplayNotFoundException extends RuntimeException {
        public LockReplayNotFoundException(String message) { super(message); }
    }

    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }
}
