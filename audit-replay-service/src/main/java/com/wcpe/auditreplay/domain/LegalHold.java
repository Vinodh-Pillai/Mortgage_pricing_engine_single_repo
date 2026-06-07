package com.wcpe.auditreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "legal_holds")
public class LegalHold {

    @Id
    @Column(name = "hold_id", nullable = false, updatable = false)
    private UUID holdId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "case_id", length = 160, nullable = false)
    private String caseId;

    @Column(name = "scope_json", columnDefinition = "jsonb", nullable = false)
    private byte[] scopeJson;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private LegalHoldStatus status;

    @Column(name = "applied_by", length = 128, nullable = false)
    private String appliedBy;

    @Column(name = "approved_by", length = 128, nullable = false)
    private String approvedBy;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "released_by", length = 128)
    private String releasedBy;

    @Column(name = "release_approved_by", length = 128)
    private String releaseApprovedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "idempotency_key", length = 160, nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LegalHold() {
        // JPA only.
    }

    public static LegalHold apply(
            UUID holdId,
            UUID tenantId,
            String caseId,
            byte[] scopeJson,
            String reason,
            String appliedBy,
            String approvedBy,
            UUID correlationId,
            String idempotencyKey) {
        Objects.requireNonNull(holdId, "holdId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        requireText(caseId, "caseId is required");
        requireBytes(scopeJson, "scope is required");
        requireText(reason, "reason is required");
        requireText(appliedBy, "appliedBy is required");
        requireText(approvedBy, "approvedBy is required");
        if (appliedBy.equals(approvedBy)) {
            throw new IllegalArgumentException("appliedBy cannot approve the same legal hold");
        }
        Objects.requireNonNull(correlationId, "correlationId is required");
        requireText(idempotencyKey, "Idempotency-Key is required");
        LegalHold hold = new LegalHold();
        hold.holdId = holdId;
        hold.tenantId = tenantId;
        hold.caseId = caseId;
        hold.scopeJson = scopeJson.clone();
        hold.reason = reason;
        hold.status = LegalHoldStatus.ACTIVE;
        hold.appliedBy = appliedBy;
        hold.approvedBy = approvedBy;
        hold.appliedAt = Instant.now();
        hold.correlationId = correlationId;
        hold.idempotencyKey = idempotencyKey;
        return hold;
    }

    public void release(String releasedBy, String releaseApprovedBy) {
        if (status != LegalHoldStatus.ACTIVE) {
            throw new IllegalStateException("Only active legal holds can be released");
        }
        requireText(releasedBy, "releasedBy is required");
        requireText(releaseApprovedBy, "releaseApprovedBy is required");
        if (releasedBy.equals(releaseApprovedBy)) {
            throw new IllegalArgumentException("releasedBy cannot approve the same legal hold release");
        }
        this.status = LegalHoldStatus.RELEASED;
        this.releasedBy = releasedBy;
        this.releaseApprovedBy = releaseApprovedBy;
        this.releasedAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getHoldId() { return holdId; }
    public UUID getTenantId() { return tenantId; }
    public String getCaseId() { return caseId; }
    public byte[] getScopeJson() { return scopeJson == null ? null : scopeJson.clone(); }
    public String getReason() { return reason; }
    public LegalHoldStatus getStatus() { return status; }
    public String getAppliedBy() { return appliedBy; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getAppliedAt() { return appliedAt; }
    public String getReleasedBy() { return releasedBy; }
    public String getReleaseApprovedBy() { return releaseApprovedBy; }
    public Instant getReleasedAt() { return releasedAt; }
    public UUID getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }

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
