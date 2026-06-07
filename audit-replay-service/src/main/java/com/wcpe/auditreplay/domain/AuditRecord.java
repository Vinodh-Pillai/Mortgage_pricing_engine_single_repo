package com.wcpe.auditreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_records")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "action", length = 120, nullable = false, updatable = false)
    private String action;

    @Column(name = "subject_type", length = 80, nullable = false, updatable = false)
    private String subjectType;

    @Column(name = "subject_id", length = 140, nullable = false, updatable = false)
    private String subjectId;

    @Column(name = "subject_version", updatable = false)
    private Long subjectVersion;

    @Column(name = "actor_type", length = 40, nullable = false, updatable = false)
    private String actorType;

    @Column(name = "actor_id", length = 120, nullable = false, updatable = false)
    private String actorId;

    @Column(name = "actor_display", length = 160, updatable = false)
    private String actorDisplay;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(name = "causation_id", updatable = false)
    private UUID causationId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "source_ip_hash", length = 128, updatable = false)
    private String sourceIpHash;

    @Column(name = "user_agent_hash", length = 128, updatable = false)
    private String userAgentHash;

    @Column(name = "before_ref", updatable = false)
    private UUID beforeRef;

    @Column(name = "after_ref", updatable = false)
    private UUID afterRef;

    @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false, updatable = false)
    private byte[] snapshotJson;

    @Column(name = "redaction_profile", length = 80, nullable = false, updatable = false)
    private String redactionProfile;

    @Column(name = "config_version_refs", columnDefinition = "jsonb", nullable = false, updatable = false)
    private byte[] configVersionRefs;

    @Column(name = "result", length = 32, nullable = false, updatable = false)
    private String result;

    @Column(name = "reason_code", length = 80, updatable = false)
    private String reasonCode;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "retention_until", nullable = false, updatable = false)
    private LocalDate retentionUntil;

    @Column(name = "legal_hold", nullable = false, updatable = false)
    private boolean legalHold;

    @Column(name = "integrity_hash", length = 128, nullable = false, updatable = false)
    private String integrityHash;

    @Column(name = "previous_hash", length = 128, updatable = false)
    private String previousHash;

    protected AuditRecord() {
        // JPA only.
    }

    public static AuditRecord create(
            UUID tenantId,
            UUID eventId,
            String action,
            String subjectType,
            String subjectId,
            Long subjectVersion,
            String actorType,
            String actorId,
            String actorDisplay,
            UUID correlationId,
            UUID causationId,
            UUID requestId,
            String sourceIpHash,
            String userAgentHash,
            UUID beforeRef,
            UUID afterRef,
            byte[] snapshotJson,
            String redactionProfile,
            byte[] configVersionRefs,
            String result,
            String reasonCode,
            Instant occurredAt,
            LocalDate retentionUntil,
            boolean legalHold,
            String integrityHash,
            String previousHash) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(eventId, "eventId is required");
        requireText(action, "action is required");
        requireText(subjectType, "subjectType is required");
        requireText(subjectId, "subjectId is required");
        requireText(actorType, "actorType is required");
        requireText(actorId, "actorId is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
        Objects.requireNonNull(requestId, "requestId is required");
        Objects.requireNonNull(snapshotJson, "snapshotJson is required");
        requireText(redactionProfile, "redactionProfile is required");
        Objects.requireNonNull(configVersionRefs, "configVersionRefs is required");
        requireText(result, "result is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(retentionUntil, "retentionUntil is required");
        requireText(integrityHash, "integrityHash is required");
        AuditRecord record = new AuditRecord();
        record.id = UUID.randomUUID();
        record.tenantId = tenantId;
        record.eventId = eventId;
        record.action = action;
        record.subjectType = subjectType;
        record.subjectId = subjectId;
        record.subjectVersion = subjectVersion;
        record.actorType = actorType;
        record.actorId = actorId;
        record.actorDisplay = actorDisplay;
        record.correlationId = correlationId;
        record.causationId = causationId;
        record.requestId = requestId;
        record.sourceIpHash = sourceIpHash;
        record.userAgentHash = userAgentHash;
        record.beforeRef = beforeRef;
        record.afterRef = afterRef;
        record.snapshotJson = snapshotJson.clone();
        record.redactionProfile = redactionProfile;
        record.configVersionRefs = configVersionRefs.clone();
        record.result = result;
        record.reasonCode = reasonCode;
        record.occurredAt = occurredAt;
        record.createdAt = Instant.now();
        record.retentionUntil = retentionUntil;
        record.legalHold = legalHold;
        record.integrityHash = integrityHash;
        record.previousHash = previousHash;
        return record;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    @PreRemove
    void rejectMutation() {
        throw new UnsupportedOperationException("Audit records are append-only");
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEventId() { return eventId; }
    public String getAction() { return action; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public Long getSubjectVersion() { return subjectVersion; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getActorDisplay() { return actorDisplay; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getCausationId() { return causationId; }
    public UUID getRequestId() { return requestId; }
    public String getSourceIpHash() { return sourceIpHash; }
    public String getUserAgentHash() { return userAgentHash; }
    public UUID getBeforeRef() { return beforeRef; }
    public UUID getAfterRef() { return afterRef; }
    public byte[] getSnapshotJson() { return snapshotJson == null ? null : snapshotJson.clone(); }
    public String getRedactionProfile() { return redactionProfile; }
    public byte[] getConfigVersionRefs() { return configVersionRefs == null ? null : configVersionRefs.clone(); }
    public String getResult() { return result; }
    public String getReasonCode() { return reasonCode; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public LocalDate getRetentionUntil() { return retentionUntil; }
    public boolean isLegalHold() { return legalHold; }
    public String getIntegrityHash() { return integrityHash; }
    public String getPreviousHash() { return previousHash; }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
