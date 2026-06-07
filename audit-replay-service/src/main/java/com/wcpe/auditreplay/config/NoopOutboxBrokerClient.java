package com.wcpe.auditreplay.config;

import com.wcpe.auditreplay.application.OutboxBrokerClient;
import com.wcpe.auditreplay.domain.OutboxEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(OutboxBrokerClient.class)
public class NoopOutboxBrokerClient implements OutboxBrokerClient {
    @Override
    public void publish(OutboxEvent event) {
        // Broker integration is supplied by environment-specific configuration; local slice records deterministic publish metadata.
    }
}
