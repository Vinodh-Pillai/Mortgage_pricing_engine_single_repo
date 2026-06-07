package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.wcpe.auditreplay.config.OutboxProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

    @Test
    void computesDeterministicBoundedBackoff() {
        OutboxProperties properties = new OutboxProperties();
        properties.getPublisher().setInitialBackoffMs(100);
        properties.getPublisher().setMaxBackoffMs(1000);
        properties.getPublisher().setBackoffMultiplier(2.0);
        properties.getPublisher().setJitterFactor(0.1);
        OutboxRetryPolicy policy = new OutboxRetryPolicy(properties.getPublisher());

        Duration first = policy.nextBackoff(1, "tenant:event:1");
        Duration second = policy.nextBackoff(2, "tenant:event:1");
        Duration secondAgain = policy.nextBackoff(2, "tenant:event:1");

        assertFalse(second.compareTo(first) < 0);
        assertEquals(second, secondAgain);
        assertFalse(policy.nextBackoff(20, "tenant:event:1").compareTo(Duration.ofMillis(1000)) > 0);
    }
}
