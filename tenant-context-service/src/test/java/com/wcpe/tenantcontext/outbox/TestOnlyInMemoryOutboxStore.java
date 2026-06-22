package com.wcpe.tenantcontext.outbox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TestOnlyInMemoryOutboxStore implements OutboxStore {
    private final Map<UUID, OutboxEvent> eventsById = new HashMap<>();
    private final Map<String, UUID> idempotencyByTenantAndKey = new HashMap<>();

    @Override
    public synchronized Optional<OutboxEvent> findByEventId(UUID eventId) {
        return Optional.ofNullable(eventsById.get(eventId));
    }

    @Override
    public synchronized Optional<OutboxEvent> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (tenantId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        UUID eventId = idempotencyByTenantAndKey.get(tenantId + "|" + idempotencyKey);
        return eventId == null ? Optional.empty() : Optional.ofNullable(eventsById.get(eventId));
    }

    @Override
    public synchronized OutboxEvent save(OutboxEvent event) {
        eventsById.put(event.eventId(), event);
        if (event.idempotencyKey() != null && !event.idempotencyKey().isBlank()) {
            idempotencyByTenantAndKey.put(event.tenantId() + "|" + event.idempotencyKey(), event.eventId());
        }
        return event;
    }

    @Override
    public synchronized List<OutboxEvent> dueForPublish(String tenantId) {
        return eventsById.values().stream()
            .filter(event -> event.tenantId().equals(tenantId))
            .filter(event -> event.status() == OutboxStatus.PENDING || event.status() == OutboxStatus.RETRY_WAIT)
            .sorted(Comparator.comparing(OutboxEvent::createdAt))
            .toList();
    }

    @Override
    public synchronized List<OutboxEvent> listByTenant(String tenantId) {
        return new ArrayList<>(eventsById.values()).stream()
            .filter(event -> event.tenantId().equals(tenantId))
            .sorted(Comparator.comparing(OutboxEvent::createdAt))
            .toList();
    }
}
