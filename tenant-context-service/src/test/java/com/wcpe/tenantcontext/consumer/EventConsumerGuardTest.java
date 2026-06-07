package com.wcpe.tenantcontext.consumer;

import static org.assertj.core.api.Assertions.*;

import com.wcpe.tenantcontext.RequestContext;
import com.wcpe.tenantcontext.TenantContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class EventConsumerGuardTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-07T18:30:00Z"), ZoneOffset.UTC);
    private final InMemoryConsumerInboxStore store = new InMemoryConsumerInboxStore();
    private final EventConsumerGuard guard = new EventConsumerGuard(store, CLOCK);

    @Test
    void processesNewEventOnceAndIgnoresDuplicateDeliveryWithMatchingPayloadHash() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger sideEffects = new AtomicInteger();

        ConsumerGuardDecision first = guard.process(context("tenant-alpha"), "pricing-cache-consumer",
            envelope("tenant-alpha", eventId, "{\"scenarioId\":\"S-1\"}"), () -> {
                sideEffects.incrementAndGet();
                return "{\"result\":\"cache-updated\"}";
            });
        ConsumerGuardDecision duplicate = guard.process(context("tenant-alpha"), "pricing-cache-consumer",
            envelope("tenant-alpha", eventId, "{\"scenarioId\":\"S-1\"}"), () -> {
                sideEffects.incrementAndGet();
                return "{\"result\":\"should-not-run\"}";
            });

        assertThat(first.outcome()).isEqualTo(ConsumerGuardDecision.Outcome.PROCESSING_COMPLETED);
        assertThat(first.record().status()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(first.sideEffectExecuted()).isTrue();
        assertThat(duplicate.outcome()).isEqualTo(ConsumerGuardDecision.Outcome.DUPLICATE_IGNORED);
        assertThat(duplicate.sideEffectExecuted()).isFalse();
        assertThat(sideEffects).hasValue(1);
        assertThat(store.listByTenant("tenant-alpha")).hasSize(1);
    }

    @Test
    void failsClosedWhenDuplicateEventIdHasDifferentPayloadHash() {
        UUID eventId = UUID.randomUUID();
        guard.process(context("tenant-alpha"), "workflow-consumer", envelope("tenant-alpha", eventId, "{\"event\":1}"), () -> "{\"ok\":true}");

        assertThatThrownBy(() -> guard.process(context("tenant-alpha"), "workflow-consumer",
            envelope("tenant-alpha", eventId, "{\"event\":2}"), () -> "{}"))
            .isInstanceOf(ConsumerInboxException.class)
            .extracting(error -> ((ConsumerInboxException) error).code())
            .isEqualTo("EVENT_ID_HASH_CONFLICT");
    }

    @Test
    void rejectsTenantMismatchWithoutCreatingVisibleInboxRecord() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> guard.start(context("tenant-alpha"), "tenant-isolation-consumer",
            envelope("tenant-beta", eventId, "{\"event\":1}")))
            .isInstanceOf(ConsumerInboxException.class)
            .extracting(error -> ((ConsumerInboxException) error).code())
            .isEqualTo("TENANT_ACCESS_DENIED");
        assertThat(store.listByTenant("tenant-alpha")).isEmpty();
        assertThat(store.listByTenant("tenant-beta")).isEmpty();
    }

    @Test
    void requiresSchemaVersionAndPayloadInsteadOfInventingDefaults() {
        assertThatThrownBy(() -> new EventEnvelope("tenant-alpha", UUID.randomUUID(), "rate.changed.v1", "rate.changed", 0,
            "{}", "correlation-1", "causation-1", Instant.parse("2026-06-07T18:30:00Z")))
            .isInstanceOf(ConsumerInboxException.class)
            .extracting(error -> ((ConsumerInboxException) error).code())
            .isEqualTo("CONSUMER_INBOX_VALIDATION_FAILED");

        assertThatThrownBy(() -> envelope("tenant-alpha", UUID.randomUUID(), ""))
            .isInstanceOf(ConsumerInboxException.class)
            .extracting(error -> ((ConsumerInboxException) error).code())
            .isEqualTo("CONSUMER_INBOX_VALIDATION_FAILED");
    }

    private static TenantContext context(String tenantId) {
        return new TenantContext(tenantId, new RequestContext("request-1", "trace-1"));
    }

    private static EventEnvelope envelope(String tenantId, UUID eventId, String payloadJson) {
        return new EventEnvelope(tenantId, eventId, "rate.changed.v1", "rate.changed", 1, payloadJson,
            "correlation-1", "causation-1", Instant.parse("2026-06-07T18:30:00Z"));
    }
}
