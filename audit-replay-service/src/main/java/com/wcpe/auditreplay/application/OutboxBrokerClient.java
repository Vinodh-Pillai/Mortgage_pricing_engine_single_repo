package com.wcpe.auditreplay.application;

import com.wcpe.auditreplay.domain.OutboxEvent;

public interface OutboxBrokerClient {
    void publish(OutboxEvent event);
}
