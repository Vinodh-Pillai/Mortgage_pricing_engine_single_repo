package com.wcpe.tenantcontext;

import java.util.List;

public record TenantContext(
    String tenantId,
    RequestContext request,
    ActorRef actor,
    List<String> roles,
    List<String> scopes,
    String channel
) {
    public TenantContext(String tenantId, RequestContext request) {
        this(tenantId, request, ActorRef.empty(), List.of(), List.of(), "");
    }

    public TenantContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "tenantId is required");
        }
        tenantId = tenantId.trim();
        if (request == null) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", "request context is required");
        }
        actor = actor == null ? ActorRef.empty() : actor;
        roles = immutableTrimmed(roles);
        scopes = immutableTrimmed(scopes);
        channel = channel == null || channel.isBlank() ? "" : channel.trim();
    }

    private static List<String> immutableTrimmed(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }
}
