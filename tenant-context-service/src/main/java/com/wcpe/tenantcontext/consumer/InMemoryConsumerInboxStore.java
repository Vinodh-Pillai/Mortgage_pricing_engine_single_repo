package com.wcpe.tenantcontext.consumer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryConsumerInboxStore {
    private final Map<String, ConsumerInboxRecord> recordsByTenantConsumerEvent = new HashMap<>();
    private final Map<UUID, ConsumerInboxRecord> recordsByInboxId = new HashMap<>();

    public synchronized Optional<ConsumerInboxRecord> find(String tenantId, String consumerName, UUID eventId) {
        return Optional.ofNullable(recordsByTenantConsumerEvent.get(key(tenantId, consumerName, eventId)));
    }

    public synchronized ConsumerInboxRecord save(ConsumerInboxRecord record) {
        recordsByTenantConsumerEvent.put(key(record.tenantId(), record.consumerName(), record.eventId()), record);
        recordsByInboxId.put(record.inboxId(), record);
        return record;
    }

    public synchronized List<ConsumerInboxRecord> listByTenant(String tenantId) {
        return new ArrayList<>(recordsByInboxId.values()).stream()
            .filter(record -> record.tenantId().equals(tenantId))
            .sorted(Comparator.comparing(ConsumerInboxRecord::firstSeenAt))
            .toList();
    }

    private static String key(String tenantId, String consumerName, UUID eventId) {
        return tenantId + "|" + consumerName + "|" + eventId;
    }
}
