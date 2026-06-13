package com.wcpe.quote;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TenantProductAuthorizationFilter {
    private final TenantProductAuthorizationRules rules;

    public TenantProductAuthorizationFilter(TenantProductAuthorizationRules rules) {
        this.rules = rules;
    }

    public boolean isAuthorized(UUID tenantId, String productCode, String investorCode, String channelCode) {
        return rules.authorizationsFor(tenantId).stream()
            .filter(rule -> "ACTIVE".equals(normalizeRequired(rule.status(), "status")))
            .filter(rule -> rule.expiresAtEpochMillis() == null || rule.expiresAtEpochMillis() > System.currentTimeMillis())
            .anyMatch(rule -> normalizeRequired(rule.productCode(), "productCode").equals(normalizeRequired(productCode, "productCode"))
                && wildcardMatches(rule.investorCode(), investorCode)
                && wildcardMatches(rule.channelCode(), channelCode));
    }

    public List<QuoteCandidate> filterAuthorized(UUID tenantId, List<QuoteCandidate> candidates, String channelCode) {
        String requestedChannel = normalizeOptional(channelCode);
        return candidates.stream()
            .filter(candidate -> requestedChannel == null || requestedChannel.equals(normalizeOptional(candidate.channel())))
            .filter(candidate -> isAuthorized(tenantId, candidate.productId(), candidate.investorId(), candidate.channel()))
            .toList();
    }

    public void onAuthorizationChanged(TenantAuthorizationChangedEvent event) {
        rules.invalidate(event.tenantId());
    }

    private static boolean wildcardMatches(String authorizationValue, String candidateValue) {
        String authorization = normalizeOptional(authorizationValue);
        return authorization == null || authorization.equals(normalizeOptional(candidateValue));
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
