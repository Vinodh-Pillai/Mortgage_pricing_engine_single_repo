package com.wcpe.tenantcontext.audit;

import com.wcpe.tenantcontext.event.DataClassification;

import java.util.UUID;

public record AuditCommand(
    String tenantId,
    UUID auditId,
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
    DataClassification dataClassification
) {
    public AuditCommand {
        tenantId = required(tenantId, "tenantId");
        auditId = auditId == null ? UUID.randomUUID() : auditId;
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
