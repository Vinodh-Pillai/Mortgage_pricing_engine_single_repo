package com.wcpe.tenantcontext.outbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxStore {
    Optional<OutboxEvent> findByEventId(UUID eventId);
    Optional<OutboxEvent> findByIdempotencyKey(String tenantId, String idempotencyKey);
    OutboxEvent save(OutboxEvent event);
    List<OutboxEvent> dueForPublish(String tenantId);
    List<OutboxEvent> listByTenant(String tenantId);
}
