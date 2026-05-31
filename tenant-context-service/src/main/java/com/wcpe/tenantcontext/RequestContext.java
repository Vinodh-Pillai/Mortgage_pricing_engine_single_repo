package com.wcpe.tenantcontext;

public record RequestContext(String requestId, String traceId) {
    public RequestContext {
        if (requestId == null || requestId.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "requestId is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "traceId is required");
        }
    }
}
