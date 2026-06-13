package com.wcpe.quote;

import java.util.List;
import java.util.Optional;

public interface QuoteDependencies {
    Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request);

    List<QuoteCandidate> candidatesFor(QuoteCreateRequest request);

    default List<QuoteCandidate> authorizedCandidatesFor(QuoteCreateRequest request, List<QuoteCandidate> candidates) {
        return candidates;
    }

    String eligibilityVersion();

    String pricingVersion();

    String adjustmentVersion();

    default AdjustmentCalculationPort adjustmentCalculationPort() {
        return AdjustmentCalculationPort.preserveCandidateAdjustments();
    }

    String marginVersion();
}
