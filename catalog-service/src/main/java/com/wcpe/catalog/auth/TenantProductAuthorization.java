package com.wcpe.catalog.auth;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record TenantProductAuthorization(
    UUID tenantId,
    String productCode,
    String investorCode,
    String channelCode,
    String status,
    Instant authorizedAt,
    String authorizedBy,
    Instant expiresAt,
    String notes
) {
    public TenantProductAuthorization {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        productCode = normalizeRequired(productCode, "productCode");
        investorCode = normalizeOptional(investorCode);
        channelCode = normalizeOptional(channelCode);
        status = normalizeRequired(status == null ? "ACTIVE" : status, "status");
    }

    boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }

    boolean matches(String productCode, String investorCode, String channelCode) {
        return this.productCode.equals(normalizeRequired(productCode, "productCode"))
            && wildcardMatches(this.investorCode, normalizeOptional(investorCode))
            && wildcardMatches(this.channelCode, normalizeOptional(channelCode));
    }

    private static boolean wildcardMatches(String authorizationValue, String candidateValue) {
        return authorizationValue == null || authorizationValue.equals(candidateValue);
    }

    static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
