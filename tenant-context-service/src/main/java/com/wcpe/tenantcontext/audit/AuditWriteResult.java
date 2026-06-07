package com.wcpe.tenantcontext.audit;

import com.wcpe.tenantcontext.event.EventEnvelope;

public record AuditWriteResult(AuditRecord record, EventEnvelope event) {
    public AuditWriteResult {
        if (record == null) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "record is required");
        }
        if (event == null) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "event is required");
        }
    }
}
