package com.wcpe.tenantcontext.observability;

import com.wcpe.tenantcontext.TenantContextValidationException;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIdGenerator {
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{1,127}$");
    private static final String GENERATED_PREFIX = "corr-";

    private CorrelationIdGenerator() {
    }

    public static String generate() {
        return GENERATED_PREFIX + UUID.randomUUID();
    }

    public static String resolve(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return generate();
        }
        String normalized = candidate.trim();
        if (!SAFE_ID_PATTERN.matcher(normalized).matches()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MALFORMED", "correlationId is malformed");
        }
        return normalized;
    }
}
