package com.wcpe.tenantcontext.cache;

public record CacheKey(String value, boolean platformStaticApproved) {
    public CacheKey {
        value = required(value, "value");
        if (!platformStaticApproved && !value.startsWith("tenant:")) {
            throw new CacheException("CACHE_KEY_TENANT_MISSING", "tenant cache keys must start with tenant:{tenantId}");
        }
        if (platformStaticApproved && !value.startsWith("platform-static:")) {
            throw new CacheException("CACHE_KEY_PLATFORM_STATIC_UNAPPROVED", "platform-static keys must be explicitly approved");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new CacheException("CACHE_KEY_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }
}
