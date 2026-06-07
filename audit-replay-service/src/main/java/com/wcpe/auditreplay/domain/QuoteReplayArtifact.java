package com.wcpe.auditreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "quote_replay_artifacts")
public class QuoteReplayArtifact {

    @Id
    @Column(name = "artifact_id", nullable = false, updatable = false)
    private UUID artifactId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "input_snapshot_json", columnDefinition = "jsonb", nullable = false, updatable = false)
    private byte[] inputSnapshotJson;

    @Column(name = "replay_ledger_json", columnDefinition = "jsonb", nullable = false, updatable = false)
    private byte[] replayLedgerJson;

    @Column(name = "diff_json", columnDefinition = "jsonb", nullable = false, updatable = false)
    private byte[] diffJson;

    @Column(name = "evidence_export_ref", length = 240, nullable = false, updatable = false)
    private String evidenceExportRef;

    @Column(name = "integrity_hash", length = 128, nullable = false, updatable = false)
    private String integrityHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuoteReplayArtifact() {
        // JPA only.
    }

    public static QuoteReplayArtifact create(
            UUID tenantId,
            UUID runId,
            byte[] inputSnapshotJson,
            byte[] replayLedgerJson,
            byte[] diffJson,
            String evidenceExportRef,
            String integrityHash) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(inputSnapshotJson, "inputSnapshotJson is required");
        Objects.requireNonNull(replayLedgerJson, "replayLedgerJson is required");
        Objects.requireNonNull(diffJson, "diffJson is required");
        requireText(evidenceExportRef, "evidenceExportRef is required");
        requireText(integrityHash, "integrityHash is required");
        QuoteReplayArtifact artifact = new QuoteReplayArtifact();
        artifact.artifactId = UUID.randomUUID();
        artifact.tenantId = tenantId;
        artifact.runId = runId;
        artifact.inputSnapshotJson = inputSnapshotJson.clone();
        artifact.replayLedgerJson = replayLedgerJson.clone();
        artifact.diffJson = diffJson.clone();
        artifact.evidenceExportRef = evidenceExportRef;
        artifact.integrityHash = integrityHash;
        artifact.createdAt = Instant.now();
        return artifact;
    }

    @PrePersist
    void prePersist() {
        if (artifactId == null) {
            artifactId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getArtifactId() { return artifactId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRunId() { return runId; }
    public byte[] getInputSnapshotJson() { return inputSnapshotJson == null ? null : inputSnapshotJson.clone(); }
    public byte[] getReplayLedgerJson() { return replayLedgerJson == null ? null : replayLedgerJson.clone(); }
    public byte[] getDiffJson() { return diffJson == null ? null : diffJson.clone(); }
    public String getEvidenceExportRef() { return evidenceExportRef; }
    public String getIntegrityHash() { return integrityHash; }
    public Instant getCreatedAt() { return createdAt; }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
