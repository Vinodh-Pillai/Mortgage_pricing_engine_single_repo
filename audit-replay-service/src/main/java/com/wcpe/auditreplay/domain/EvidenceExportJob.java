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
@Table(name = "evidence_export_jobs")
public class EvidenceExportJob {

    @Id
    @Column(name = "export_id", nullable = false, updatable = false)
    private UUID exportId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "requested_by", length = 128, nullable = false, updatable = false)
    private String requestedBy;

    @Column(name = "purpose", length = 500, nullable = false, updatable = false)
    private String purpose;

    @Column(name = "format", length = 32, nullable = false, updatable = false)
    private String format;

    @Column(name = "redaction_profile", length = 80, nullable = false, updatable = false)
    private String redactionProfile;

    @Column(name = "source_refs", columnDefinition = "jsonb", nullable = false, updatable = false)
    private byte[] sourceRefs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private EvidenceExportStatus status;

    @Column(name = "artifact_uri", length = 240, nullable = false)
    private String artifactUri;

    @Column(name = "manifest_json", columnDefinition = "jsonb", nullable = false)
    private byte[] manifestJson;

    @Column(name = "manifest_hash", length = 128, nullable = false)
    private String manifestHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "retention_until", nullable = false)
    private LocalDate retentionUntil;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "idempotency_key", length = 160, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 128, nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EvidenceExportJob() {
        // JPA only.
    }

    public static EvidenceExportJob ready(
            UUID tenantId,
            UUID exportId,
            String requestedBy,
            String purpose,
            String format,
            String redactionProfile,
            byte[] sourceRefs,
            String artifactUri,
            byte[] manifestJson,
            String manifestHash,
            Instant expiresAt,
            LocalDate retentionUntil,
            boolean legalHold,
            UUID correlationId,
            String idempotencyKey,
            String requestHash) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(exportId, "exportId is required");
        requireText(requestedBy, "requestedBy is required");
        requireText(purpose, "purpose is required");
        requireText(format, "format is required");
        requireText(redactionProfile, "redactionProfile is required");
        Objects.requireNonNull(sourceRefs, "sourceRefs is required");
        requireText(artifactUri, "artifactUri is required");
        Objects.requireNonNull(manifestJson, "manifestJson is required");
        requireText(manifestHash, "manifestHash is required");
        Objects.requireNonNull(retentionUntil, "retentionUntil is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
        requireText(idempotencyKey, "idempotencyKey is required");
        requireText(requestHash, "requestHash is required");
        Instant now = Instant.now();
        EvidenceExportJob job = new EvidenceExportJob();
        job.tenantId = tenantId;
        job.exportId = exportId;
        job.requestedBy = requestedBy;
        job.purpose = purpose;
        job.format = format;
        job.redactionProfile = redactionProfile;
        job.sourceRefs = sourceRefs.clone();
        job.status = EvidenceExportStatus.READY;
        job.artifactUri = artifactUri;
        job.manifestJson = manifestJson.clone();
        job.manifestHash = manifestHash;
        job.expiresAt = expiresAt;
        job.retentionUntil = retentionUntil;
        job.legalHold = legalHold;
        job.correlationId = correlationId;
        job.idempotencyKey = idempotencyKey;
        job.requestHash = requestHash;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    @PrePersist
    void prePersist() {
        if (exportId == null) {
            exportId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public UUID getExportId() { return exportId; }
    public UUID getTenantId() { return tenantId; }
    public String getRequestedBy() { return requestedBy; }
    public String getPurpose() { return purpose; }
    public String getFormat() { return format; }
    public String getRedactionProfile() { return redactionProfile; }
    public byte[] getSourceRefs() { return sourceRefs == null ? null : sourceRefs.clone(); }
    public EvidenceExportStatus getStatus() { return status; }
    public String getArtifactUri() { return artifactUri; }
    public byte[] getManifestJson() { return manifestJson == null ? null : manifestJson.clone(); }
    public String getManifestHash() { return manifestHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public LocalDate getRetentionUntil() { return retentionUntil; }
    public boolean isLegalHold() { return legalHold; }
    public UUID getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
