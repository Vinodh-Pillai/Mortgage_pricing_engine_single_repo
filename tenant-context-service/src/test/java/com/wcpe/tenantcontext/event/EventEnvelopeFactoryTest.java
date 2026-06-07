package com.wcpe.tenantcontext.event;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

class EventEnvelopeFactoryTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-07T21:45:00Z"), ZoneOffset.UTC);
    private final EventEnvelopeFactory factory = new EventEnvelopeFactory(CLOCK, new EventClassificationPolicy());

    @Test
    void buildsTenantScopedEnvelopeWithRequiredHeadersAndDeterministicHash() {
        UUID eventId = UUID.fromString("018f7c7e-9f3b-7cc2-a6db-2e3df7d1c001");

        EventEnvelope envelope = factory.create(context("tenant-alpha"), command(eventId,
            "{\"scenarioId\":\"S-1\",\"tenantId\":\"tenant-alpha\",\"status\":\"READY\"}"));

        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.eventName()).isEqualTo("TenantAccessChanged");
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.tenantId()).isEqualTo("tenant-alpha");
        assertThat(envelope.actor()).isEqualTo(new EventActor("actor-1", "USER", "tenant-context-service"));
        assertThat(envelope.correlationId()).isEqualTo("corr-1");
        assertThat(envelope.causationId()).isEqualTo("cmd-1");
        assertThat(envelope.idempotencyKey()).isEqualTo("idem-1");
        assertThat(envelope.schemaRef()).isEqualTo("mpe.security.TenantAccessChanged.v1");
        assertThat(envelope.dataClassification()).isEqualTo(DataClassification.INTERNAL);
        assertThat(envelope.payloadJson()).isEqualTo("{\"scenarioId\":\"S-1\",\"status\":\"READY\",\"tenantId\":\"tenant-alpha\"}");
        assertThat(envelope.payloadHash()).isEqualTo(CanonicalPayloadHash.sha256("{\"tenantId\":\"tenant-alpha\",\"scenarioId\":\"S-1\",\"status\":\"READY\"}"));
        assertThat(envelope.topicName("tenant-context", "access")).isEqualTo("tenant-context.access.v1");
        assertThat(envelope.partitionKey("aggregate-1")).isEqualTo("tenant-alpha:aggregate-1");
    }

    @Test
    void failsClosedWhenPayloadTenantIsMissingOrMismatched() {
        assertThatThrownBy(() -> factory.create(context("tenant-alpha"), command(UUID.randomUUID(), "{\"status\":\"READY\"}")))
            .isInstanceOf(EventEnvelopeValidationException.class)
            .extracting(error -> ((EventEnvelopeValidationException) error).code())
            .isEqualTo("MISSING_TENANT");

        assertThatThrownBy(() -> factory.create(context("tenant-alpha"), command(UUID.randomUUID(), "{\"tenantId\":\"tenant-beta\"}")))
            .isInstanceOf(EventEnvelopeValidationException.class)
            .extracting(error -> ((EventEnvelopeValidationException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, new RequestContext("request-1", "trace-1"));
    }

    private static EventEnvelopeFactory.EnvelopeCommand command(UUID eventId, String payloadJson) {
        return new EventEnvelopeFactory.EnvelopeCommand(eventId, "TenantAccessChanged", 1,
            Instant.parse("2026-06-07T21:45:00Z"), "tenant-alpha", "actor-1", "USER", "corr-1", "cmd-1",
            "idem-1", "tenant-context-service", "mpe.security.TenantAccessChanged.v1", DataClassification.INTERNAL,
            payloadJson);
    }
}
