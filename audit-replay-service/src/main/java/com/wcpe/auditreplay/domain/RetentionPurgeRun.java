package com.wcpe.auditreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "retention_purge_runs")
public class RetentionPurgeRun {

    @Id
    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cutoff", nullable = false)
    private LocalDate cutoff;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "purged_count", nullable = false)
    private int purgedCount;

    @Column(name = "skipped_held_count", nullable = false)
    private int skippedHeldCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private RetentionPurgeRunStatus status;

    @Column(name = "report_json", columnDefinition = "jsonb", nullable = false)
    private byte[] reportJson;

    @Column(name = "report_hash", length = 128, nullable = false)
    private String reportHash;

    @Column(name = "requested_by", length = 128, nullable = false)
    private String requestedBy;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "idempotency_key", length = 160, nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RetentionPurgeRun() {
        // JPA only.
    }

    public static RetentionPurgeRun create(
            UUID runId,
            UUID tenantId,
            LocalDate cutoff,
            int candidateCount,
            int purgedCount,
            int skippedHeldCount,
            RetentionPurgeRunStatus status,
            byte[] reportJson,
            String reportHash,
            String requestedBy,
            UUID correlationId,
            String idempotencyKey) {
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(cutoff, "cutoff is required");
        Objects.requireNonNull(status, "status is required");
        requireBytes(reportJson, "reportJson is required");
        requireText(reportHash, "reportHash is required");
        requireText(requestedBy, "requestedBy is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
        requireText(idempotencyKey, "Idempotency-Key is required");
        RetentionPurgeRun run = new RetentionPurgeRun();
        run.runId = runId;
        run.tenantId = tenantId;
        run.cutoff = cutoff;
        run.candidateCount = candidateCount;
        run.purgedCount = purgedCount;
        run.skippedHeldCount = skippedHeldCount;
        run.status = status;
        run.reportJson = reportJson.clone();
        run.reportHash = reportHash;
        run.requestedBy = requestedBy;
        run.correlationId = correlationId;
        run.idempotencyKey = idempotencyKey;
        return run;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getRunId() { return runId; }
    public UUID getTenantId() { return tenantId; }
    public LocalDate getCutoff() { return cutoff; }
    public int getCandidateCount() { return candidateCount; }
    public int getPurgedCount() { return purgedCount; }
    public int getSkippedHeldCount() { return skippedHeldCount; }
    public RetentionPurgeRunStatus getStatus() { return status; }
    public byte[] getReportJson() { return reportJson == null ? null : reportJson.clone(); }
    public String getReportHash() { return reportHash; }
    public String getRequestedBy() { return requestedBy; }
    public UUID getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }

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
}
