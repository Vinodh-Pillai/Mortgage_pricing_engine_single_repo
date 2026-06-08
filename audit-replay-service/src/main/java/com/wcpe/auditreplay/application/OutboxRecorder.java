package com.wcpe.auditreplay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.domain.EventEnvelopeHash;
import com.wcpe.auditreplay.domain.EventEnvelopeV1;
import com.wcpe.auditreplay.domain.EventContractRegistry;
import com.wcpe.auditreplay.domain.EventEnvelopeValidator;
import com.wcpe.auditreplay.domain.OutboxEvent;
import com.wcpe.auditreplay.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxRecorder {

    static final String PRODUCER = "audit-replay-service";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final EventEnvelopeValidator envelopeValidator;

    @Autowired
    public OutboxRecorder(OutboxEventRepository repository, ObjectMapper objectMapper, EventContractRegistry eventContractRegistry) {
        this(repository, objectMapper, Clock.systemUTC(), new EventEnvelopeValidator(objectMapper, Clock.systemUTC(), eventContractRegistry));
    }

    OutboxRecorder(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC(), new EventEnvelopeValidator(objectMapper, Clock.systemUTC(), (eventType, eventVersion) -> true));
    }

    OutboxRecorder(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this(repository, objectMapper, clock, new EventEnvelopeValidator(objectMapper, clock, (eventType, eventVersion) -> true));
    }

    OutboxRecorder(OutboxEventRepository repository, ObjectMapper objectMapper, EventEnvelopeValidator envelopeValidator) {
        this(repository, objectMapper, Clock.systemUTC(), envelopeValidator);
    }

    OutboxRecorder(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock, EventEnvelopeValidator envelopeValidator) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.envelopeValidator = envelopeValidator;
    }

    @Transactional
    public OutboxEvent record(OutboxRecordCommand command) {
        validate(command);
        return repository.findByTenantIdAndEventKeyAndEventVersion(
                        command.tenantId(), command.eventKey(), command.eventVersion())
                .orElseGet(() -> repository.save(toEvent(command)));
    }

    private OutboxEvent toEvent(OutboxRecordCommand command) {
        EventEnvelopeV1 envelope = envelope(command);
        Map<String, String> headers = envelopeHeaders(envelope);
        List<String> validationErrors = envelopeValidator.validate(envelope, headers);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException("Invalid event envelope: " + String.join("; ", validationErrors));
        }
        byte[] envelopeJson = writeJson(envelope);
        return OutboxEvent.createPending(
                command.tenantId(),
                command.aggregateType(),
                command.aggregateId(),
                command.aggregateVersion(),
                command.eventType(),
                command.eventVersion(),
                command.eventKey(),
                command.partitionKey(),
                envelopeJson,
                writeJson(headers),
                command.correlationId(),
                command.causationId(),
                command.actorId(),
                command.idempotencyKey(),
                envelope.integrityHash());
    }

    private EventEnvelopeV1 envelope(OutboxRecordCommand command) {
        Instant occurredAt = Instant.now(clock);
        String payloadHash = EventEnvelopeHash.payloadHash(objectMapper, command.payload());
        EventEnvelopeV1 unsigned = new EventEnvelopeV1(
                UUID.nameUUIDFromBytes(command.eventKey().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                command.tenantId(),
                command.eventType(),
                command.eventVersion(),
                occurredAt,
                new EventEnvelopeV1.Producer(PRODUCER, "0.1.0"),
                new EventEnvelopeV1.Aggregate(command.aggregateType(), command.aggregateId(), command.aggregateVersion()),
                new EventEnvelopeV1.Actor("USER_OR_SERVICE", command.actorId()),
                command.correlationId(),
                command.causationId(),
                command.idempotencyKey(),
                command.schemaRef(),
                command.payload(),
                payloadHash,
                null,
                null,
                List.of());
        return new EventEnvelopeV1(
                unsigned.eventId(), unsigned.tenantId(), unsigned.eventType(), unsigned.eventVersion(), unsigned.occurredAt(),
                unsigned.producer(), unsigned.aggregate(), unsigned.actor(), unsigned.correlationId(), unsigned.causationId(),
                unsigned.idempotencyKey(), unsigned.schemaRef(), unsigned.payload(), unsigned.payloadHash(), unsigned.previousHash(),
                EventEnvelopeHash.integrityHash(objectMapper, unsigned), unsigned.legalHoldTags());
    }

    private Map<String, String> envelopeHeaders(EventEnvelopeV1 envelope) {
        return Map.of(
                "x-tenant-id", envelope.tenantId().toString(),
                "x-event-type", envelope.eventType(),
                "x-event-version", envelope.eventVersion().toString(),
                "x-correlation-id", envelope.correlationId().toString(),
                "x-causation-id", Objects.toString(envelope.causationId(), ""),
                "x-idempotency-key", envelope.idempotencyKey(),
                "x-schema-ref", envelope.schemaRef(),
                "x-producer", envelope.producer().service() + ":" + envelope.producer().version(),
                "x-integrity-hash", envelope.integrityHash());
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Outbox payload is not JSON serializable", ex);
        }
    }

    private void validate(OutboxRecordCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.tenantId(), "tenantId is required");
        requireText(command.eventType(), "eventType is required");
        Objects.requireNonNull(command.eventVersion(), "eventVersion is required");
        requireText(command.eventKey(), "eventKey is required");
        requireText(command.actorId(), "actorId is required");
        Objects.requireNonNull(command.correlationId(), "correlationId is required");
        requireText(command.idempotencyKey(), "idempotencyKey is required");
        requireText(command.schemaRef(), "schemaRef is required");
        if (command.payload() == null || command.payload().isEmpty()) {
            throw new IllegalArgumentException("payload is required");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
