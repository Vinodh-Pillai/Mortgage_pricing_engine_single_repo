package com.wcpe.tenantcontext;

import java.util.regex.Pattern;

public class TenantContextService {
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$");
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{1,127}$");

    public TenantContext normalize(TenantContextInput input) {
        if (input == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenant context input is required");
        }

        String tenantId = normalizeRequiredString(input.tenantId(), "tenantId", TENANT_ID_PATTERN);
        String requestId = normalizeRequiredString(input.requestId(), "requestId", TRACE_ID_PATTERN);
        String traceId = normalizeRequiredString(input.traceId(), "traceId", TRACE_ID_PATTERN);

        return new TenantContext(tenantId, new RequestContext(requestId, traceId));
    }

    private String normalizeRequiredString(String value, String fieldName, Pattern pattern) {
        if (value == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", fieldName + " is required");
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", fieldName + " is required");
        }

        if (!pattern.matcher(normalized).matches()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MALFORMED", fieldName + " is malformed");
        }

        return normalized;
    }
}
