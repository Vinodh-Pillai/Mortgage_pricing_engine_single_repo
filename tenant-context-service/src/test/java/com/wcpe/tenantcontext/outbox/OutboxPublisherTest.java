package com.wcpe.tenantcontext.outbox;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

class OutboxPublisherTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-07T18:20:00Z"), ZoneOffset.UTC);
    private final InMemoryOutboxStore store = new InMemoryOutboxStore();
    private final OutboxWriter writer = new OutboxWriter(store, CLOCK);
    private final OutboxPublisher publisher = new OutboxPublisher(store, CLOCK, 2, Duration.ofSeconds(30));

    @Test
    void selectsPendingEventsByTenantOnly() {
        OutboxEvent alpha = writer.write(OutboxWriterTest.context("tenant-alpha"), OutboxWriterTest.command("tenant-alpha", UUID.randomUUID(), "a", "{\"a\":1}"));
        writer.write(OutboxWriterTest.context("tenant-beta"), OutboxWriterTest.command("tenant-beta", UUID.randomUUID(), "b", "{\"b\":1}"));

        assertThat(publisher.dueForPublish("tenant-alpha"))
            .extracting(OutboxEvent::eventId)
            .containsExactly(alpha.eventId());
    }

    @Test
    void marksPublishingThenPublishedAndPreventsFurtherFailureMutation() {
        OutboxEvent event = writer.write(OutboxWriterTest.context("tenant-alpha"), OutboxWriterTest.command("tenant-alpha", UUID.randomUUID(), "pub", "{\"event\":1}"));

        OutboxEvent publishing = publisher.markPublishing(event.eventId());
        OutboxEvent published = publisher.markPublished(event.eventId());

        assertThat(publishing.status()).isEqualTo(OutboxStatus.PUBLISHING);
        assertThat(published.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(published.publishedAt()).isEqualTo(Instant.parse("2026-06-07T18:20:00Z"));
        assertThatThrownBy(() -> publisher.recordFailure(event.eventId(), "BROKER_UNAVAILABLE", "broker unavailable"))
            .isInstanceOf(OutboxException.class)
            .extracting(error -> ((OutboxException) error).code())
            .isEqualTo("OUTBOX_EVENT_ALREADY_PUBLISHED");
    }

    @Test
    void failureTransitionsToRetryWaitThenDlqAtConfiguredLimit() {
        OutboxEvent event = writer.write(OutboxWriterTest.context("tenant-alpha"), OutboxWriterTest.command("tenant-alpha", UUID.randomUUID(), "retry", "{\"event\":1}"));

        OutboxEvent retry = publisher.recordFailure(event.eventId(), "BROKER_UNAVAILABLE", "broker unavailable");
        OutboxEvent dlq = publisher.recordFailure(event.eventId(), "BROKER_UNAVAILABLE", "broker unavailable");

        assertThat(retry.status()).isEqualTo(OutboxStatus.RETRY_WAIT);
        assertThat(retry.attemptCount()).isEqualTo(1);
        assertThat(retry.nextAttemptAt()).isEqualTo(Instant.parse("2026-06-07T18:20:30Z"));
        assertThat(dlq.status()).isEqualTo(OutboxStatus.DLQ);
        assertThat(dlq.attemptCount()).isEqualTo(2);
        assertThat(dlq.nextAttemptAt()).isNull();
        assertThat(dlq.attempts()).hasSize(2);
        assertThatThrownBy(() -> publisher.recordFailure(event.eventId(), "BROKER_UNAVAILABLE", "still unavailable"))
            .isInstanceOf(OutboxException.class)
            .extracting(error -> ((OutboxException) error).code())
            .isEqualTo("OUTBOX_RETRY_NOT_ALLOWED");
    }

    @Test
    void quarantineRequiresReasonAndCreatesTerminalStatus() {
        OutboxEvent event = writer.write(OutboxWriterTest.context("tenant-alpha"), OutboxWriterTest.command("tenant-alpha", UUID.randomUUID(), "quarantine", "{\"event\":1}"));

        assertThatThrownBy(() -> publisher.quarantine(event.eventId(), " "))
            .isInstanceOf(OutboxException.class)
            .extracting(error -> ((OutboxException) error).code())
            .isEqualTo("OUTBOX_VALIDATION_FAILED");

        OutboxEvent quarantined = publisher.quarantine(event.eventId(), "poison event isolated by operator reason");

        assertThat(quarantined.status()).isEqualTo(OutboxStatus.QUARANTINED);
        assertThat(quarantined.quarantineReason()).isEqualTo("poison event isolated by operator reason");
    }
}
