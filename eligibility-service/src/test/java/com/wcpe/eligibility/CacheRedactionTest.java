package com.wcpe.eligibility;

import com.wcpe.eligibility.cache.EligibilityCacheKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheRedactionTest {
    @Test
    void doesNotLogSensitiveKeyParts() {
        String sensitiveHash = "borrower-derived-hash";
        EligibilityCacheKey key = EligibilityCacheKey.decision(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "CONVENTIONAL_PURCHASE",
            sensitiveHash,
            "candidate-hash",
            "rules-v1"
        );

        String redacted = key.redactedForLog();

        assertTrue(redacted.contains("eligibility:decision"));
        assertFalse(redacted.contains(sensitiveHash));
        assertFalse(redacted.contains("candidate-hash"));
    }
}
