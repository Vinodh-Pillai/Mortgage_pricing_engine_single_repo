package com.wcpe.quote.parallel;

import com.wcpe.quote.QuoteCandidate;

public record FailedCandidate(
    QuoteCandidate candidate,
    String failureCode,
    String failureDetail
) {
    public static FailedCandidate timeout(QuoteCandidate candidate) {
        return new FailedCandidate(candidate, "CANDIDATE_PRICING_TIMEOUT", "candidate exceeded per-candidate pricing timeout");
    }

    public static FailedCandidate failed(QuoteCandidate candidate, Throwable throwable) {
        String message = throwable == null || throwable.getMessage() == null ? "pricing failed" : throwable.getMessage();
        return new FailedCandidate(candidate, "CANDIDATE_PRICING_FAILED", message);
    }
}
