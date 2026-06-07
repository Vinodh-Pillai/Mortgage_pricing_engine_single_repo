package com.wcpe.auditreplay.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void createsPendingEventWithEnvelopeHeaders() {
        byte[] payload = "{\"sourceRefs\":[\"quote:123\"]}".getBytes(StandardCharsets.UTF_8);
        byte[] headers = "{\"eventType\":\"outbox_pattern.completed.v1\"}".getBytes(StandardCharsets.UTF_8);

        OutboxEvent event = OutboxEvent.createPending(
                UUID.randomUUID(),
                "quote",
                "quote-123",
                7L,
                "outbox_pattern.completed.v1",
                1,
                "tenant:quote-123:7",
                "tenant:quote-123",
                payload,
                headers,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "svc-audit",
                "idem-123");

        assertNotNull(event.getId());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttemptCount());
        assertEquals("idem-123", event.getIdempotencyKey());
        assertArrayEquals(payload, event.getPayloadJson());
        assertArrayEquals(headers, event.getHeadersJson());
        assertNotNull(event.getIntegrityHash());
    }

    @Test
    void movesFailedToPoisonAfterMaxAttempts() {
        OutboxEvent event = minimalEvent();

        event.markInFlight();
        event.markFailed("BROKER_DOWN", "broker unavailable", Instant.parse("2026-06-06T19:00:00Z"), 2);
        assertEquals(OutboxEventStatus.FAILED, event.getStatus());
        assertEquals(1, event.getAttemptCount());

        event.markInFlight();
        event.markFailed("BROKER_DOWN", "broker unavailable", Instant.parse("2026-06-06T19:05:00Z"), 2);
        assertEquals(OutboxEventStatus.POISON, event.getStatus());
        assertEquals(2, event.getAttemptCount());
    }

    @Test
    void retryOnlyAllowsFailedEvents() {
        OutboxEvent event = minimalEvent();

        assertThrows(IllegalStateException.class, () -> event.queueRetry(Instant.now()));

        event.markInFlight();
        event.markFailed("BROKER_DOWN", "broker unavailable", Instant.parse("2026-06-06T19:00:00Z"), 3);
        event.queueRetry(Instant.parse("2026-06-06T19:01:00Z"));
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals("tenant:event:1", event.getEventKey());
    }

    private OutboxEvent minimalEvent() {
        return OutboxEvent.createPending(
                UUID.randomUUID(),
                "audit",
                "aggregate-1",
                1L,
                "outbox_pattern.completed.v1",
                1,
                "tenant:event:1",
                "tenant:aggregate-1",
                "{\"id\":\"aggregate-1\"}".getBytes(StandardCharsets.UTF_8),
                "{\"tenantId\":\"tenant\"}".getBytes(StandardCharsets.UTF_8),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "actor-1",
                "idem-1");
    }
}
