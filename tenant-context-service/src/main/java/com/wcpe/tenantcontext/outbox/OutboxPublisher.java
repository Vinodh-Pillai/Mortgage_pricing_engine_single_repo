package com.wcpe.tenantcontext.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class OutboxPublisher {
    private final InMemoryOutboxStore store;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration retryDelay;

    public OutboxPublisher(InMemoryOutboxStore store, Clock clock, int maxAttempts, Duration retryDelay) {
        if (store == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "store is required");
        }
        if (maxAttempts < 1) {
            throw new OutboxException("OUTBOX_RETRY_CONFIG_INVALID", "maxAttempts must be positive");
        }
        if (retryDelay == null || retryDelay.isNegative()) {
            throw new OutboxException("OUTBOX_RETRY_CONFIG_INVALID", "retryDelay must not be negative");
        }
        this.store = store;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    public List<OutboxEvent> dueForPublish(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "tenantId is required");
        }
        java.time.Instant now = clock.instant();
        return store.dueForPublish(tenantId.trim()).stream()
            .filter(event -> event.nextAttemptAt() == null || !event.nextAttemptAt().isAfter(now))
            .toList();
    }

    public OutboxEvent markPublishing(UUID eventId) {
        OutboxEvent event = load(eventId);
        OutboxEvent updated = event.publishing(clock.instant());
        return store.save(updated);
    }

    public OutboxEvent markPublished(UUID eventId) {
        OutboxEvent event = load(eventId);
        OutboxEvent updated = event.published(clock.instant());
        return store.save(updated);
    }

    public OutboxEvent recordFailure(UUID eventId, String errorCode, String message) {
        OutboxEvent event = load(eventId);
        OutboxEvent updated = event.failed(errorCode, message, maxAttempts, clock.instant().plus(retryDelay), clock.instant());
        return store.save(updated);
    }

    public OutboxEvent quarantine(UUID eventId, String reason) {
        OutboxEvent event = load(eventId);
        OutboxEvent updated = event.quarantined(reason, clock.instant());
        return store.save(updated);
    }

    private OutboxEvent load(UUID eventId) {
        if (eventId == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "eventId is required");
        }
        return store.findByEventId(eventId)
            .orElseThrow(() -> new OutboxException("OUTBOX_EVENT_NOT_FOUND", "outbox event was not found"));
    }
}
