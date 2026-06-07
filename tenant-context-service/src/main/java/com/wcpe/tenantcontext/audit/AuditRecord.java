package com.wcpe.tenantcontext.audit;

import com.wcpe.tenantcontext.event.DataClassification;

import java.time.Instant;
import java.util.UUID;

public record AuditRecord(
    UUID auditId,
    String tenantId,
    Instant occurredAt,
    String actorId,
    String actorType,
    String action,
    String entityType,
    String entityId,
    String entityVersion,
    String outcome,
    String correlationId,
    String causationId,
    String eventId,
    String idempotencyKey,
    String beforeRef,
    String afterRef,
    String changeSummaryJson,
    DataClassification dataClassification,
    String recordHash,
    String previousHash
) {
    public AuditRecord {
        if (auditId == null) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "auditId is required");
        }
        tenantId = required(tenantId, "tenantId");
        if (occurredAt == null) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "occurredAt is required");
        }
        actorId = required(actorId, "actorId");
        actorType = required(actorType, "actorType");
        action = required(action, "action");
        entityType = required(entityType, "entityType");
        entityId = required(entityId, "entityId");
        entityVersion = optional(entityVersion);
        outcome = required(outcome, "outcome");
        correlationId = required(correlationId, "correlationId");
        causationId = required(causationId, "causationId");
        eventId = optional(eventId);
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        beforeRef = optional(beforeRef);
        afterRef = optional(afterRef);
        changeSummaryJson = required(changeSummaryJson, "changeSummaryJson");
        if (dataClassification == null) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "dataClassification is required");
        }
        recordHash = required(recordHash, "recordHash");
        if (!recordHash.startsWith("sha256:")) {
            throw new AuditException("AUDIT_HASH_MISMATCH", "recordHash must use sha256 prefix");
        }
        previousHash = optional(previousHash);
    }

    public boolean sameCommandHash(String commandHash) {
        return recordHash.equals(commandHash);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", fieldName + " is required");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
