package com.wcpe.tenantcontext.consumer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsumerInboxStore {
    Optional<ConsumerInboxRecord> find(String tenantId, String consumerName, UUID eventId);
    ConsumerInboxRecord save(ConsumerInboxRecord record);
    List<ConsumerInboxRecord> listByTenant(String tenantId);
}
