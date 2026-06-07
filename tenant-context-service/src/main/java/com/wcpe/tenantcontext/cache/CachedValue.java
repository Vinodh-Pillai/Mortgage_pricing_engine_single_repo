package com.wcpe.tenantcontext.cache;

import com.wcpe.tenantcontext.TenantContext;

import java.time.Instant;

public record CachedValue(
    String tenantId,
    int version,
    Instant sourceUpdatedAt,
    String payloadHash,
    String payloadJson
) {
    public CachedValue {
        tenantId = required(tenantId, "tenantId");
        if (version < 1) {
            throw new CacheException("CACHE_VERSION_INVALID", "cache version must be positive");
        }
        if (sourceUpdatedAt == null) {
            throw new CacheException("CACHE_VALUE_METADATA_MISSING", "sourceUpdatedAt is required");
        }
        payloadHash = required(payloadHash, "payloadHash");
        payloadJson = required(payloadJson, "payloadJson");
    }

    public void validateFor(TenantContext tenantContext, CacheKey key, int expectedVersion) {
        if (tenantContext == null) {
            throw new CacheException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        if (key == null) {
            throw new CacheException("CACHE_KEY_VALIDATION_FAILED", "cache key is required");
        }
        if (!tenantContext.tenantId().equals(tenantId)) {
            throw new CacheException("CACHE_KEY_TENANT_MISMATCH", "cached tenant metadata does not match tenant context");
        }
        if (!key.platformStaticApproved() && !key.value().startsWith("tenant:" + tenantContext.tenantId() + ":")) {
            throw new CacheException("CACHE_KEY_TENANT_MISMATCH", "cache key tenant prefix does not match tenant context");
        }
        if (version != expectedVersion) {
            throw new CacheException("CACHE_STALE_VERSION", "cached value version is stale");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new CacheException("CACHE_VALUE_METADATA_MISSING", fieldName + " is required");
        }
        return value.trim();
    }
}
