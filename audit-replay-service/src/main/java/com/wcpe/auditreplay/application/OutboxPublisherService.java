package com.wcpe.auditreplay.application;

import com.wcpe.auditreplay.config.OutboxProperties;
import com.wcpe.auditreplay.domain.OutboxEvent;
import com.wcpe.auditreplay.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisherService {

    private final OutboxEventRepository repository;
    private final OutboxBrokerClient brokerClient;
    private final OutboxRetryPolicy retryPolicy;
    private final Clock clock;

    public OutboxPublisherService(
            OutboxEventRepository repository,
            OutboxBrokerClient brokerClient,
            OutboxProperties properties) {
        this(repository, brokerClient, new OutboxRetryPolicy(properties.getPublisher()), Clock.systemUTC());
    }

    OutboxPublisherService(
            OutboxEventRepository repository,
            OutboxBrokerClient brokerClient,
            OutboxRetryPolicy retryPolicy,
            Clock clock) {
        this.repository = repository;
        this.brokerClient = brokerClient;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Transactional
    public int publishAvailable(int limit) {
        int count = 0;
        for (OutboxEvent event : repository.findPublishableWithSkipLocked(limit)) {
            publishOne(event);
            count++;
        }
        return count;
    }

    private void publishOne(OutboxEvent event) {
        event.markInFlight();
        try {
            brokerClient.publish(event);
            event.markPublished(Instant.now(clock));
        } catch (RuntimeException ex) {
            event.markFailed(
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    Instant.now(clock).plus(retryPolicy.nextBackoff(event.getAttemptCount() + 1, event.getEventKey())),
                    retryPolicy.maxAttempts());
        }
    }
}
