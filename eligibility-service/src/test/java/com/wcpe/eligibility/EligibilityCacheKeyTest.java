package com.wcpe.eligibility;

import com.wcpe.eligibility.cache.EligibilityCacheKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityCacheKeyTest {
    @Test
    void includesTenantFamilyQuoteTypeAndVersion() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        EligibilityCacheKey key = EligibilityCacheKey.config(
            tenantId,
            "CONVENTIONAL",
            "CONVENTIONAL_PURCHASE",
            "loan-limit",
            "version-7",
            "FNMA",
            "2026",
            "TX"
        );

        assertTrue(key.value().contains(tenantId.toString()));
        assertTrue(key.value().contains("CONVENTIONAL:CONVENTIONAL_PURCHASE"));
        assertTrue(key.value().contains("version-7"));
    }
}
