package com.wcpe.tenantcontext;

public record RequestContext(
    String requestId,
    String traceId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    String requestSource
) {
    public RequestContext(String requestId, String traceId) {
        this(requestId, traceId, traceId, "", "", "");
    }

    public RequestContext {
        requestId = required(requestId, "requestId");
        traceId = required(traceId, "traceId");
        correlationId = required(correlationId, "correlationId");
        causationId = optional(causationId);
        idempotencyKey = optional(idempotencyKey);
        requestSource = optional(requestSource);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", fieldName + " is required");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
