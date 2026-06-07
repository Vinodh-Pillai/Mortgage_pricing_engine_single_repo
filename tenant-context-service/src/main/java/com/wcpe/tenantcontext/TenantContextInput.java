package com.wcpe.tenantcontext;

import java.util.List;

public record TenantContextInput(
    String tenantId,
    String requestId,
    String traceId,
    String actorId,
    String actorType,
    List<String> roles,
    List<String> scopes,
    String channel,
    String correlationId,
    String causationId,
    String idempotencyKey,
    String requestSource,
    List<String> allowedTenantIds,
    String selectedTenantId,
    String tenantStatus
) {
    public TenantContextInput(String tenantId, String requestId, String traceId) {
        this(tenantId, requestId, traceId, null, null, List.of(), List.of(), null, traceId, null, null, null, List.of(), tenantId, null);
    }
}
