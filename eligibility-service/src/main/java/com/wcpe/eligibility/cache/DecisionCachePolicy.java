package com.wcpe.eligibility.cache;

public class DecisionCachePolicy {
    public EligibilityCacheStatus statusFor(String cachedRuleVersionGraphHash, String currentRuleVersionGraphHash) {
        if (cachedRuleVersionGraphHash == null || currentRuleVersionGraphHash == null) {
            return EligibilityCacheStatus.MISS;
        }
        return cachedRuleVersionGraphHash.equals(currentRuleVersionGraphHash)
            ? EligibilityCacheStatus.HIT
            : EligibilityCacheStatus.STALE_REJECTED;
    }
}
