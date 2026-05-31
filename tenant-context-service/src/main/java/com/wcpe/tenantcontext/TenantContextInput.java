package com.wcpe.tenantcontext;

public record TenantContextInput(String tenantId, String requestId, String traceId) {
}
