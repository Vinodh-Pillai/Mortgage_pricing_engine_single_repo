package com.wcpe.tenantcontext.outbox;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

class OutboxWriterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-07T18:15:00Z"), ZoneOffset.UTC);
    private final InMemoryOutboxStore store = new InMemoryOutboxStore();
    private final OutboxWriter writer = new OutboxWriter(store, CLOCK);

    @Test
    void writesTenantScopedPendingOutboxEventWithImmutablePayloadHash() {
        OutboxEvent event = writer.write(context("tenant-alpha"), command("tenant-alpha", UUID.randomUUID(), "key-1", "{\"loanId\":\"L-1\"}"));

        assertThat(event.tenantId()).isEqualTo("tenant-alpha");
        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.payloadHash()).isEqualTo(OutboxWriter.payloadHash("{\"loanId\":\"L-1\"}"));
        assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-06-07T18:15:00Z"));
        assertThat(event.topic()).isEqualTo("tenant-context.outbox.v1");
        assertThat(event.eventName()).isEqualTo("tenant_context.outbox_recorded.v1");
        assertThat(event.causationId()).isEqualTo("cause-1");
    }

    @Test
    void replaysDuplicateIdempotencyKeyWhenPayloadHashMatches() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent first = writer.write(context("tenant-alpha"), command("tenant-alpha", eventId, "idem-1", "{\"event\":1}"));

        OutboxEvent replay = writer.write(context("tenant-alpha"), command("tenant-alpha", UUID.randomUUID(), "idem-1", "{\"event\":1}"));

        assertThat(replay).isEqualTo(first);
        assertThat(store.listByTenant("tenant-alpha")).hasSize(1);
    }

    @Test
    void failsClosedWhenDuplicateIdempotencyKeyHasDifferentPayload() {
        writer.write(context("tenant-alpha"), command("tenant-alpha", UUID.randomUUID(), "idem-2", "{\"event\":1}"));

        assertThatThrownBy(() -> writer.write(context("tenant-alpha"), command("tenant-alpha", UUID.randomUUID(), "idem-2", "{\"event\":2}")))
            .isInstanceOf(OutboxException.class)
            .extracting(error -> ((OutboxException) error).code())
            .isEqualTo("OUTBOX_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void rejectsCrossTenantWritesAndDoesNotLeakEventsBetweenTenants() {
        writer.write(context("tenant-alpha"), command("tenant-alpha", UUID.randomUUID(), "alpha-key", "{\"event\":1}"));
        writer.write(context("tenant-beta"), command("tenant-beta", UUID.randomUUID(), "beta-key", "{\"event\":2}"));

        assertThatThrownBy(() -> writer.write(context("tenant-alpha"), command("tenant-beta", UUID.randomUUID(), "bad-key", "{}")))
            .isInstanceOf(OutboxException.class)
            .extracting(error -> ((OutboxException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");
        assertThat(store.listByTenant("tenant-alpha")).hasSize(1);
        assertThat(store.listByTenant("tenant-beta")).hasSize(1);
    }

    @Test
    void doesNotInventTenantOrPayloadDefaults() {
        assertThatThrownBy(() -> writer.write(context("tenant-alpha"), command("tenant-alpha", UUID.randomUUID(), "missing-payload", "")))
            .isInstanceOf(OutboxException.class)
            .extracting(error -> ((OutboxException) error).code())
            .isEqualTo("OUTBOX_VALIDATION_FAILED");
    }

    static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, new RequestContext("request-1", "trace-1"));
    }

    static OutboxEventCommand command(String tenantId, UUID eventId, String idempotencyKey, String envelopeJson) {
        return new OutboxEventCommand(tenantId, eventId, "Scenario", "scenario-1", "tenant-context.outbox.v1",
            tenantId + ":" + eventId, "tenant-context.outbox.v1", "tenant_context.outbox_recorded.v1", 1,
            envelopeJson, "actor-1", "correlation-1", "cause-1", idempotencyKey, Instant.parse("2026-06-07T18:15:00Z"));
    }
}
