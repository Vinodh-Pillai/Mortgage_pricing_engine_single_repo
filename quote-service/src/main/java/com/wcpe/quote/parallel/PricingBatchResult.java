package com.wcpe.quote.parallel;

import com.wcpe.quote.QuoteCandidate;
import java.time.Duration;
import java.util.List;

public record PricingBatchResult(
    List<QuoteCandidate> successful,
    List<FailedCandidate> failed,
    Duration totalDuration,
    int totalCandidates,
    int minimumSuccessfulResults
) {
    public PricingBatchResult {
        successful = List.copyOf(successful == null ? List.of() : successful);
        failed = List.copyOf(failed == null ? List.of() : failed);
        totalDuration = totalDuration == null ? Duration.ZERO : totalDuration;
        if (minimumSuccessfulResults <= 0) {
            minimumSuccessfulResults = 1;
        }
    }

    public double successRate() {
        return totalCandidates == 0 ? 0.0d : (double) successful.size() / (double) totalCandidates;
    }

    public boolean hasMinimumResults() {
        return successful.size() >= minimumSuccessfulResults;
    }

    public boolean allFailed() {
        return totalCandidates > 0 && successful.isEmpty();
    }
}
