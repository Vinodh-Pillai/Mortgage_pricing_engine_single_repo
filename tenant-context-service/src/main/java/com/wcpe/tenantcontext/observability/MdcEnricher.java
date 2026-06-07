package com.wcpe.tenantcontext.observability;

import com.wcpe.tenantcontext.TenantContext;
import com.wcpe.tenantcontext.TenantContextValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

public class MdcEnricher {
    private final LogRedactionPolicy redactionPolicy;

    public MdcEnricher() {
        this(new LogRedactionPolicy());
    }

    public MdcEnricher(LogRedactionPolicy redactionPolicy) {
        this.redactionPolicy = redactionPolicy == null ? new LogRedactionPolicy() : redactionPolicy;
    }

    public Map<String, String> fields(TenantContext tenantContext, Map<String, String> candidateFields) {
        if (tenantContext == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenant context is required");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("service", "tenant-context-service");
        fields.put("tenant_id", tenantContext.tenantId());
        fields.put("correlation_id", tenantContext.request().correlationId());
        fields.put("causation_id", tenantContext.request().causationId());
        fields.put("trace_id", tenantContext.request().traceId());
        fields.put("request_id", tenantContext.request().requestId());
        fields.put("actor_type", tenantContext.actor().actorType());
        fields.put("channel", tenantContext.channel());
        if (candidateFields != null) {
            candidateFields.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    fields.put(key.trim(), redactionPolicy.redact(key, value));
                }
            });
        }
        return Map.copyOf(fields);
    }
}
