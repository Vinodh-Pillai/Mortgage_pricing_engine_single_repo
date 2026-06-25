package com.wcpe.tenantcontext.audit;

import com.wcpe.tenantcontext.TenantContext;
import com.wcpe.tenantcontext.event.CanonicalPayloadHash;
import com.wcpe.tenantcontext.event.DataClassification;
import com.wcpe.tenantcontext.event.EventEnvelope;
import com.wcpe.tenantcontext.event.EventEnvelopeFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    public static final String AUDIT_WRITE_SCOPE = "audit:write";
    public static final String AUDIT_READ_SCOPE = "audit:read";
    private static final String SOURCE_SERVICE = "tenant-context-service";
    private static final String EVENT_NAME = "AuditRecordWritten.v1";
    private static final String SCHEMA_REF = "mpe.security.AuditRecordWritten.v1";

    private final AuditLogStore store;
    private final Clock clock;
    private final AuditRedactionPolicy redactionPolicy;
    private final EventEnvelopeFactory envelopeFactory;

    @Autowired
    public AuditLogService(AuditLogStore store, Clock clock) {
        this(store, clock, new AuditRedactionPolicy(), new EventEnvelopeFactory(clock, null));
    }

    public AuditLogService(AuditLogStore store, Clock clock, AuditRedactionPolicy redactionPolicy, EventEnvelopeFactory envelopeFactory) {
        if (store == null) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "store is required");
        }
        this.store = store;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.redactionPolicy = redactionPolicy == null ? new AuditRedactionPolicy() : redactionPolicy;
        this.envelopeFactory = envelopeFactory == null ? new EventEnvelopeFactory(this.clock, null) : envelopeFactory;
    }

    public AuditWriteResult record(TenantContext tenantContext, AuditCommand command) {
        requireContext(tenantContext, command == null ? "" : command.tenantId(), AUDIT_WRITE_SCOPE);
        if (command == null) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "audit command is required");
        }
        if (!tenantContext.tenantId().equals(command.tenantId())) {
            throw new AuditException("TENANT_ACCESS_DENIED", "audit command tenant does not match tenant context");
        }

        String redactedSummary = redactionPolicy.redact(command.changeSummaryJson());
        String canonicalSummary = canonicalize(redactedSummary);
        AuditRecord idempotentMatch = store.findByIdempotencyKey(command.tenantId(), command.idempotencyKey()).orElse(null);
        AuditRecord existing = store.findByAuditId(command.auditId()).orElse(null);
        String previousHash = idempotentMatch != null
            ? idempotentMatch.previousHash()
            : existing != null
                ? existing.previousHash()
                : store.latestForTenant(command.tenantId()).map(AuditRecord::recordHash).orElse("");
        UUID hashAuditId = idempotentMatch != null ? idempotentMatch.auditId() : command.auditId();
        String recordHash = recordHash(hashAuditId, command, canonicalSummary, previousHash);

        if (idempotentMatch != null) {
            if (idempotentMatch.sameCommandHash(recordHash)) {
                return new AuditWriteResult(idempotentMatch, eventFor(idempotentMatch));
            }
            throw new AuditException("AUDIT_IDEMPOTENCY_CONFLICT", "idempotency key was reused with a different audit payload");
        }

        if (existing != null) {
            if (existing.sameCommandHash(recordHash)) {
                return new AuditWriteResult(existing, eventFor(existing));
            }
            throw new AuditException("AUDIT_RECORD_IMMUTABLE", "auditId was reused with a different payload");
        }

        Instant now = clock.instant();
        AuditRecord record = new AuditRecord(command.auditId(), command.tenantId(), now,
            tenantContext.actor().actorId(), tenantContext.actor().actorType(), command.action(), command.entityType(),
            command.entityId(), command.entityVersion(), command.outcome(), command.correlationId(), command.causationId(),
            command.eventId(), command.idempotencyKey(), command.beforeRef(), command.afterRef(), canonicalSummary,
            command.dataClassification(), recordHash, previousHash);
        return new AuditWriteResult(store.append(record), eventFor(record));
    }

    public AuditRecord get(TenantContext tenantContext, String tenantId, UUID auditId) {
        requireContext(tenantContext, tenantId, AUDIT_READ_SCOPE);
        return store.findByAuditId(auditId)
            .filter(record -> record.tenantId().equals(tenantId))
            .orElseThrow(() -> new AuditException("AUDIT_RECORD_NOT_FOUND", "audit record was not found for this tenant"));
    }

    public List<AuditRecord> listByTenant(TenantContext tenantContext, String tenantId) {
        requireContext(tenantContext, tenantId, AUDIT_READ_SCOPE);
        return store.listByTenant(tenantId);
    }

    private void requireContext(TenantContext tenantContext, String tenantId, String requiredScope) {
        if (tenantContext == null) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "tenant context is required");
        }
        String normalizedTenant = required(tenantId, "tenantId");
        if (!tenantContext.tenantId().equals(normalizedTenant)) {
            throw new AuditException("TENANT_ACCESS_DENIED", "tenant context does not match requested tenant");
        }
        if (tenantContext.actor() == null || tenantContext.actor().actorId().isBlank() || tenantContext.actor().actorType().isBlank()) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "complete actor context is required");
        }
        if (!tenantContext.scopes().contains(requiredScope)) {
            throw new AuditException("TENANT_ACCESS_DENIED", "required audit scope is missing");
        }
    }

    private EventEnvelope eventFor(AuditRecord record) {
        String payloadJson = "{"
            + "\"action\":\"" + json(record.action()) + "\","
            + "\"auditId\":\"" + record.auditId() + "\","
            + "\"dataClassification\":\"" + record.dataClassification().name() + "\","
            + "\"entityId\":\"" + json(record.entityId()) + "\","
            + "\"entityType\":\"" + json(record.entityType()) + "\","
            + "\"outcome\":\"" + json(record.outcome()) + "\","
            + "\"recordHash\":\"" + record.recordHash() + "\","
            + "\"tenantId\":\"" + json(record.tenantId()) + "\""
            + "}";
        return envelopeFactory.create(new TenantContext(record.tenantId(), new com.wcpe.tenantcontext.RequestContext(
                record.correlationId(), record.correlationId(), record.correlationId(), record.causationId(), record.idempotencyKey(), SOURCE_SERVICE),
                new com.wcpe.tenantcontext.ActorRef(record.actorId(), record.actorType()), List.of(), List.of(AUDIT_WRITE_SCOPE), SOURCE_SERVICE),
            new EventEnvelopeFactory.EnvelopeCommand(UUID.nameUUIDFromBytes((record.tenantId() + ":" + record.auditId()).getBytes()),
                EVENT_NAME, 1, record.occurredAt(), record.tenantId(), record.actorId(), record.actorType(), record.correlationId(),
                record.causationId(), record.idempotencyKey(), SOURCE_SERVICE, SCHEMA_REF, DataClassification.INTERNAL, payloadJson));
    }

    private String recordHash(UUID auditId, AuditCommand command, String canonicalSummary, String previousHash) {
        String hashMaterial = "{"
            + "\"action\":\"" + json(command.action()) + "\","
            + "\"auditId\":\"" + auditId + "\","
            + "\"changeSummary\":" + canonicalSummary + ","
            + "\"correlationId\":\"" + json(command.correlationId()) + "\","
            + "\"entityId\":\"" + json(command.entityId()) + "\","
            + "\"entityType\":\"" + json(command.entityType()) + "\","
            + "\"outcome\":\"" + json(command.outcome()) + "\","
            + "\"previousHash\":\"" + json(previousHash) + "\","
            + "\"tenantId\":\"" + json(command.tenantId()) + "\""
            + "}";
        return CanonicalPayloadHash.sha256(hashMaterial);
    }

    private String canonicalize(String payloadJson) {
        try {
            return CanonicalPayloadHash.canonicalize(payloadJson);
        } catch (RuntimeException error) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "changeSummaryJson must be valid JSON");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", fieldName + " is required");
        }
        return value.trim();
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
