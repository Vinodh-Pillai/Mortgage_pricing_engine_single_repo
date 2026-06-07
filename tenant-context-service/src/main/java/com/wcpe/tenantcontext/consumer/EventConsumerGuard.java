package com.wcpe.tenantcontext.consumer;

import com.wcpe.tenantcontext.TenantContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;

public class EventConsumerGuard {
    private final InMemoryConsumerInboxStore store;
    private final Clock clock;

    public EventConsumerGuard(InMemoryConsumerInboxStore store, Clock clock) {
        if (store == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "store is required");
        }
        this.store = store;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public synchronized ConsumerGuardDecision start(TenantContext tenantContext, String consumerName, EventEnvelope envelope) {
        validateTenant(tenantContext, envelope);
        String normalizedConsumerName = required(consumerName, "consumerName");
        String payloadHash = payloadHash(envelope.payloadJson());
        ConsumerInboxRecord existing = store.find(envelope.tenantId(), normalizedConsumerName, envelope.eventId()).orElse(null);
        if (existing != null) {
            if (!existing.samePayload(payloadHash)) {
                throw new ConsumerInboxException("EVENT_ID_HASH_CONFLICT", "eventId was reused with a different payload hash");
            }
            return new ConsumerGuardDecision(ConsumerGuardDecision.Outcome.DUPLICATE_IGNORED, existing, false);
        }

        Instant now = clock.instant();
        ConsumerInboxRecord record = ConsumerInboxRecord.processing(envelope.tenantId(), normalizedConsumerName, envelope, payloadHash, now);
        return new ConsumerGuardDecision(ConsumerGuardDecision.Outcome.PROCESSING_STARTED, store.save(record), false);
    }

    public synchronized ConsumerInboxRecord complete(ConsumerInboxRecord record, String resultPayload) {
        if (record == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "record is required");
        }
        if (record.status() != ProcessingStatus.PROCESSING) {
            throw new ConsumerInboxException("CONSUMER_INBOX_STATUS_CONFLICT", "only processing records can be completed");
        }
        return store.save(record.processed(payloadHash(required(resultPayload, "resultPayload")), clock.instant()));
    }

    public synchronized ConsumerGuardDecision process(
        TenantContext tenantContext,
        String consumerName,
        EventEnvelope envelope,
        Supplier<String> sideEffectHandler
    ) {
        if (sideEffectHandler == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "sideEffectHandler is required");
        }
        ConsumerGuardDecision started = start(tenantContext, consumerName, envelope);
        if (started.outcome() == ConsumerGuardDecision.Outcome.DUPLICATE_IGNORED) {
            return started;
        }
        ConsumerInboxRecord processed = complete(started.record(), sideEffectHandler.get());
        return new ConsumerGuardDecision(ConsumerGuardDecision.Outcome.PROCESSING_COMPLETED, processed, true);
    }

    static String payloadHash(String payloadJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(required(payloadJson, "payloadJson").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new ConsumerInboxException("CONSUMER_INBOX_HASH_UNAVAILABLE", "SHA-256 payload hashing is unavailable");
        }
    }

    private static void validateTenant(TenantContext tenantContext, EventEnvelope envelope) {
        if (tenantContext == null) {
            throw new ConsumerInboxException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        if (envelope == null) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", "event envelope is required");
        }
        if (!tenantContext.tenantId().equals(envelope.tenantId())) {
            throw new ConsumerInboxException("TENANT_ACCESS_DENIED", "event envelope tenant does not match tenant context");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ConsumerInboxException("CONSUMER_INBOX_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }
}
