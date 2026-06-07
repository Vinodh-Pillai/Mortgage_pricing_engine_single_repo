package com.wcpe.tenantcontext.cache;

import com.wcpe.tenantcontext.TenantContext;

public record InvalidationScope(
    String scopeType,
    String scopeValue,
    int version,
    boolean dryRun,
    boolean breakGlassApproved
) {
    public InvalidationScope {
        scopeType = required(scopeType, "scopeType");
        scopeValue = required(scopeValue, "scopeValue");
        if (version < 1) {
            throw new CacheException("CACHE_VERSION_INVALID", "invalidation version must be positive");
        }
    }

    public void validateFor(TenantContext tenantContext) {
        if (tenantContext == null) {
            throw new CacheException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        boolean broadTenantFlush = "tenant".equalsIgnoreCase(scopeType) && ("*".equals(scopeValue) || "all".equalsIgnoreCase(scopeValue));
        if (broadTenantFlush && !breakGlassApproved) {
            throw new CacheException("CACHE_SCOPE_TOO_BROAD", "wildcard tenant invalidation requires break-glass approval");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new CacheException("CACHE_INVALIDATION_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }
}
