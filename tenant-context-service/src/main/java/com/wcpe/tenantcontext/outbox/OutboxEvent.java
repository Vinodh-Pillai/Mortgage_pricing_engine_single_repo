package com.wcpe.tenantcontext.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OutboxEvent(
    String tenantId,
    UUID eventId,
    String aggregateType,
    String aggregateId,
    String topic,
    String partitionKey,
    String schemaRef,
    String eventName,
    int eventVersion,
    String envelopeJson,
    String payloadHash,
    OutboxStatus status,
    int attemptCount,
    Instant nextAttemptAt,
    Instant createdAt,
    Instant updatedAt,
    Instant publishedAt,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    List<PublishAttempt> attempts,
    String lastErrorCode,
    String lastErrorMessage,
    String quarantineReason
) {
    public OutboxEvent {
        if (tenantId == null || tenantId.isBlank()) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "tenantId is required");
        }
        if (eventId == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "eventId is required");
        }
        if (payloadHash == null || payloadHash.isBlank()) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "payloadHash is required");
        }
        if (status == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "status is required");
        }
        if (attemptCount < 0) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "attemptCount cannot be negative");
        }
        if (createdAt == null || updatedAt == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "timestamps are required");
        }
        attempts = List.copyOf(attempts == null ? List.of() : attempts);
        causationId = causationId == null ? "" : causationId.trim();
        lastErrorCode = lastErrorCode == null ? "" : lastErrorCode.trim();
        lastErrorMessage = lastErrorMessage == null ? "" : lastErrorMessage.trim();
        quarantineReason = quarantineReason == null ? "" : quarantineReason.trim();
    }

    boolean samePayload(String candidateHash) {
        return payloadHash.equals(candidateHash);
    }

    OutboxEvent publishing(Instant now) {
        ensureNotPublished();
        return copyWith(OutboxStatus.PUBLISHING, attemptCount, null, updatedAtOrNow(now), publishedAt,
            attempts, "", "", quarantineReason);
    }

    OutboxEvent published(Instant now) {
        ensureNotTerminalForMutation();
        return copyWith(OutboxStatus.PUBLISHED, attemptCount, null, updatedAtOrNow(now), updatedAtOrNow(now),
            attempts, "", "", quarantineReason);
    }

    OutboxEvent failed(String errorCode, String message, int maxAttempts, Instant nextAttemptAt, Instant now) {
        ensureCanRecordFailure();
        if (maxAttempts < 1) {
            throw new OutboxException("OUTBOX_RETRY_CONFIG_INVALID", "maxAttempts must be positive");
        }
        int nextAttemptCount = attemptCount + 1;
        List<PublishAttempt> nextAttempts = appendAttempt(nextAttemptCount, now, errorCode, message);
        OutboxStatus nextStatus = nextAttemptCount >= maxAttempts ? OutboxStatus.DLQ : OutboxStatus.RETRY_WAIT;
        Instant retryAt = nextStatus == OutboxStatus.RETRY_WAIT ? requireInstant(nextAttemptAt, "nextAttemptAt") : null;
        return copyWith(nextStatus, nextAttemptCount, retryAt, updatedAtOrNow(now), publishedAt, nextAttempts,
            required(errorCode, "errorCode"), message == null ? "" : message.trim(), quarantineReason);
    }

    OutboxEvent quarantined(String reason, Instant now) {
        ensureNotPublished();
        return copyWith(OutboxStatus.QUARANTINED, attemptCount, nextAttemptAt, updatedAtOrNow(now), publishedAt,
            attempts, lastErrorCode, lastErrorMessage, required(reason, "reason"));
    }

    private void ensureNotPublished() {
        if (status == OutboxStatus.PUBLISHED) {
            throw new OutboxException("OUTBOX_EVENT_ALREADY_PUBLISHED", "published outbox events are immutable");
        }
    }

    private void ensureCanRecordFailure() {
        ensureNotPublished();
        if (status == OutboxStatus.DLQ || status == OutboxStatus.QUARANTINED) {
            throw new OutboxException("OUTBOX_RETRY_NOT_ALLOWED", "terminal outbox event cannot record another publish failure");
        }
    }

    private void ensureNotTerminalForMutation() {
        if (status == OutboxStatus.DLQ || status == OutboxStatus.QUARANTINED) {
            throw new OutboxException("OUTBOX_RETRY_NOT_ALLOWED", "terminal outbox event cannot be published");
        }
    }

    private List<PublishAttempt> appendAttempt(int nextAttemptCount, Instant now, String errorCode, String message) {
        java.util.ArrayList<PublishAttempt> nextAttempts = new java.util.ArrayList<>(attempts);
        nextAttempts.add(new PublishAttempt(nextAttemptCount, updatedAtOrNow(now), required(errorCode, "errorCode"), message));
        return List.copyOf(nextAttempts);
    }

    private OutboxEvent copyWith(
        OutboxStatus nextStatus,
        int nextAttemptCount,
        Instant nextAttemptAt,
        Instant nextUpdatedAt,
        Instant nextPublishedAt,
        List<PublishAttempt> nextAttempts,
        String nextLastErrorCode,
        String nextLastErrorMessage,
        String nextQuarantineReason
    ) {
        return new OutboxEvent(tenantId, eventId, aggregateType, aggregateId, topic, partitionKey, schemaRef,
            eventName, eventVersion, envelopeJson, payloadHash, nextStatus, nextAttemptCount, nextAttemptAt,
            createdAt, nextUpdatedAt, nextPublishedAt, actorId, correlationId, causationId, idempotencyKey, nextAttempts,
            nextLastErrorCode, nextLastErrorMessage, nextQuarantineReason);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }

    private static Instant requireInstant(Instant value, String fieldName) {
        if (value == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", fieldName + " is required");
        }
        return value;
    }

    private static Instant updatedAtOrNow(Instant now) {
        return requireInstant(now, "now");
    }
}
