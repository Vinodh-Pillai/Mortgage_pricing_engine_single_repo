package com.wcpe.quote;

import java.util.List;
import java.util.Optional;

public interface QuoteDependencies {
    Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request);

    List<QuoteCandidate> candidatesFor(QuoteCreateRequest request);

    String eligibilityVersion();

    String pricingVersion();

    String adjustmentVersion();

    String marginVersion();
}
