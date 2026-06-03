package com.wcpe.eligibility.cache;

import java.util.Objects;
import java.util.UUID;

public record EligibilityCacheKey(String value) {
    public EligibilityCacheKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cache key is required");
        }
    }

    public static EligibilityCacheKey config(UUID tenantId, String productFamily, String quoteType,
                                             String namespace, String versionToken, String... keyParts) {
        require(tenantId, "tenantId");
        require(productFamily, "productFamily");
        require(quoteType, "quoteType");
        require(namespace, "namespace");
        require(versionToken, "versionToken");
        StringBuilder key = new StringBuilder("eligibility:cfg:")
            .append(namespace).append(':')
            .append(tenantId).append(':')
            .append(productFamily).append(':')
            .append(quoteType).append(':')
            .append(versionToken);
        for (String keyPart : keyParts) {
            require(keyPart, "keyPart");
            key.append(':').append(keyPart);
        }
        return new EligibilityCacheKey(key.toString());
    }

    public static EligibilityCacheKey decision(UUID tenantId, String quoteType, String scenarioHash,
                                               String productCandidateHash, String ruleVersionGraphHash) {
        require(tenantId, "tenantId");
        require(quoteType, "quoteType");
        require(scenarioHash, "scenarioHash");
        require(productCandidateHash, "productCandidateHash");
        require(ruleVersionGraphHash, "ruleVersionGraphHash");
        return new EligibilityCacheKey("eligibility:decision:%s:%s:%s:%s:%s".formatted(
            tenantId, quoteType, scenarioHash, productCandidateHash, ruleVersionGraphHash));
    }

    public String redactedForLog() {
        String[] parts = value.split(":");
        if (parts.length < 4) {
            return "eligibility:cache:<redacted>";
        }
        return parts[0] + ':' + parts[1] + ':' + parts[2] + ":<redacted>";
    }

    private static void require(Object value, String name) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new IllegalArgumentException(name + " is required");
        }
        Objects.requireNonNull(value, name);
    }
}
