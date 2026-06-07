package com.wcpe.auditreplay.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC);
    private final EventEnvelopeValidator validator = new EventEnvelopeValidator(objectMapper, clock,
            (eventType, eventVersion) -> "audit_record.created".equals(eventType) && Integer.valueOf(1).equals(eventVersion));

    @Test
    void rejectsMissingTenantCorrelationAndSchema() {
        EventEnvelopeV1 envelope = validEnvelope(null, null, "unknown-schema", "bad", "bad");

        List<String> errors = validator.validate(envelope, Map.of("x-borrower-name", "blocked"));

        assertTrue(errors.contains("tenantId is required"));
        assertTrue(errors.contains("correlationId is required"));
        assertTrue(errors.contains("payloadHash does not match canonical payload JSON"));
        assertTrue(errors.contains("integrityHash does not match canonical envelope JSON"));
        assertTrue(errors.stream().anyMatch(error -> error.contains("raw PII is not allowed")));
    }

    @Test
    void acceptsValidCurrentEnvelope() {
        Map<String, Object> payload = Map.of("id", "audit-1", "status", "CREATED", "version", 1, "sourceRefs", List.of("audit:1"));
        String payloadHash = EventEnvelopeHash.payloadHash(objectMapper, payload);
        EventEnvelopeV1 unsigned = validEnvelope(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"), "audit-record-created-v1", payloadHash, null);
        String integrityHash = EventEnvelopeHash.integrityHash(objectMapper, unsigned);
        EventEnvelopeV1 signed = validEnvelope(unsigned.tenantId(), unsigned.correlationId(), unsigned.schemaRef(), payloadHash, integrityHash);

        List<String> errors = validator.validate(signed, Map.of("x-tenant-id", signed.tenantId().toString()));

        assertTrue(errors.isEmpty(), () -> "Unexpected errors: " + errors);
    }

    private EventEnvelopeV1 validEnvelope(UUID tenantId, UUID correlationId, String schemaRef, String payloadHash, String integrityHash) {
        return new EventEnvelopeV1(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                tenantId,
                "audit_record.created",
                1,
                Instant.parse("2026-06-06T11:59:00Z"),
                new EventEnvelopeV1.Producer("audit-replay-service", "0.1.0"),
                new EventEnvelopeV1.Aggregate("audit-record", "audit-1", 1L),
                new EventEnvelopeV1.Actor("SERVICE", "audit-replay-service"),
                correlationId,
                null,
                "idem-1",
                schemaRef,
                Map.of("id", "audit-1", "status", "CREATED", "version", 1, "sourceRefs", List.of("audit:1")),
                payloadHash,
                null,
                integrityHash,
                List.of());
    }
}
