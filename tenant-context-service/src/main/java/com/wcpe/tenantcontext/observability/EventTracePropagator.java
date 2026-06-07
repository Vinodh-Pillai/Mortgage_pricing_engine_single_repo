package com.wcpe.tenantcontext.observability;

import com.wcpe.tenantcontext.TenantContext;
import com.wcpe.tenantcontext.TenantContextValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

public class EventTracePropagator {
    public String correlationId(TenantContext tenantContext) {
        requireContext(tenantContext);
        return CorrelationIdGenerator.resolve(tenantContext.request().correlationId());
    }

    public String causationId(TenantContext tenantContext, String boundaryId) {
        requireContext(tenantContext);
        return resolveCausationId(tenantContext.request().causationId(), boundaryId);
    }

    public Map<String, String> headers(TenantContext tenantContext, String boundaryId) {
        requireContext(tenantContext);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Correlation-ID", correlationId(tenantContext));
        headers.put("X-Causation-ID", causationId(tenantContext, boundaryId));
        headers.put("traceparent", traceparent(tenantContext));
        return Map.copyOf(headers);
    }

    public static String resolveCausationId(String candidate, String boundaryId) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate.trim();
        }
        if (boundaryId == null || boundaryId.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "causation boundary id is required");
        }
        return boundaryId.trim();
    }

    private String traceparent(TenantContext tenantContext) {
        String traceId = tenantContext.request().traceId().replaceAll("[^A-Fa-f0-9]", "");
        if (traceId.length() < 32) {
            traceId = (traceId + "00000000000000000000000000000000").substring(0, 32);
        } else if (traceId.length() > 32) {
            traceId = traceId.substring(0, 32);
        }
        return "00-" + traceId.toLowerCase() + "-0000000000000001-01";
    }

    private void requireContext(TenantContext tenantContext) {
        if (tenantContext == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
    }
}
