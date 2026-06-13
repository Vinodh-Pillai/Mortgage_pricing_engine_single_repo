package com.wcpe.quote;

import java.util.List;

public record EligibilityDecision(boolean eligible, List<EligibilityFailure> failures, List<String> warnings) {
    public EligibilityDecision {
        failures = List.copyOf(failures == null ? List.of() : failures);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public static EligibilityDecision allowed() {
        return new EligibilityDecision(true, List.of(), List.of());
    }
}
