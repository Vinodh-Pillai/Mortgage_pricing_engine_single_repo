package com.wcpe.auditreplay.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class EventEnvelopeValidator {

    private static final Duration MAX_EVENT_AGE = Duration.ofDays(1);
    private static final List<String> RAW_PII_HEADER_TERMS = List.of("borrower", "ssn", "tin", "email", "phone", "address", "dob");

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final EventContractRegistry registry;

    public EventEnvelopeValidator(ObjectMapper objectMapper, Clock clock, EventContractRegistry registry) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.registry = Objects.requireNonNull(registry, "registry is required");
    }

    public List<String> validate(EventEnvelopeV1 envelope, Map<String, String> headers) {
        List<String> errors = new ArrayList<>();
        if (envelope == null) {
            return List.of("envelope is required");
        }
        require(envelope.eventId() != null, "eventId is required", errors);
        require(envelope.tenantId() != null, "tenantId is required", errors);
        requireText(envelope.eventType(), "eventType is required", errors);
        require(envelope.eventVersion() != null && envelope.eventVersion() > 0, "eventVersion must be positive", errors);
        require(envelope.occurredAt() != null, "occurredAt is required", errors);
        require(envelope.producer() != null && hasText(envelope.producer().service()) && hasText(envelope.producer().version()), "producer service and version are required", errors);
        require(envelope.aggregate() != null && hasText(envelope.aggregate().type()) && hasText(envelope.aggregate().id()), "aggregate type and id are required", errors);
        require(envelope.actor() != null && hasText(envelope.actor().type()) && hasText(envelope.actor().id()), "actor type and id are required", errors);
        require(envelope.correlationId() != null, "correlationId is required", errors);
        requireText(envelope.idempotencyKey(), "idempotencyKey is required", errors);
        requireText(envelope.schemaRef(), "schemaRef is required", errors);
        require(envelope.payload() != null && !envelope.payload().isEmpty(), "payload is required", errors);
        requireText(envelope.payloadHash(), "payloadHash is required", errors);
        requireText(envelope.integrityHash(), "integrityHash is required", errors);
        if (envelope.tenantId() != null && headers != null && headers.containsKey("x-tenant-id")) {
            require(envelope.tenantId().toString().equals(headers.get("x-tenant-id")), "x-tenant-id must match envelope tenantId", errors);
        }
        rejectRawPiiHeaders(headers, errors);
        if (envelope.occurredAt() != null && envelope.occurredAt().isBefore(Instant.now(clock).minus(MAX_EVENT_AGE))) {
            errors.add("occurredAt is stale for publish/replay validation");
        }
        if (hasText(envelope.eventType()) && envelope.eventVersion() != null && !registry.supports(envelope.eventType(), envelope.eventVersion())) {
            errors.add("event schema version is unknown");
        }
        if (envelope.payload() != null && hasText(envelope.payloadHash())) {
            String actualPayloadHash = EventEnvelopeHash.payloadHash(objectMapper, envelope.payload());
            require(actualPayloadHash.equals(envelope.payloadHash()), "payloadHash does not match canonical payload JSON", errors);
        }
        if (hasText(envelope.integrityHash())) {
            EventEnvelopeV1 unsigned = new EventEnvelopeV1(
                    envelope.eventId(), envelope.tenantId(), envelope.eventType(), envelope.eventVersion(), envelope.occurredAt(),
                    envelope.producer(), envelope.aggregate(), envelope.actor(), envelope.correlationId(), envelope.causationId(),
                    envelope.idempotencyKey(), envelope.schemaRef(), envelope.payload(), envelope.payloadHash(), envelope.previousHash(), null,
                    envelope.legalHoldTags());
            String actualIntegrityHash = EventEnvelopeHash.integrityHash(objectMapper, unsigned);
            require(actualIntegrityHash.equals(envelope.integrityHash()), "integrityHash does not match canonical envelope JSON", errors);
        }
        return List.copyOf(errors);
    }

    private static void rejectRawPiiHeaders(Map<String, String> headers, List<String> errors) {
        if (headers == null) {
            return;
        }
        headers.keySet().stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .filter(key -> RAW_PII_HEADER_TERMS.stream().anyMatch(key::contains))
                .findFirst()
                .ifPresent(key -> errors.add("raw PII is not allowed in envelope headers: " + key));
    }

    private static void requireText(String value, String message, List<String> errors) {
        require(hasText(value), message, errors);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message, List<String> errors) {
        if (!condition) {
            errors.add(message);
        }
    }
}
