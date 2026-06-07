package com.wcpe.auditreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "legal_hold_items")
public class LegalHoldItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "hold_id", nullable = false, updatable = false)
    private UUID holdId;

    @Column(name = "subject_type", length = 80, nullable = false)
    private String subjectType;

    @Column(name = "subject_id", length = 160, nullable = false)
    private String subjectId;

    @Column(name = "audit_record_id")
    private UUID auditRecordId;

    @Column(name = "export_id")
    private UUID exportId;

    @Column(name = "replay_run_id")
    private UUID replayRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private LegalHoldStatus status;

    protected LegalHoldItem() {
        // JPA only.
    }

    public static LegalHoldItem active(UUID tenantId, UUID holdId, String subjectType, String subjectId, UUID auditRecordId, UUID exportId, UUID replayRunId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(holdId, "holdId is required");
        requireText(subjectType, "subjectType is required");
        requireText(subjectId, "subjectId is required");
        LegalHoldItem item = new LegalHoldItem();
        item.tenantId = tenantId;
        item.holdId = holdId;
        item.subjectType = subjectType;
        item.subjectId = subjectId;
        item.auditRecordId = auditRecordId;
        item.exportId = exportId;
        item.replayRunId = replayRunId;
        item.status = LegalHoldStatus.ACTIVE;
        return item;
    }

    public void release() {
        status = LegalHoldStatus.RELEASED;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getHoldId() { return holdId; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public UUID getAuditRecordId() { return auditRecordId; }
    public UUID getExportId() { return exportId; }
    public UUID getReplayRunId() { return replayRunId; }
    public LegalHoldStatus getStatus() { return status; }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
