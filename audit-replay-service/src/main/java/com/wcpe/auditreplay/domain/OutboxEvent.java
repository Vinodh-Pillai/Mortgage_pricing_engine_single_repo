package com.wcpe.auditreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "aggregate_type", length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 120)
    private String aggregateId;

    @Column(name = "aggregate_version")
    private Long aggregateVersion;

    @Column(name = "event_type", length = 120)
    private String eventType;

    @Column(name = "event_version")
    private Integer eventVersion;

    @Column(name = "event_key", length = 180)
    private String eventKey;

    @Column(name = "partition_key", length = 180)
    private String partitionKey;

    @Column(name = "payload_json", columnDefinition = "jsonb")
    private byte[] payloadJson;

    @Column(name = "headers_json", columnDefinition = "jsonb")
    private byte[] headersJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "causation_id")
    private UUID causationId;

    @Column(name = "actor_id", length = 120)
    private String actorId;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "integrity_hash", length = 128)
    private String integrityHash;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    protected OutboxEvent() {
        // JPA only.
    }

    public static OutboxEvent createPending(
            UUID tenantId,
            String aggregateType,
            String aggregateId,
            Long aggregateVersion,
            String eventType,
            Integer eventVersion,
            String eventKey,
            String partitionKey,
            byte[] payloadJson,
            byte[] headersJson,
            UUID correlationId,
            UUID causationId,
            String actorId) {
        return createPending(
                tenantId,
                aggregateType,
                aggregateId,
                aggregateVersion,
                eventType,
                eventVersion,
                eventKey,
                partitionKey,
                payloadJson,
                headersJson,
                correlationId,
                causationId,
                actorId,
                null);
    }

    public static OutboxEvent createPending(
            UUID tenantId,
            String aggregateType,
            String aggregateId,
            Long aggregateVersion,
            String eventType,
            Integer eventVersion,
            String eventKey,
            String partitionKey,
            byte[] payloadJson,
            byte[] headersJson,
            UUID correlationId,
            UUID causationId,
            String actorId,
            String idempotencyKey) {
        return createPending(
                tenantId,
                aggregateType,
                aggregateId,
                aggregateVersion,
                eventType,
                eventVersion,
                eventKey,
                partitionKey,
                payloadJson,
                headersJson,
                correlationId,
                causationId,
                actorId,
                idempotencyKey,
                null);
    }

    public static OutboxEvent createPending(
            UUID tenantId,
            String aggregateType,
            String aggregateId,
            Long aggregateVersion,
            String eventType,
            Integer eventVersion,
            String eventKey,
            String partitionKey,
            byte[] payloadJson,
            byte[] headersJson,
            UUID correlationId,
            UUID causationId,
            String actorId,
            String idempotencyKey,
            String integrityHash) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        requireText(eventType, "eventType is required");
        Objects.requireNonNull(eventVersion, "eventVersion is required");
        requireText(eventKey, "eventKey is required");
        Objects.requireNonNull(payloadJson, "payloadJson is required");
        Objects.requireNonNull(headersJson, "headersJson is required");
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.tenantId = tenantId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.aggregateVersion = aggregateVersion;
        event.eventType = eventType;
        event.eventVersion = eventVersion;
        event.eventKey = eventKey;
        event.partitionKey = partitionKey;
        event.setPayloadJson(payloadJson);
        event.setHeadersJson(headersJson);
        event.status = OutboxEventStatus.PENDING;
        event.attemptCount = 0;
        event.createdAt = Instant.now();
        event.correlationId = correlationId;
        event.causationId = causationId;
        event.actorId = actorId;
        event.idempotencyKey = idempotencyKey;
        event.integrityHash = integrityHash == null ? sha256Hex(event.payloadJson) : integrityHash;
        return event;
    }

    public void markInFlight() {
        if (status != OutboxEventStatus.PENDING && status != OutboxEventStatus.FAILED) {
            throw new IllegalStateException("Only pending or failed outbox events can be claimed for publish");
        }
        status = OutboxEventStatus.IN_FLIGHT;
        nextAttemptAt = null;
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt is required");
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage, Instant nextAttemptAt, int maxAttempts) {
        this.attemptCount++;
        this.lastErrorCode = truncate(errorCode, 80);
        this.lastErrorMessage = errorMessage;
        if (this.attemptCount >= maxAttempts) {
            this.status = OutboxEventStatus.POISON;
            this.nextAttemptAt = null;
        } else {
            this.status = OutboxEventStatus.FAILED;
            this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt is required before max attempts");
        }
    }

    public void queueRetry(Instant nextAttemptAt) {
        if (status != OutboxEventStatus.FAILED) {
            throw new IllegalStateException("Only failed outbox events can be queued for retry");
        }
        this.status = OutboxEventStatus.PENDING;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt is required");
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = OutboxEventStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (integrityHash == null && payloadJson != null) {
            integrityHash = sha256Hex(payloadJson);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public Long getAggregateVersion() {
        return aggregateVersion;
    }

    public void setAggregateVersion(Long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(Integer eventVersion) {
        this.eventVersion = eventVersion;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public byte[] getPayloadJson() {
        return payloadJson == null ? null : payloadJson.clone();
    }

    public void setPayloadJson(byte[] payloadJson) {
        this.payloadJson = payloadJson == null ? null : payloadJson.clone();
        this.integrityHash = this.payloadJson == null ? null : sha256Hex(this.payloadJson);
    }

    public byte[] getHeadersJson() {
        return headersJson == null ? null : headersJson.clone();
    }

    public void setHeadersJson(byte[] headersJson) {
        this.headersJson = headersJson == null ? null : headersJson.clone();
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxEventStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public UUID getCausationId() {
        return causationId;
    }

    public void setCausationId(UUID causationId) {
        this.causationId = causationId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getIntegrityHash() {
        return integrityHash;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    private static String sha256Hex(byte[] payload) {
        if (payload == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
