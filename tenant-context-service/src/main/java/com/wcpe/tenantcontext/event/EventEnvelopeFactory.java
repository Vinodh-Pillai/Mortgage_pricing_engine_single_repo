package com.wcpe.tenantcontext.event;

import com.wcpe.tenantcontext.TenantContext;
import com.wcpe.tenantcontext.observability.EventTracePropagator;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class EventEnvelopeFactory {
    private final Clock clock;
    private final EventClassificationPolicy classificationPolicy;

    public EventEnvelopeFactory(Clock clock, EventClassificationPolicy classificationPolicy) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.classificationPolicy = classificationPolicy == null ? new EventClassificationPolicy() : classificationPolicy;
    }

    public EventEnvelope create(TenantContext tenantContext, EnvelopeCommand command) {
        if (tenantContext == null) {
            throw new EventEnvelopeValidationException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        if (command == null) {
            throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", "envelope command is required");
        }
        if (!tenantContext.tenantId().equals(command.tenantId())) {
            throw new EventEnvelopeValidationException("TENANT_ACCESS_DENIED", "command tenant does not match tenant context");
        }
        String payloadTenantId = CanonicalPayloadHash.topLevelTenantId(command.payloadJson());
        if (payloadTenantId.isBlank()) {
            throw new EventEnvelopeValidationException("MISSING_TENANT", "payload tenantId is required");
        }
        if (!tenantContext.tenantId().equals(payloadTenantId)) {
            throw new EventEnvelopeValidationException("TENANT_ACCESS_DENIED", "payload tenantId does not match tenant context");
        }
        classificationPolicy.validatePayload(command.dataClassification(), command.payloadJson());
        String canonicalPayload = CanonicalPayloadHash.canonicalize(command.payloadJson());
        String payloadHash = CanonicalPayloadHash.sha256(command.payloadJson());
        return new EventEnvelope(command.eventId(), command.eventName(), command.eventVersion(), command.occurredAt(clock),
            command.tenantId(), new EventActor(command.actorId(), command.actorType(), command.sourceService()),
            command.correlationId(), command.causationId(), command.idempotencyKey(), command.sourceService(),
            command.schemaRef(), command.dataClassification(), payloadHash, canonicalPayload);
    }

    public record EnvelopeCommand(
        UUID eventId,
        String eventName,
        int eventVersion,
        Instant occurredAt,
        String tenantId,
        String actorId,
        String actorType,
        String correlationId,
        String causationId,
        String idempotencyKey,
        String sourceService,
        String schemaRef,
        DataClassification dataClassification,
        String payloadJson
    ) {
        public EnvelopeCommand {
            if (eventId == null) {
                eventId = UUID.randomUUID();
            }
            tenantId = required(tenantId, "tenantId");
            eventName = required(eventName, "eventName");
            if (eventVersion < 1) {
                throw new EventEnvelopeValidationException("SCHEMA_VERSION_UNSUPPORTED", "eventVersion must be positive");
            }
            actorId = required(actorId, "actorId");
            actorType = required(actorType, "actorType");
            correlationId = required(correlationId, "correlationId");
            causationId = EventTracePropagator.resolveCausationId(causationId, eventId.toString());
            idempotencyKey = required(idempotencyKey, "idempotencyKey");
            sourceService = required(sourceService, "sourceService");
            schemaRef = required(schemaRef, "schemaRef");
            dataClassification = DataClassification.require(dataClassification);
            payloadJson = required(payloadJson, "payloadJson");
        }

        private Instant occurredAt(Clock clock) {
            return occurredAt == null ? clock.instant() : occurredAt;
        }

        private static String required(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", fieldName + " is required");
            }
            return value.trim();
        }
    }
}
