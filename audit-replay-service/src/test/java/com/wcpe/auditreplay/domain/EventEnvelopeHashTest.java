package com.wcpe.auditreplay.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeHashTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void hashesCanonicalJsonDeterministically() {
        Map<String, Object> left = Map.of("b", 2, "a", Map.of("z", true, "m", List.of("one", "two")));
        Map<String, Object> right = Map.of("a", Map.of("m", List.of("one", "two"), "z", true), "b", 2);

        assertEquals(EventEnvelopeHash.payloadHash(objectMapper, left), EventEnvelopeHash.payloadHash(objectMapper, right));
    }

    @Test
    void integrityHashChangesWhenPayloadHashChanges() {
        EventEnvelopeV1 envelope = envelope("abc");
        EventEnvelopeV1 changed = envelope("def");

        String first = EventEnvelopeHash.integrityHash(objectMapper, envelope);
        String second = EventEnvelopeHash.integrityHash(objectMapper, changed);

        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }

    private EventEnvelopeV1 envelope(String payloadHash) {
        return new EventEnvelopeV1(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "audit_record.created",
                1,
                Instant.parse("2026-06-06T00:00:00Z"),
                new EventEnvelopeV1.Producer("audit-replay-service", "0.1.0"),
                new EventEnvelopeV1.Aggregate("audit-record", "audit-1", 1L),
                new EventEnvelopeV1.Actor("SERVICE", "audit-replay-service"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                null,
                "idem-1",
                "audit-record-created-v1",
                Map.of("id", "audit-1"),
                payloadHash,
                null,
                null,
                List.of());
    }
}
