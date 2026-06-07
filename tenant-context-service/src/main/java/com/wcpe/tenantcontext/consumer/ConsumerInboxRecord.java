package com.wcpe.tenantcontext.consumer;

import java.time.Instant;
import java.util.UUID;

public record ConsumerInboxRecord(
    UUID inboxId,
    String tenantId,
    String consumerName,
    UUID eventId,
    String eventName,
    String schemaRef,
    int schemaVersion,
    String payloadHash,
    String correlationId,
    String causationId,
    ProcessingStatus status,
    int attemptCount,
    Instant firstSeenAt,
    Instant lastAttemptAt,
    Instant processedAt,
    String resultHash,
    String lastErrorCode,
    String lastErrorMessage
) {
    public ConsumerInboxRecord {
        if (inboxId == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "inboxId is required");
        }
        tenantId = required(tenantId, "tenantId");
        consumerName = required(consumerName, "consumerName");
        if (eventId == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "eventId is required");
        }
        eventName = required(eventName, "eventName");
        schemaRef = required(schemaRef, "schemaRef");
        if (schemaVersion < 1) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "schemaVersion must be positive");
        }
        payloadHash = required(payloadHash, "payloadHash");
        correlationId = optional(correlationId);
        causationId = optional(causationId);
        if (status == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "status is required");
        }
        if (attemptCount < 0) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "attemptCount cannot be negative");
        }
        if (firstSeenAt == null || lastAttemptAt == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "attempt timestamps are required");
        }
        resultHash = optional(resultHash);
        lastErrorCode = optional(lastErrorCode);
        lastErrorMessage = optional(lastErrorMessage);
    }

    static ConsumerInboxRecord processing(String tenantId, String consumerName, EventEnvelope envelope, String payloadHash, Instant now) {
        return new ConsumerInboxRecord(UUID.randomUUID(), tenantId, consumerName, envelope.eventId(), envelope.eventName(),
            envelope.schemaRef(), envelope.schemaVersion(), payloadHash, envelope.correlationId(), envelope.causationId(),
            ProcessingStatus.PROCESSING, 1, now, now, null, "", "", "");
    }

    ConsumerInboxRecord processed(String resultHash, Instant now) {
        if (status == ProcessingStatus.PROCESSED) {
            throw new ConsumerInboxException("CONSUMER_INBOX_ALREADY_PROCESSED", "processed inbox records are immutable");
        }
        return new ConsumerInboxRecord(inboxId, tenantId, consumerName, eventId, eventName, schemaRef, schemaVersion,
            payloadHash, correlationId, causationId, ProcessingStatus.PROCESSED, attemptCount, firstSeenAt,
            lastAttemptAt, now, required(resultHash, "resultHash"), "", "");
    }

    boolean samePayload(String candidateHash) {
        return payloadHash.equals(candidateHash);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
