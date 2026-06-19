package com.wcpe.tenantcontext;

import com.wcpe.tenantcontext.observability.CorrelationIdGenerator;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TenantContextService {
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$");
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{1,127}$");
    private final TenantAccessPolicy accessPolicy;

    public TenantContextService() {
        this(new TenantAccessPolicy());
    }

    public TenantContextService(TenantAccessPolicy accessPolicy) {
        if (accessPolicy == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenant access policy is required");
        }
        this.accessPolicy = accessPolicy;
    }

    public TenantContext normalize(TenantContextInput input) {
        if (input == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenant context input is required");
        }
        String pathTenantId = input.selectedTenantId() == null || input.selectedTenantId().isBlank() ? input.tenantId() : input.selectedTenantId();
        return resolve(pathTenantId, input);
    }

    public TenantContext resolve(String pathTenantId, TenantContextInput input) {
        if (input == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenant context input is required");
        }

        String tenantId = normalizeRequiredString(pathTenantId, "tenantId", TENANT_ID_PATTERN);
        accessPolicy.validate(tenantId, input, TenantAccessPolicy.DEFAULT_CONTEXT_READ_SCOPE);

        String requestId = normalizeRequiredString(input.requestId(), "requestId", TRACE_ID_PATTERN);
        String traceId = normalizeRequiredString(input.traceId(), "traceId", TRACE_ID_PATTERN);
        String correlationId = normalizeOptionalString(input.correlationId(), "correlationId", TRACE_ID_PATTERN);
        if (correlationId.isBlank()) {
            correlationId = CorrelationIdGenerator.generate();
        }
        String actorId = normalizeRequiredString(input.actorId(), "actorId", TRACE_ID_PATTERN);
        String actorType = normalizeRequiredString(input.actorType(), "actorType", TRACE_ID_PATTERN);
        String channel = normalizeRequiredString(input.channel(), "channel", TRACE_ID_PATTERN);
        String requestSource = normalizeRequiredString(input.requestSource(), "requestSource", TRACE_ID_PATTERN);
        if (TenantAccessPolicy.normalizeList(input.roles()).isEmpty()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "roles are required");
        }

        return new TenantContext(
            tenantId,
            new RequestContext(requestId, traceId, correlationId, input.causationId(), input.idempotencyKey(), requestSource),
            new ActorRef(actorId, actorType),
            TenantAccessPolicy.normalizeList(input.roles()),
            TenantAccessPolicy.normalizeList(input.scopes()),
            channel
        );
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

    private String normalizeOptionalString(String value, String fieldName, Pattern pattern) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim();
        if (!pattern.matcher(normalized).matches()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MALFORMED", fieldName + " is malformed");
        }

        return normalized;
    }
}
