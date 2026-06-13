package com.wcpe.quote;

@FunctionalInterface
public interface EligibilityFilter {
    EligibilityDecision evaluate(QuoteCandidate candidate);

    static EligibilityFilter allowAll() {
        return candidate -> EligibilityDecision.allowed();
    }
}
