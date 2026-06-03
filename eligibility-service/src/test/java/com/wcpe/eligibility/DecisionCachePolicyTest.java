package com.wcpe.eligibility;

import com.wcpe.eligibility.cache.DecisionCachePolicy;
import com.wcpe.eligibility.cache.EligibilityCacheStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionCachePolicyTest {
    @Test
    void rejectsRuleGraphMismatch() {
        DecisionCachePolicy policy = new DecisionCachePolicy();

        assertEquals(EligibilityCacheStatus.STALE_REJECTED, policy.statusFor("rules-v1", "rules-v2"));
        assertEquals(EligibilityCacheStatus.HIT, policy.statusFor("rules-v2", "rules-v2"));
    }
}
