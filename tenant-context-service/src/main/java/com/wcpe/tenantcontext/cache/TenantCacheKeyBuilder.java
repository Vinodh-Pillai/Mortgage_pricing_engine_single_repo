package com.wcpe.tenantcontext.cache;

import com.wcpe.tenantcontext.TenantContext;

import java.util.Locale;

public class TenantCacheKeyBuilder {
    public CacheKey keyFor(TenantContext tenantContext, String domain, String entity, String id, int version) {
        if (tenantContext == null) {
            throw new CacheException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        if (version < 1) {
            throw new CacheException("CACHE_VERSION_INVALID", "cache version must be positive");
        }
        String value = "tenant:" + segment(tenantContext.tenantId(), "tenantId")
            + ":" + segment(domain, "domain")
            + ":" + segment(entity, "entity")
            + ":" + segment(id, "id")
            + ":v" + version;
        return new CacheKey(value, false);
    }

    public CacheKey approvedPlatformStaticKey(String namespace, String name) {
        return new CacheKey("platform-static:" + segment(namespace, "namespace") + ":" + segment(name, "name"), true);
    }

    private static String segment(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new CacheException("CACHE_KEY_VALIDATION_FAILED", fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.contains(":")) {
            throw new CacheException("CACHE_KEY_VALIDATION_FAILED", fieldName + " must not contain ':'");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
