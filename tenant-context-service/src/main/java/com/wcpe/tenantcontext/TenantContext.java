package com.wcpe.tenantcontext;

public record TenantContext(String tenantId, RequestContext request) {
    public TenantContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenantId is required");
        }
        if (request == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "request context is required");
        }
    }
}
