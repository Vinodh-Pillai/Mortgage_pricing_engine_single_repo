package com.wcpe.auditreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "lock_replay_runs")
public class LockReplayRun {

    @Id
    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "source_lock_id", length = 140, updatable = false)
    private String sourceLockId;

    @Column(name = "source_audit_record_id", nullable = false, updatable = false)
    private UUID sourceAuditRecordId;

    @Column(name = "requested_by", length = 128, nullable = false, updatable = false)
    private String requestedBy;

    @Column(name = "reason", length = 500, nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 24, nullable = false, updatable = false)
    private LockReplayMode mode;

    @Column(name = "decision_type", length = 60, nullable = false, updatable = false)
    private String decisionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private LockReplayStatus status;

    @Column(name = "original_hash", length = 128, nullable = false, updatable = false)
    private String originalHash;

    @Column(name = "replay_hash", length = 128, nullable = false, updatable = false)
    private String replayHash;

    @Column(name = "mismatch_code", length = 80, updatable = false)
    private String mismatchCode;

    @Column(name = "lock_policy_version_refs", columnDefinition = "jsonb", nullable = false, updatable = false)
    private byte[] lockPolicyVersionRefs;

    @Column(name = "market_snapshot_ref", length = 180, nullable = false, updatable = false)
    private String marketSnapshotRef;

    @Column(name = "extension_policy_ref", length = 180, updatable = false)
    private String extensionPolicyRef;

    @Column(name = "event_sequence_ref", length = 180, nullable = false, updatable = false)
    private String eventSequenceRef;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "idempotency_key", length = 160, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 128, nullable = false, updatable = false)
    private String requestHash;

    protected LockReplayRun() {
        // JPA only.
    }

    public static LockReplayRun completed(
            UUID tenantId,
            UUID runId,
            String sourceLockId,
            UUID sourceAuditRecordId,
            String requestedBy,
            String reason,
            LockReplayMode mode,
            String decisionType,
            LockReplayStatus status,
            String originalHash,
            String replayHash,
            String mismatchCode,
            byte[] lockPolicyVersionRefs,
            String marketSnapshotRef,
            String extensionPolicyRef,
            String eventSequenceRef,
            Instant startedAt,
            Instant completedAt,
            UUID correlationId,
            String idempotencyKey,
            String requestHash) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(sourceAuditRecordId, "sourceAuditRecordId is required");
        requireText(requestedBy, "requestedBy is required");
        requireText(reason, "reason is required");
        Objects.requireNonNull(mode, "mode is required");
        requireText(decisionType, "decisionType is required");
        Objects.requireNonNull(status, "status is required");
        requireText(originalHash, "originalHash is required");
        requireText(replayHash, "replayHash is required");
        Objects.requireNonNull(lockPolicyVersionRefs, "lockPolicyVersionRefs is required");
        requireText(marketSnapshotRef, "marketSnapshotRef is required");
        requireText(eventSequenceRef, "eventSequenceRef is required");
        Objects.requireNonNull(startedAt, "startedAt is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
        requireText(idempotencyKey, "idempotencyKey is required");
        requireText(requestHash, "requestHash is required");
        LockReplayRun run = new LockReplayRun();
        run.tenantId = tenantId;
        run.runId = runId;
        run.sourceLockId = sourceLockId;
        run.sourceAuditRecordId = sourceAuditRecordId;
        run.requestedBy = requestedBy;
        run.reason = reason;
        run.mode = mode;
        run.decisionType = decisionType;
        run.status = status;
        run.originalHash = originalHash;
        run.replayHash = replayHash;
        run.mismatchCode = mismatchCode;
        run.lockPolicyVersionRefs = lockPolicyVersionRefs.clone();
        run.marketSnapshotRef = marketSnapshotRef;
        run.extensionPolicyRef = extensionPolicyRef;
        run.eventSequenceRef = eventSequenceRef;
        run.startedAt = startedAt;
        run.completedAt = completedAt;
        run.correlationId = correlationId;
        run.idempotencyKey = idempotencyKey;
        run.requestHash = requestHash;
        return run;
    }

    @PrePersist
    void prePersist() {
        if (runId == null) {
            runId = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        if (completedAt == null) {
            completedAt = startedAt;
        }
    }

    public UUID getRunId() { return runId; }
    public UUID getTenantId() { return tenantId; }
    public String getSourceLockId() { return sourceLockId; }
    public UUID getSourceAuditRecordId() { return sourceAuditRecordId; }
    public String getRequestedBy() { return requestedBy; }
    public String getReason() { return reason; }
    public LockReplayMode getMode() { return mode; }
    public String getDecisionType() { return decisionType; }
    public LockReplayStatus getStatus() { return status; }
    public String getOriginalHash() { return originalHash; }
    public String getReplayHash() { return replayHash; }
    public String getMismatchCode() { return mismatchCode; }
    public byte[] getLockPolicyVersionRefs() { return lockPolicyVersionRefs == null ? null : lockPolicyVersionRefs.clone(); }
    public String getMarketSnapshotRef() { return marketSnapshotRef; }
    public String getExtensionPolicyRef() { return extensionPolicyRef; }
    public String getEventSequenceRef() { return eventSequenceRef; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
