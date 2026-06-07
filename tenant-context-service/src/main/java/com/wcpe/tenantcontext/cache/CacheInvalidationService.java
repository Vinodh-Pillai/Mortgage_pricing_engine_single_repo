package com.wcpe.tenantcontext.cache;

import com.wcpe.tenantcontext.TenantContext;
import com.wcpe.tenantcontext.audit.AuditCommand;
import com.wcpe.tenantcontext.audit.AuditLogService;
import com.wcpe.tenantcontext.audit.AuditWriteResult;
import com.wcpe.tenantcontext.event.DataClassification;
import com.wcpe.tenantcontext.outbox.OutboxEvent;
import com.wcpe.tenantcontext.outbox.OutboxEventCommand;
import com.wcpe.tenantcontext.outbox.OutboxWriter;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class CacheInvalidationService {
    public static final String EVENT_NAME = "CacheInvalidationRequested.v1";
    public static final String TOPIC = "tenant-context.cache-invalidation.v1";
    public static final String SCHEMA_REF = "mpe.security.CacheInvalidationRequested.v1";

    private final OutboxWriter outboxWriter;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public CacheInvalidationService(OutboxWriter outboxWriter, AuditLogService auditLogService, Clock clock) {
        if (outboxWriter == null) {
            throw new CacheException("CACHE_INVALIDATION_DEPENDENCY_MISSING", "outboxWriter is required");
        }
        if (auditLogService == null) {
            throw new CacheException("CACHE_INVALIDATION_DEPENDENCY_MISSING", "auditLogService is required");
        }
        this.outboxWriter = outboxWriter;
        this.auditLogService = auditLogService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public CacheInvalidationResult requestInvalidation(TenantContext tenantContext, UUID requestId,
                                                       InvalidationScope scope, String reasonCode) {
        if (tenantContext == null) {
            throw new CacheException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        if (requestId == null) {
            throw new CacheException("CACHE_INVALIDATION_VALIDATION_FAILED", "requestId is required");
        }
        if (scope == null) {
            throw new CacheException("CACHE_INVALIDATION_VALIDATION_FAILED", "scope is required");
        }
        scope.validateFor(tenantContext);
        String reason = required(reasonCode, "reasonCode");
        String correlationId = tenantContext.request().correlationId();
        String causationId = tenantContext.request().causationId().isBlank()
            ? requestId.toString()
            : tenantContext.request().causationId();
        String idempotencyKey = tenantContext.request().idempotencyKey().isBlank()
            ? requestId.toString()
            : tenantContext.request().idempotencyKey();
        Instant now = clock.instant();
        String payload = payload(tenantContext.tenantId(), requestId, scope, reason, correlationId, causationId, now);

        OutboxEvent event = outboxWriter.write(tenantContext, new OutboxEventCommand(tenantContext.tenantId(),
            UUID.nameUUIDFromBytes((tenantContext.tenantId() + ":" + requestId).getBytes()), "CacheInvalidation",
            requestId.toString(), TOPIC, tenantContext.tenantId() + ":" + scope.scopeType() + ":" + scope.scopeValue(),
            SCHEMA_REF, EVENT_NAME, 1, payload, tenantContext.actor().actorId(), correlationId, causationId, idempotencyKey, now));

        AuditWriteResult audit = auditLogService.record(tenantContext, new AuditCommand(tenantContext.tenantId(),
            UUID.nameUUIDFromBytes(("audit:" + tenantContext.tenantId() + ":" + requestId).getBytes()),
            "CACHE_INVALIDATION_REQUESTED", "CacheInvalidation", requestId.toString(), Integer.toString(scope.version()),
            scope.dryRun() ? "DRY_RUN" : "REQUESTED", correlationId, causationId, event.eventId().toString(), idempotencyKey,
            "cache-metadata-only", "outbox:" + event.eventId(), payload, DataClassification.INTERNAL));

        return new CacheInvalidationResult(requestId, event, audit, scope.dryRun());
    }

    private String payload(String tenantId, UUID requestId, InvalidationScope scope, String reasonCode,
                           String correlationId, String causationId, Instant requestedAt) {
        return "{"
            + "\"requestId\":\"" + json(requestId.toString()) + "\","
            + "\"tenantId\":\"" + json(tenantId) + "\","
            + "\"scopeType\":\"" + json(scope.scopeType()) + "\","
            + "\"scopeValue\":\"" + json(scope.scopeValue()) + "\","
            + "\"version\":" + scope.version() + ","
            + "\"reasonCode\":\"" + json(reasonCode) + "\"," 
            + "\"correlationId\":\"" + json(correlationId) + "\"," 
            + "\"causationId\":\"" + json(causationId) + "\"," 
            + "\"sourceEntity\":\"tenant-context-cache\"," 
            + "\"sourceEventId\":\"" + json(requestId.toString()) + "\","
            + "\"requestedAt\":\"" + requestedAt + "\""
            + "}";
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new CacheException("CACHE_INVALIDATION_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record CacheInvalidationResult(UUID requestId, OutboxEvent event, AuditWriteResult audit, boolean dryRun) { }
}
