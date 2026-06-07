package com.wcpe.tenantcontext;

import java.util.List;

public class TenantAccessPolicy {
    public static final String DEFAULT_CONTEXT_READ_SCOPE = "tenant:context:read";

    public void validate(String pathTenantId, TenantContextInput input, String requiredScope) {
        String normalizedPathTenant = required(pathTenantId, "pathTenantId");
        String tokenTenant = required(input.tenantId(), "tenantId");
        String selectedTenant = optional(input.selectedTenantId());
        String effectiveSelectedTenant = selectedTenant.isBlank() ? tokenTenant : selectedTenant;

        if (!normalizedPathTenant.equals(effectiveSelectedTenant) || !allowedTenants(input.allowedTenantIds(), tokenTenant).contains(normalizedPathTenant)) {
            throw new TenantContextValidationException("TENANT_ACCESS_DENIED", "You do not have access to this tenant.");
        }

        if ("SUSPENDED".equalsIgnoreCase(optional(input.tenantStatus()))) {
            throw new TenantContextValidationException("TENANT_SUSPENDED", "Tenant is suspended.");
        }

        if (!contains(input.scopes(), required(requiredScope, "requiredScope"))) {
            throw new TenantContextValidationException("TENANT_ACCESS_DENIED", "Required tenant scope is missing.");
        }
    }

    private static List<String> allowedTenants(List<String> allowedTenantIds, String tokenTenant) {
        List<String> normalized = normalizeList(allowedTenantIds);
        if (normalized.isEmpty()) {
            return List.of(tokenTenant);
        }
        return normalized;
    }

    static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    private static boolean contains(List<String> values, String expected) {
        return normalizeList(values).contains(expected);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new TenantContextValidationException("TENANT_CONTEXT_MISSING", fieldName + " is required");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
