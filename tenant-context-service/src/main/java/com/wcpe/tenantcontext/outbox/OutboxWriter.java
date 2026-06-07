package com.wcpe.tenantcontext.outbox;

import com.wcpe.tenantcontext.TenantContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public class OutboxWriter {
    private final InMemoryOutboxStore store;
    private final Clock clock;

    public OutboxWriter(InMemoryOutboxStore store, Clock clock) {
        if (store == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "store is required");
        }
        this.store = store;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public OutboxEvent write(TenantContext tenantContext, OutboxEventCommand command) {
        if (tenantContext == null) {
            throw new OutboxException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        if (command == null) {
            throw new OutboxException("OUTBOX_VALIDATION_FAILED", "outbox command is required");
        }
        if (!tenantContext.tenantId().equals(command.tenantId())) {
            throw new OutboxException("TENANT_ACCESS_DENIED", "outbox command tenant does not match tenant context");
        }

        String payloadHash = payloadHash(command.envelopeJson());

        OutboxEvent idempotentMatch = store.findByIdempotencyKey(command.tenantId(), command.idempotencyKey()).orElse(null);
        if (idempotentMatch != null) {
            if (idempotentMatch.samePayload(payloadHash)) {
                return idempotentMatch;
            }
            throw new OutboxException("OUTBOX_IDEMPOTENCY_CONFLICT", "idempotency key was reused with a different payload");
        }

        OutboxEvent eventIdMatch = store.findByEventId(command.eventId()).orElse(null);
        if (eventIdMatch != null) {
            if (!eventIdMatch.tenantId().equals(command.tenantId())) {
                throw new OutboxException("TENANT_ACCESS_DENIED", "eventId belongs to a different tenant");
            }
            if (eventIdMatch.samePayload(payloadHash)) {
                return eventIdMatch;
            }
            throw new OutboxException("OUTBOX_EVENT_CONFLICT", "eventId was reused with a different payload");
        }

        Instant now = clock.instant();
        OutboxEvent event = new OutboxEvent(command.tenantId(), command.eventId(), command.aggregateType(),
            command.aggregateId(), command.topic(), command.partitionKey(), command.schemaRef(), command.eventName(),
            command.eventVersion(), command.envelopeJson(), payloadHash, OutboxStatus.PENDING, 0, null, now, now,
            null, command.actorId(), command.correlationId(), command.idempotencyKey(), List.of(), "", "", "");
        return store.save(event);
    }

    static String payloadHash(String envelopeJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(envelopeJson.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new OutboxException("OUTBOX_HASH_UNAVAILABLE", "SHA-256 payload hashing is unavailable");
        }
    }
}
