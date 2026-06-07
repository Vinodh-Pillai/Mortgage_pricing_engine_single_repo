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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "retention_policies")
public class RetentionPolicy {

    @Id
    @Column(name = "policy_id", nullable = false, updatable = false)
    private UUID policyId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "evidence_type", length = 80, nullable = false)
    private String evidenceType;

    @Column(name = "jurisdiction", length = 80, nullable = false)
    private String jurisdiction;

    @Column(name = "product_filter", length = 160)
    private String productFilter;

    @Column(name = "channel_filter", length = 160)
    private String channelFilter;

    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private RetentionPolicyStatus status;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_by", length = 128, nullable = false)
    private String createdBy;

    @Column(name = "approved_by", length = 128, nullable = false)
    private String approvedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "idempotency_key", length = 160, nullable = false)
    private String idempotencyKey;

    protected RetentionPolicy() {
        // JPA only.
    }

    public static RetentionPolicy publish(
            UUID policyId,
            UUID tenantId,
            String evidenceType,
            String jurisdiction,
            String productFilter,
            String channelFilter,
            Integer retentionDays,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String createdBy,
            String approvedBy,
            UUID correlationId,
            String idempotencyKey) {
        Objects.requireNonNull(policyId, "policyId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        requireText(evidenceType, "evidenceType is required");
        requireText(jurisdiction, "jurisdiction is required");
        requirePositive(retentionDays, "tenant retentionDays policy is required");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        requireText(createdBy, "createdBy is required");
        requireText(approvedBy, "approvedBy is required");
        if (createdBy.equals(approvedBy)) {
            throw new IllegalArgumentException("createdBy cannot approve the same retention policy");
        }
        Objects.requireNonNull(correlationId, "correlationId is required");
        requireText(idempotencyKey, "Idempotency-Key is required");
        RetentionPolicy policy = new RetentionPolicy();
        policy.policyId = policyId;
        policy.tenantId = tenantId;
        policy.evidenceType = evidenceType;
        policy.jurisdiction = jurisdiction;
        policy.productFilter = productFilter;
        policy.channelFilter = channelFilter;
        policy.retentionDays = retentionDays;
        policy.status = RetentionPolicyStatus.PUBLISHED;
        policy.version = 1;
        policy.effectiveFrom = effectiveFrom;
        policy.effectiveTo = effectiveTo;
        policy.createdBy = createdBy;
        policy.approvedBy = approvedBy;
        policy.publishedAt = Instant.now();
        policy.correlationId = correlationId;
        policy.idempotencyKey = idempotencyKey;
        return policy;
    }

    public boolean overlaps(LocalDate from, LocalDate to) {
        LocalDate thisTo = effectiveTo == null ? LocalDate.MAX : effectiveTo;
        LocalDate otherTo = to == null ? LocalDate.MAX : to;
        return !effectiveFrom.isAfter(otherTo) && !from.isAfter(thisTo);
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

    public UUID getPolicyId() { return policyId; }
    public UUID getTenantId() { return tenantId; }
    public String getEvidenceType() { return evidenceType; }
    public String getJurisdiction() { return jurisdiction; }
    public String getProductFilter() { return productFilter; }
    public String getChannelFilter() { return channelFilter; }
    public Integer getRetentionDays() { return retentionDays; }
    public RetentionPolicyStatus getStatus() { return status; }
    public Integer getVersion() { return version; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getCreatedBy() { return createdBy; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }

    private static void requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
