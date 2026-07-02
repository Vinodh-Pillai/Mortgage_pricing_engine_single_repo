package com.wcpe.quote;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dev-only deterministic quote dependencies for committed synthetic load-test fixtures.
 *
 * <p>This bean is opt-in by configuration and intentionally mirrors the existing
 * QuoteTestSupport/PII-17-S07 fixture shape instead of inventing production pricing,
 * eligibility, investor, or tenant rules.</p>
 */
class SyntheticQuoteDependencies implements QuoteDependencies {
    static final String POLICY_ID = "ranking-policy-fixture";
    static final String POLICY_VERSION = "rank-v1";
    static final String VERSION = "pii-17-s07-synthetic-dev-v1";

    @Override
    public Optional<RankingPolicy> rankingPolicyFor(QuoteCreateRequest request) {
        return Optional.of(new RankingPolicy(
            POLICY_ID,
            POLICY_VERSION,
            Duration.ofHours(4),
            Map.of(),
            Map.of(),
            Map.of(),
            new RankingPolicyRef(
                POLICY_ID,
                POLICY_VERSION,
                List.of(new RankingCriterion("fixture-price", "numeric_min", "basePriceBps", BigDecimal.ONE, true, "{}")),
                List.of(new TieBreaker("fixture-candidate-id", "candidateId", "DESC", 1, Map.of())),
                Map.of(
                    "source", "projects/observability-service/src/test/resources/loadtest/PII-17-S07/pricing-load-test-fixture.json",
                    "productionValidity", "false",
                    "policy", "dev-only deterministic fixture ranking"
                )
            )
        ));
    }

    @Override
    public List<QuoteCandidate> candidatesFor(QuoteCreateRequest request) {
        String productId = firstOrDefault(request.filters() == null ? List.of() : request.filters().productTypeIds(), "fixture-product");
        String investorId = firstOrDefault(request.filters() == null ? List.of() : request.filters().investorIds(), "fixture-investor");
        String channel = firstOrDefault(request.filters() == null ? List.of() : request.filters().channels(), "retail");
        List<QuoteCandidate> candidates = new ArrayList<>();
        for (Integer lockPeriod : request.requestedLockPeriods()) {
            candidates.add(candidate("fixture-A-" + lockPeriod, productId, investorId, channel, lockPeriod));
            candidates.add(candidate("fixture-B-" + lockPeriod, productId, investorId, channel, lockPeriod));
        }
        return List.copyOf(candidates);
    }

    @Override
    public String eligibilityVersion() {
        return VERSION;
    }

    @Override
    public String pricingVersion() {
        return VERSION;
    }

    @Override
    public String adjustmentVersion() {
        return VERSION;
    }

    @Override
    public String marginVersion() {
        return VERSION;
    }

    private QuoteCandidate candidate(String id, String productId, String investorId, String channel, int lockPeriodDays) {
        return new QuoteCandidate(
            id,
            "eligibility-" + id,
            productId,
            investorId,
            channel,
            lockPeriodDays,
            new BigDecimal("6.12500"),
            new BigDecimal("10000.0000"),
            new BigDecimal("25.2500"),
            new BigDecimal("10.0000"),
            Map.of(
                "fixtureSource", "PII-17-S07 synthetic load-test fixture",
                "pricingPolicy", "dev-only deterministic fixture values from committed QuoteTestSupport shape"
            )
        );
    }

    private static String firstOrDefault(List<String> values, String defaultValue) {
        if (values == null) {
            return defaultValue;
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).findFirst().orElse(defaultValue);
    }
}
